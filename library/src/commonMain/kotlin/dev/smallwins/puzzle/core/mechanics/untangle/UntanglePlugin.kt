package dev.smallwins.puzzle.core.mechanics.untangle

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
import kotlinx.serialization.json.intOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

const val UNTANGLE_RULES_VERSION = 1
const val UNTANGLE_GENERATOR_VERSION = 1

object UntanglePlugin :
    ActivityPlugin<
        UntangleLevel,
        UntangleState,
        UntangleAction,
        UntangleWitness,
        UntangleEvaluationMetrics,
        UntangleGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "untangle",
            rulesVersion = UNTANGLE_RULES_VERSION,
            aliases = emptyList(),
            capabilities =
                listOf(
                    ActivityCapability.DETERMINISTIC_GENERATION,
                    ActivityCapability.DIFFICULTY,
                    ActivityCapability.SERIALIZABLE_STATE,
                    ActivityCapability.SOLUTION_WITNESS,
                ),
        )

    override fun validateLevel(level: UntangleLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != UNTANGLE_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (!validBounds(p.fieldBounds)) issues += issue("bounds.invalid", "$.payload.fieldBounds")
        if (!p.minimumNodeDistance.isFinite() || p.minimumNodeDistance <= 0.0) {
            issues += issue("node-distance.invalid", "$.payload.minimumNodeDistance")
        }
        if (!p.epsilon.isFinite() || p.epsilon <= 0.0 || p.coordinateScale !in 1..1_000_000) {
            issues += issue("numeric-contract.invalid", "$.payload")
        }
        val nodeIds = p.nodes.map { it.id }
        if (
            p.nodes.size !in 2..100 || nodeIds.any { it.isBlank() } || nodeIds.distinct().size != nodeIds.size ||
            p.nodes.any { node ->
                !node.x.isFinite() || !node.y.isFinite() || !contains(p.fieldBounds, UntanglePoint(node.x, node.y), p.epsilon) ||
                    (node.movementBounds != null && !validBounds(node.movementBounds))
            }
        ) issues += issue("nodes.invalid", "$.payload.nodes")
        val edgeIds = p.edges.map { it.id }
        val pairs = p.edges.map { setOf(it.firstNodeId, it.secondNodeId) }
        if (
            p.edges.isEmpty() || edgeIds.any { it.isBlank() } || edgeIds.distinct().size != edgeIds.size ||
            pairs.distinct().size != pairs.size || p.edges.any {
                it.firstNodeId == it.secondNodeId || it.firstNodeId !in nodeIds || it.secondNodeId !in nodeIds
            }
        ) issues += issue("edges.invalid", "$.payload.edges")
        if (
            p.placementZones.map { it.id }.distinct().size != p.placementZones.size ||
            p.placementZones.any { !validBounds(it.bounds) || it.allowedNodeIds.isEmpty() || it.allowedNodeIds.any { id -> id !in nodeIds } }
        ) issues += issue("placement-zones.invalid", "$.payload.placementZones")
        if (p.forbiddenZones.any { !validBounds(it) }) issues += issue("forbidden-zones.invalid", "$.payload.forbiddenZones")
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: UntangleLevel) =
        UntangleState(level.payload.nodes.associate { it.id to UntanglePoint(it.x, it.y) })

    override fun validateState(level: UntangleLevel, state: UntangleState): ValidationResult {
        val p = level.payload
        val valid =
            state.positions.keys == p.nodes.map { it.id }.toSet() &&
                state.positions.all { (id, point) ->
                    point.x.isFinite() && point.y.isFinite() && contains(p.fieldBounds, point, p.epsilon) &&
                        p.nodes.first { it.id == id }.let { node ->
                            (!node.fixed || point == UntanglePoint(node.x, node.y)) &&
                                (node.movementBounds == null || contains(node.movementBounds, point, p.epsilon))
                        }
                } &&
                state.positions.values.toList().allSeparated(p.minimumNodeDistance, p.epsilon)
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("positions.invalid", "$.positions")))
    }

    override fun transition(level: UntangleLevel, state: UntangleState, action: UntangleAction): Transition<UntangleState> {
        val p = level.payload
        if (action.type != "moveNode") return rejected(state, "unknown-action")
        val node = p.nodes.firstOrNull { it.id == action.nodeId } ?: return rejected(state, "unknown-node")
        if (node.fixed) return rejected(state, "fixed-node")
        if (!action.x.isFinite() || !action.y.isFinite()) return rejected(state, "non-finite-position")
        val point = UntanglePoint(round(action.x * p.coordinateScale) / p.coordinateScale, round(action.y * p.coordinateScale) / p.coordinateScale)
        if (!contains(p.fieldBounds, point, p.epsilon) || (node.movementBounds != null && !contains(node.movementBounds, point, p.epsilon))) {
            return rejected(state, "out-of-bounds")
        }
        if (p.forbiddenZones.any { contains(it, point, p.epsilon) }) return rejected(state, "forbidden-zone")
        val zones = p.placementZones.filter { action.nodeId in it.allowedNodeIds }
        if (zones.isNotEmpty() && zones.none { contains(it.bounds, point, p.epsilon) }) return rejected(state, "placement-zone")
        if (state.positions.any { (id, other) -> id != action.nodeId && distance(point, other) < p.minimumNodeDistance - p.epsilon }) {
            return rejected(state, "nodes-too-close")
        }
        val previous = state.positions.getValue(action.nodeId)
        if (previous == point) return Transition(true, false, state)
        return Transition(
            accepted = true,
            changed = true,
            state = UntangleState(state.positions + (action.nodeId to point)),
            events = listOf(EngineEvent("node-moved", JsonObject(mapOf("nodeId" to JsonPrimitive(action.nodeId))))),
        )
    }

    override fun evaluate(level: UntangleLevel, state: UntangleState): Evaluation<UntangleEvaluationMetrics> {
        val conflicts = conflicts(level.payload, state)
        val placementsValid = placementsValid(level.payload, state)
        val edges = conflicts.flatMap { listOfNotNull(it.firstEdgeId, it.secondEdgeId) }.distinct().sorted()
        val solved = conflicts.isEmpty() && placementsValid
        return Evaluation(
            solved,
            if (solved) 1.0 else 1.0 / (1.0 + conflicts.size + if (placementsValid) 0 else 1),
            UntangleEvaluationMetrics(conflicts.size, edges, conflicts, placementsValid),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<UntangleLevel, UntangleWitness, UntangleGeneratorMetrics> {
        require(request.generatorVersion == UNTANGLE_GENERATOR_VERSION)
        val defaultCount = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL, DifficultyBand.EASY -> 4
            DifficultyBand.MEDIUM -> 5
            DifficultyBand.HARD -> 6
            DifficultyBand.EXPERT -> 7
        }
        val count = request.parameters["nodes"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultCount }.coerceIn(4, 10)
        val random = StableRandom("untangle/v$UNTANGLE_GENERATOR_VERSION/${request.seed}/$count")
        val offset = random.nextInt(360).toDouble() * PI / 180.0
        val solutionPoints = circlePoints(count, offset)
        val solutionNodes = solutionPoints.mapIndexed { index, point ->
            UntangleNode(nodeId(index), point.x, point.y, fixed = index == 0)
        }
        val scrambled = solutionNodes.toMutableList()
        scrambled[1] = solutionNodes[2].copy(id = solutionNodes[1].id, fixed = false)
        scrambled[2] = solutionNodes[1].copy(id = solutionNodes[2].id, fixed = false)
        val edges = List(count) { index -> UntangleEdge("edge-$index", nodeId(index), nodeId((index + 1) % count)) }
        val payload = UntanglePayload(nodes = scrambled, edges = edges)
        val level =
            PuzzleLevel(
                PuzzleIdentity(
                    manifest.mechanicId,
                    manifest.mechanicId,
                    "generated",
                    "generated-${stableHash("v$UNTANGLE_GENERATOR_VERSION/${request.seed}/$count").toString(16)}",
                    1,
                    UNTANGLE_RULES_VERSION,
                    1,
                    UNTANGLE_GENERATOR_VERSION,
                    request.seed,
                ),
                request.difficulty,
                payload,
            )
        val witness = UntangleState(solutionNodes.associate { it.id to UntanglePoint(it.x, it.y) })
        require(evaluate(level, witness).solved)
        return GenerationResult(level, witness, UntangleGeneratorMetrics(count, edges.size, conflicts(payload, initialState(level)).size))
    }

    override fun solve(level: UntangleLevel): UntangleWitness? {
        val points = circlePoints(level.payload.nodes.size, 0.0)
        val candidate = UntangleState(level.payload.nodes.mapIndexed { index, node -> node.id to points[index] }.toMap())
        return candidate.takeIf { validateState(level, it).valid && evaluate(level, it).solved }
    }

    override fun hint(level: UntangleLevel, state: UntangleState, used: Int): PuzzleHint? = null

    fun conflicts(payload: UntanglePayload, state: UntangleState): List<UntangleConflict> {
        val result = linkedSetOf<UntangleConflict>()
        payload.edges.forEachIndexed { index, first ->
            payload.edges.drop(index + 1).forEach { second ->
                if (intersectionAllowed(payload, first, second)) return@forEach
                val shared = listOf(first.firstNodeId, first.secondNodeId).firstOrNull { it == second.firstNodeId || it == second.secondNodeId }
                val hit = if (shared == null) {
                    segmentsIntersect(
                        state.positions.getValue(first.firstNodeId),
                        state.positions.getValue(first.secondNodeId),
                        state.positions.getValue(second.firstNodeId),
                        state.positions.getValue(second.secondNodeId),
                        payload.epsilon,
                    )
                } else {
                    val a = if (first.firstNodeId == shared) first.secondNodeId else first.firstNodeId
                    val b = if (second.firstNodeId == shared) second.secondNodeId else second.firstNodeId
                    onSegment(state.positions.getValue(shared), state.positions.getValue(a), state.positions.getValue(b), payload.epsilon) ||
                        onSegment(state.positions.getValue(shared), state.positions.getValue(b), state.positions.getValue(a), payload.epsilon)
                }
                if (hit) result += UntangleConflict(first.id, second.id)
            }
        }
        payload.edges.forEach { edge ->
            payload.nodes.filter { it.id != edge.firstNodeId && it.id != edge.secondNodeId }.forEach { node ->
                if (
                    onSegment(
                        state.positions.getValue(edge.firstNodeId),
                        state.positions.getValue(edge.secondNodeId),
                        state.positions.getValue(node.id),
                        payload.epsilon,
                    )
                ) result += UntangleConflict(edge.id, nodeId = node.id)
            }
        }
        return result.toList()
    }
}

