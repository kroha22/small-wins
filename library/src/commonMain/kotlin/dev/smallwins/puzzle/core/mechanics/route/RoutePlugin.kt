package dev.smallwins.puzzle.core.mechanics.route

import dev.smallwins.puzzle.core.ActivityCapability
import dev.smallwins.puzzle.core.ActivityManifest
import dev.smallwins.puzzle.core.ActivityPlugin
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.EngineEvent
import dev.smallwins.puzzle.core.Evaluation
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.GenerationResult
import dev.smallwins.puzzle.core.PuzzleHint
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import dev.smallwins.puzzle.core.StableRandom
import dev.smallwins.puzzle.core.Transition
import dev.smallwins.puzzle.core.ValidationIssue
import dev.smallwins.puzzle.core.ValidationResult
import dev.smallwins.puzzle.core.stableHash
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

const val ROUTE_RULES_VERSION = 1
const val ROUTE_GENERATOR_VERSION = 1

object RoutePlugin :
    ActivityPlugin<RouteLevel, RouteState, RouteAction, RouteWitness, RouteEvaluationMetrics, RouteGeneratorMetrics> {
    override val manifest =
        ActivityManifest(
            mechanicId = "route",
            rulesVersion = ROUTE_RULES_VERSION,
            aliases = listOf("waypoints"),
            capabilities =
                listOf(
                    ActivityCapability.DETERMINISTIC_GENERATION,
                    ActivityCapability.DIFFICULTY,
                    ActivityCapability.SOLVER,
                    ActivityCapability.HINTS,
                    ActivityCapability.SERIALIZABLE_STATE,
                    ActivityCapability.SOLUTION_WITNESS,
                ),
        )

    override fun validateLevel(level: RouteLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != ROUTE_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        val nodeIds = p.nodes.map { it.id }
        if (p.nodes.isEmpty() || nodeIds.any { it.isBlank() } || nodeIds.distinct().size != nodeIds.size) {
            issues += issue("nodes.invalid", "$.payload.nodes")
        }
        if (p.nodes.any { !it.x.isFinite() || !it.y.isFinite() }) issues += issue("nodes.position", "$.payload.nodes")
        if (
            p.connections.any {
                it.firstNodeId !in nodeIds || it.secondNodeId !in nodeIds || it.firstNodeId == it.secondNodeId
            }
        ) issues += issue("connections.invalid", "$.payload.connections")
        if (!validEndpoints(p.starts, nodeIds)) issues += issue("starts.invalid", "$.payload.starts")
        if (!validEndpoints(p.targets, nodeIds)) issues += issue("targets.invalid", "$.payload.targets")
        if (
            p.waypoints.map { it.id }.distinct().size != p.waypoints.size ||
            p.waypoints.any { it.id.isBlank() || it.nodeId !in nodeIds || (it.order != null && it.order <= 0) }
        ) issues += issue("waypoints.invalid", "$.payload.waypoints")
        val ordered = p.waypoints.filter { it.required && it.order != null }.map { it.order }
        if (ordered.distinct().size != ordered.size) issues += issue("waypoints.order", "$.payload.waypoints")
        if (p.forbiddenNodeIds.distinct().size != p.forbiddenNodeIds.size || p.forbiddenNodeIds.any { it !in nodeIds }) {
            issues += issue("forbidden.invalid", "$.payload.forbiddenNodeIds")
        }
        if (p.starts.any { it.nodeId in p.forbiddenNodeIds } || p.targets.any { it.nodeId in p.forbiddenNodeIds }) {
            issues += issue("endpoints.forbidden", "$.payload.forbiddenNodeIds")
        }
        if (p.pathRules.maximumLength != null && p.pathRules.maximumLength < 0) {
            issues += issue("maximum-length.invalid", "$.payload.pathRules.maximumLength")
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: RouteLevel) = RouteState()

    override fun validateState(level: RouteLevel, state: RouteState): ValidationResult {
        val p = level.payload
        var rebuilt = RouteState()
        state.paths.forEach { path ->
            rebuilt = rawStart(p, rebuilt, path.startId)
                ?: return ValidationResult(false, listOf(issue("state.paths", "$.paths")))
            path.nodeIds.drop(1).forEach { nodeId ->
                rebuilt = rawExtend(p, rebuilt, nodeId)
                    ?: return ValidationResult(false, listOf(issue("state.paths", "$.paths")))
            }
        }
        if (rebuilt.paths != state.paths || rebuilt.activeStartId != state.activeStartId) {
            return ValidationResult(false, listOf(issue("state.paths", "$.paths")))
        }
        val derived = deriveProgress(p, rebuilt, deadEnd = state.deadEndDetected)
        return if (
            state.visitedWaypointIds == derived.visitedWaypointIds &&
            state.deadEndDetected == derived.deadEndDetected
        ) ValidationResult(true) else ValidationResult(false, listOf(issue("state.derived", "$")))
    }

    override fun transition(level: RouteLevel, state: RouteState, action: RouteAction): Transition<RouteState> {
        if (!validateState(level, state).valid) return rejected(state, "invalid-state")
        if (isSolved(level.payload, state)) return rejected(state, "already-completed")
        return when (action.type) {
            "start" -> start(level, state, action.startId)
            "extend" -> extend(level, state, action.nodeId)
            "trim" -> trim(level, state, action.nodeId)
            else -> rejected(state, "invalid-action")
        }
    }

    override fun evaluate(level: RouteLevel, state: RouteState): Evaluation<RouteEvaluationMetrics> {
        val p = level.payload
        val requiredWaypoints = p.waypoints.filter { it.required }
        val completedPaths = state.paths.count { it.targetId != null }
        val visitedRequired = requiredWaypoints.count { it.id in state.visitedWaypointIds }
        val solved = isSolved(p, state)
        val denominator = (p.starts.size + requiredWaypoints.size).coerceAtLeast(1)
        return Evaluation(
            solved,
            if (solved) 1.0 else (completedPaths + visitedRequired).toDouble() / denominator,
            RouteEvaluationMetrics(completedPaths, p.starts.size, visitedRequired, requiredWaypoints.size, state.deadEndDetected),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<RouteLevel, RouteWitness, RouteGeneratorMetrics> {
        require(request.generatorVersion == ROUTE_GENERATOR_VERSION)
        if ((request.parameters["profile"] as? JsonPrimitive)?.contentOrNull == "ludeni-v3") return generateLudeni(request)
        val defaultSize = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL, DifficultyBand.EASY -> 4
            else -> 5
        }
        val size = request.parameters["size"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(3, 8)
        val random = StableRandom("route/v$ROUTE_GENERATOR_VERSION/${request.seed}/$size")
        val snake = buildList {
            repeat(size) { row ->
                val columns = if (row % 2 == 0) 0 until size else size - 1 downTo 0
                columns.forEach { column -> add(row * size + column) }
            }
        }
        val route = snake.take(size * size * 3 / 4)
        val waypointCells = listOf(route[route.size / 3], route[route.size * 2 / 3])
        val blocked = (0 until size * size).filter { it !in route && random.nextInt(4) != 0 }
        val payload = gridPayload(size, size, route.first(), route.last(), waypointCells, blocked)
        val level =
            PuzzleLevel(
                PuzzleIdentity(
                    manifest.mechanicId,
                    manifest.mechanicId,
                    "generated",
                    "generated-${stableHash("v$ROUTE_GENERATOR_VERSION/${request.seed}/$size").toString(16)}",
                    1,
                    ROUTE_RULES_VERSION,
                    1,
                    ROUTE_GENERATOR_VERSION,
                    request.seed,
                ),
                request.difficulty,
                payload,
            )
        val witness = stateForPath(payload, route.map(::cellId))
        require(evaluate(level, witness).solved)
        return GenerationResult(level, witness, RouteGeneratorMetrics(size, size, route.size, blocked.size))
    }

    private fun generateLudeni(request: GenerationRequest): GenerationResult<RouteLevel, RouteWitness, RouteGeneratorMetrics> {
        val tutorial = request.difficulty.band == DifficultyBand.TUTORIAL
        val t = request.difficulty.normalized.coerceIn(0.0, 1.0)
        val size = ludeniRouteScale(t, 4, 7)
        val target = size * size - 1
        val blockedCount = if (tutorial) ((request.variant?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0) else ludeniRouteScale(t, 3, kotlin.math.floor(size * size * .27).toInt())
        val waypointCount = if (tutorial) ((request.variant?.toIntOrNull() ?: 1) - 2).coerceAtLeast(0) else ludeniRouteScale(t, 2, 4)
        val random = ludeniRouteRandom(request)
        val candidates = (1 until size * size - 1).toList()
        val requiredDetour = ludeniRouteScale(t, 1, 3)
        val minimumLength = 2 * size - 1 + 2 * requiredDetour
        var route: List<Int>? = null
        var blocked = emptyList<Int>()
        repeat(100) {
            if (route != null) return@repeat
            val path = mutableListOf(0); val used = mutableSetOf(0); var visits = 0
            lateinit var search: (Int) -> Boolean
            search = { cell ->
                visits += 1
                if (visits > 2000) false
                else if (cell == target) path.size >= minimumLength
                else {
                    val x = cell % size; val y = cell / size
                    val nexts = random.shuffled(listOf(if (y > 0) cell - size else null, if (x < size - 1) cell + 1 else null, if (y < size - 1) cell + size else null, if (x > 0) cell - 1 else null).filterNotNull().filterNot { it in used })
                    var found = false
                    for (next in nexts) {
                        used += next; path += next
                        if (search(next)) { found = true; break }
                        path.removeAt(path.lastIndex); used.remove(next)
                    }
                    found
                }
            }
            if (!search(0)) return@repeat
            val stops = path.drop(1).dropLast(1)
            if (!tutorial && stops.none { a -> stops.any { b -> (a % size - b % size) * (a / size - b / size) < 0 && minOf(kotlin.math.abs(a % size - b % size), kotlin.math.abs(a / size - b / size)) >= requiredDetour } }) return@repeat
            route = path.toList(); blocked = random.shuffled(candidates.filterNot { it in used }).take(blockedCount)
        }
        val witnessPath = route ?: error("Could not construct route witness")
        val waypoints = MutableList(waypointCount) { index -> witnessPath[((index + 1) * (witnessPath.size - 1)) / (waypointCount + 1)] }
        if (!tutorial) {
            val pairs = random.shuffled(witnessPath.drop(1).dropLast(1).flatMap { a -> witnessPath.drop(1).dropLast(1).filter { b -> a < b && (a % size - b % size) * (a / size - b / size) < 0 && minOf(kotlin.math.abs(a % size - b % size), kotlin.math.abs(a / size - b / size)) >= requiredDetour }.map { b -> a to b } })
            val pair = pairs.first(); waypoints[0] = pair.first; waypoints[1] = pair.second
        }
        val uniqueStops = waypoints.distinct().toMutableList()
        for (stop in random.shuffled(witnessPath.drop(1).dropLast(1))) { if (uniqueStops.size >= waypointCount) break; if (stop !in uniqueStops) uniqueStops += stop }
        val payload = gridPayload(size, size, 0, target, uniqueStops, blocked)
        val level = PuzzleLevel(PuzzleIdentity("route", "route", "generated", "generated-${stableHash("ludeni-v3/${request.seed}/${request.variant}").toString(16)}", 1, ROUTE_RULES_VERSION, 1, 1, request.seed), request.difficulty, payload)
        return GenerationResult(level, stateForPath(payload, witnessPath.map(::cellId)), RouteGeneratorMetrics(size, size, witnessPath.size, blocked.size))
    }

    override fun solve(level: RouteLevel): RouteWitness? = solveDetails(level, initialState(level), 1, 100_000).solutions.firstOrNull()

    override fun hint(level: RouteLevel, state: RouteState, used: Int): PuzzleHint? {
        val result = solveDetails(level, state, 1, 100_000)
        if (result.exceeded) return null
        val answer = result.solutions.firstOrNull()
            ?: return PuzzleHint("dead-end", JsonObject(emptyMap()))
        val active = answer.paths.firstOrNull { it.startId == (state.activeStartId ?: answer.paths.firstOrNull()?.startId) } ?: return null
        val currentSize = state.paths.firstOrNull { it.startId == active.startId }?.nodeIds?.size ?: 0
        val next = active.nodeIds.getOrNull(currentSize) ?: return null
        val pending = active.nodeIds.drop(currentSize).firstOrNull { node ->
            level.payload.waypoints.any { it.required && it.nodeId == node && it.id !in state.visitedWaypointIds }
        }
        val targetNode = pending ?: matchingTargetNode(level.payload, active.startId) ?: next
        val target = level.payload.nodes.first { it.id == targetNode }
        val nextNode = level.payload.nodes.first { it.id == next }
        val data =
            JsonObject(
                mapOf(
                    "nodeId" to JsonPrimitive(nextNode.id),
                    "x" to JsonPrimitive(nextNode.x),
                    "y" to JsonPrimitive(nextNode.y),
                    "targetNodeId" to JsonPrimitive(target.id),
                    "targetX" to JsonPrimitive(target.x),
                    "targetY" to JsonPrimitive(target.y),
                    "targetKind" to JsonPrimitive(if (pending == null) "target" else "waypoint"),
                ),
            )
        return PuzzleHint(if (used == 0) "target-axis" else if (used == 1) "target-node" else "next-node", data)
    }

    fun solveDetails(level: RouteLevel, partial: RouteState = initialState(level), limit: Int = 2, budget: Int = 100_000): RouteSolveResult {
        if (!validateState(level, partial).valid) return RouteSolveResult(emptyList(), 0, false)
        val p = level.payload
        val found = mutableListOf<RouteState>()
        var nodes = 0
        var exceeded = false
        fun search(input: RouteState) {
            if (found.size >= limit || exceeded) return
            nodes += 1
            if (nodes > budget) {
                exceeded = true
                return
            }
            val state = deriveProgress(p, input.copy(deadEndDetected = false), false)
            if (isSolved(p, state)) {
                found += state
                return
            }
            val active = state.paths.firstOrNull { it.startId == state.activeStartId }
            if (active == null || active.targetId != null) {
                p.starts.filter { start -> state.paths.none { it.startId == start.id } }.forEach { start ->
                    rawStart(p, state, start.id)?.let(::search)
                }
                if (active == null) return
            }
            val current = state.paths.firstOrNull { it.startId == state.activeStartId } ?: return
            outgoingNodeIds(p, current.nodeIds.last()).forEach { nodeId ->
                rawExtend(p, state, nodeId)?.let(::search)
            }
        }
        search(partial)
        return RouteSolveResult(found, nodes, exceeded)
    }

    private fun start(level: RouteLevel, state: RouteState, startId: String?): Transition<RouteState> {
        if (startId == null) return rejected(state, "unknown-start")
        val next = rawStart(level.payload, state, startId) ?: return rejected(state, "start-unavailable")
        return accepted(withDeadEnd(level, next), "path-started")
    }

    private fun extend(level: RouteLevel, state: RouteState, nodeId: String?): Transition<RouteState> {
        val reason = extendRejection(level.payload, state, nodeId)
        if (reason != null) return rejected(state, reason)
        return accepted(withDeadEnd(level, rawExtend(level.payload, state, nodeId!!)!!), "segment-added")
    }

    private fun trim(level: RouteLevel, state: RouteState, nodeId: String?): Transition<RouteState> {
        val index = state.paths.indexOfFirst { it.startId == state.activeStartId }
        if (index < 0 || nodeId == null) return rejected(state, "no-active-path")
        val path = state.paths[index]
        val nodeIndex = path.nodeIds.lastIndexOf(nodeId)
        if (nodeIndex < 0 || nodeIndex == path.nodeIds.lastIndex) return rejected(state, "trim-unavailable")
        val paths = state.paths.toMutableList()
        paths[index] = path.copy(targetId = null, nodeIds = path.nodeIds.take(nodeIndex + 1))
        return accepted(withDeadEnd(level, deriveProgress(level.payload, state.copy(paths = paths), false)), "path-trimmed")
    }

    private fun withDeadEnd(level: RouteLevel, state: RouteState): RouteState {
        val result = solveDetails(level, state.copy(deadEndDetected = false), 1, 25_000)
        return state.copy(deadEndDetected = !isSolved(level.payload, state) && !result.exceeded && result.solutions.isEmpty())
    }
}

private fun ludeniRouteRandom(request: GenerationRequest): StableRandom {
    val mode = if (request.difficulty.band == DifficultyBand.TUTORIAL) "tutorial" else "regular"
    val number = request.difficulty.normalized.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    return StableRandom("route:v3:$mode:$number:${request.seed}:${request.variant ?: "1"}")
}

private fun ludeniRouteScale(t: Double, minimum: Int, maximum: Int) = kotlin.math.floor(minimum + (maximum - minimum) * t + .5).toInt()

private fun rawStart(p: RoutePayload, state: RouteState, startId: String): RouteState? {
    val start = p.starts.firstOrNull { it.id == startId } ?: return null
    if (state.paths.any { it.startId == startId }) return null
    val path = RoutePath(start.id, matchingTarget(p, start, start.nodeId)?.id, listOf(start.nodeId))
    return deriveProgress(p, state.copy(paths = state.paths + path, activeStartId = start.id), false)
}

private fun rawExtend(p: RoutePayload, state: RouteState, nodeId: String): RouteState? {
    if (extendRejection(p, state, nodeId) != null) return null
    val index = state.paths.indexOfFirst { it.startId == state.activeStartId }
    val path = state.paths[index]
    val start = p.starts.first { it.id == path.startId }
    val paths = state.paths.toMutableList()
    paths[index] = path.copy(targetId = matchingTarget(p, start, nodeId)?.id, nodeIds = path.nodeIds + nodeId)
    return deriveProgress(p, state.copy(paths = paths), false)
}

private fun extendRejection(p: RoutePayload, state: RouteState, nodeId: String?): String? {
    if (nodeId == null || p.nodes.none { it.id == nodeId }) return "unknown-node"
    if (nodeId in p.forbiddenNodeIds) return "forbidden-node"
    val path = state.paths.firstOrNull { it.startId == state.activeStartId } ?: return "no-active-path"
    if (!canTravel(p, path.nodeIds.last(), nodeId)) return "not-connected"
    val node = p.nodes.first { it.id == nodeId }
    if (!p.pathRules.nodesMayRepeat && nodeId in path.nodeIds && !node.reusable) return "node-already-used"
    if (!p.pathRules.pathsMayShareNodes && state.paths.any { it.startId != path.startId && nodeId in it.nodeIds }) return "node-already-used"
    if (p.pathRules.maximumLength != null && path.nodeIds.size > p.pathRules.maximumLength) return "maximum-length"
    val ordered = p.waypoints.filter { it.nodeId == nodeId && it.order != null }.minByOrNull { it.order!! }
    if (ordered != null && ordered.order != nextRequiredOrder(p, state.visitedWaypointIds)) return "waypoint-order"
    if (!p.pathRules.pathsMayCross && createsCrossing(p, state, path, path.nodeIds.last(), nodeId)) return "paths-cross"
    val start = p.starts.first { it.id == path.startId }
    if (p.targets.any { it.nodeId == nodeId } && matchingTarget(p, start, nodeId) == null) return "route-type"
    if (
        p.pathRules.targetRequiresAllRequiredWaypoints && matchingTarget(p, start, nodeId) != null &&
        p.waypoints.any { it.required && it.id !in state.visitedWaypointIds && it.nodeId != nodeId }
    ) return "missing-waypoints"
    return null
}

private fun deriveProgress(p: RoutePayload, state: RouteState, deadEnd: Boolean): RouteState {
    val visited = p.waypoints.filter { waypoint -> state.paths.any { waypoint.nodeId in it.nodeIds } }.map { it.id }
    return state.copy(visitedWaypointIds = visited, deadEndDetected = deadEnd)
}

private fun isSolved(p: RoutePayload, state: RouteState) =
    p.starts.all { start -> state.paths.any { it.startId == start.id && it.targetId != null } } &&
        p.waypoints.filter { it.required }.all { it.id in state.visitedWaypointIds }

private fun validEndpoints(endpoints: List<RouteEndpoint>, nodeIds: List<String>) =
    endpoints.isNotEmpty() && endpoints.map { it.id }.distinct().size == endpoints.size &&
        endpoints.all { it.id.isNotBlank() && it.nodeId in nodeIds }

private fun matchingTarget(p: RoutePayload, start: RouteEndpoint, nodeId: String) =
    p.targets.firstOrNull { it.nodeId == nodeId && (start.routeType == null || it.routeType == null || start.routeType == it.routeType) }

private fun matchingTargetNode(p: RoutePayload, startId: String): String? {
    val start = p.starts.firstOrNull { it.id == startId } ?: return null
    return p.targets.firstOrNull { start.routeType == null || it.routeType == null || start.routeType == it.routeType }?.nodeId
}

private fun nextRequiredOrder(p: RoutePayload, visited: List<String>) =
    p.waypoints.filter { it.required && it.order != null && it.id !in visited }.minOfOrNull { it.order!! }

private fun canTravel(p: RoutePayload, from: String, to: String) =
    p.connections.any { it.firstNodeId == from && it.secondNodeId == to || it.bidirectional && it.firstNodeId == to && it.secondNodeId == from }

private fun outgoingNodeIds(p: RoutePayload, nodeId: String) =
    p.connections.mapNotNull {
        when {
            it.firstNodeId == nodeId -> it.secondNodeId
            it.bidirectional && it.secondNodeId == nodeId -> it.firstNodeId
            else -> null
        }
    }

private fun createsCrossing(p: RoutePayload, state: RouteState, active: RoutePath, fromId: String, toId: String): Boolean {
    val nodes = p.nodes.associateBy { it.id }
    val from = nodes.getValue(fromId)
    val to = nodes.getValue(toId)
    return state.paths.any { path ->
        path.nodeIds.zipWithNext().any { (otherFromId, otherToId) ->
            if (path.startId == active.startId && (otherFromId == fromId || otherToId == fromId)) return@any false
            if (p.pathRules.pathsMayShareNodes && setOf(fromId, toId).intersect(setOf(otherFromId, otherToId)).isNotEmpty()) return@any false
            segmentsIntersect(from, to, nodes.getValue(otherFromId), nodes.getValue(otherToId))
        }
    }
}

private fun segmentsIntersect(a: RouteNode, b: RouteNode, c: RouteNode, d: RouteNode): Boolean {
    fun orientation(first: RouteNode, second: RouteNode, third: RouteNode) =
        (second.x - first.x) * (third.y - first.y) - (second.y - first.y) * (third.x - first.x)
    fun opposite(first: Double, second: Double) = first > EPSILON && second < -EPSILON || first < -EPSILON && second > EPSILON
    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    return opposite(o1, o2) && opposite(o3, o4) ||
        abs(o1) <= EPSILON && onSegment(c, a, b) || abs(o2) <= EPSILON && onSegment(d, a, b) ||
        abs(o3) <= EPSILON && onSegment(a, c, d) || abs(o4) <= EPSILON && onSegment(b, c, d)
}

private fun onSegment(point: RouteNode, start: RouteNode, end: RouteNode) =
    point.x >= minOf(start.x, end.x) - EPSILON && point.x <= maxOf(start.x, end.x) + EPSILON &&
        point.y >= minOf(start.y, end.y) - EPSILON && point.y <= maxOf(start.y, end.y) + EPSILON

private fun stateForPath(p: RoutePayload, nodeIds: List<String>): RouteState {
    val start = p.starts.single()
    val path = RoutePath(start.id, matchingTarget(p, start, nodeIds.last())?.id, nodeIds)
    return deriveProgress(p, RouteState(listOf(path), start.id), false)
}

private fun gridPayload(rows: Int, cols: Int, start: Int, target: Int, waypoints: List<Int>, blocked: List<Int>): RoutePayload {
    val nodes = List(rows * cols) { cell -> RouteNode(cellId(cell), (cell % cols).toDouble(), (cell / cols).toDouble()) }
    val connections = buildList {
        repeat(rows) { row -> repeat(cols) { col ->
            val cell = row * cols + col
            if (col + 1 < cols) add(RouteConnection(cellId(cell), cellId(cell + 1)))
            if (row + 1 < rows) add(RouteConnection(cellId(cell), cellId(cell + cols)))
        } }
    }
    return RoutePayload(
        nodes,
        connections,
        listOf(RouteEndpoint("start", cellId(start))),
        listOf(RouteEndpoint("target", cellId(target))),
        waypoints.mapIndexed { index, cell -> RouteWaypoint("waypoint-${index + 1}", cellId(cell)) },
        blocked.map(::cellId),
        RoutePathRules(targetRequiresAllRequiredWaypoints = true),
    )
}

private fun cellId(cell: Int) = "cell-$cell"
private fun issue(code: String, path: String) = ValidationIssue(code, path)
private fun rejected(state: RouteState, reason: String) = Transition(false, false, state, reason)
private fun accepted(state: RouteState, event: String) = Transition(true, true, state, events = listOf(EngineEvent(event)))
private const val EPSILON = 0.000_01