private fun placementsValid(payload: UntanglePayload, state: UntangleState) =
    payload.placementZones.all { zone -> zone.allowedNodeIds.any { contains(zone.bounds, state.positions.getValue(it), payload.epsilon) } }

private fun intersectionAllowed(payload: UntanglePayload, first: UntangleEdge, second: UntangleEdge): Boolean {
    if (first.intersectionsAllowed || second.intersectionsAllowed) return true
    return first.groupId != null && first.groupId == second.groupId && first.groupId in payload.intersectionsAllowedWithinGroups
}

private fun segmentsIntersect(a: UntanglePoint, b: UntanglePoint, c: UntanglePoint, d: UntanglePoint, epsilon: Double): Boolean {
    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    return (((abC > epsilon && abD < -epsilon) || (abC < -epsilon && abD > epsilon)) &&
        ((cdA > epsilon && cdB < -epsilon) || (cdA < -epsilon && cdB > epsilon))) ||
        onSegment(a, b, c, epsilon) || onSegment(a, b, d, epsilon) ||
        onSegment(c, d, a, epsilon) || onSegment(c, d, b, epsilon)
}

private fun onSegment(a: UntanglePoint, b: UntanglePoint, point: UntanglePoint, epsilon: Double) =
    abs(cross(a, b, point)) <= epsilon &&
        point.x >= minOf(a.x, b.x) - epsilon && point.x <= maxOf(a.x, b.x) + epsilon &&
        point.y >= minOf(a.y, b.y) - epsilon && point.y <= maxOf(a.y, b.y) + epsilon

private fun cross(a: UntanglePoint, b: UntanglePoint, c: UntanglePoint) =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

private fun distance(first: UntanglePoint, second: UntanglePoint) = hypot(first.x - second.x, first.y - second.y)

private fun List<UntanglePoint>.allSeparated(minimum: Double, epsilon: Double) =
    indices.all { first -> ((first + 1) until size).all { second -> distance(this[first], this[second]) >= minimum - epsilon } }

private fun validBounds(bounds: UntangleBounds) =
    bounds.left.isFinite() && bounds.top.isFinite() && bounds.right.isFinite() && bounds.bottom.isFinite() &&
        bounds.left < bounds.right && bounds.top < bounds.bottom

private fun contains(bounds: UntangleBounds, point: UntanglePoint, epsilon: Double) =
    point.x >= bounds.left - epsilon && point.x <= bounds.right + epsilon &&
        point.y >= bounds.top - epsilon && point.y <= bounds.bottom + epsilon

private fun circlePoints(count: Int, offset: Double): List<UntanglePoint> =
    List(count) { index ->
        val angle = offset - PI / 2 + index * 2 * PI / count
        UntanglePoint(tenth(50 + 37 * cos(angle)), tenth(50 + 37 * sin(angle)))
    }

private fun tenth(value: Double) = round(value * 10.0) / 10.0
private fun nodeId(index: Int) = "node-$index"
private fun issue(code: String, path: String) = ValidationIssue(code, path)
private fun rejected(state: UntangleState, reason: String) = Transition(false, false, state, reason)
