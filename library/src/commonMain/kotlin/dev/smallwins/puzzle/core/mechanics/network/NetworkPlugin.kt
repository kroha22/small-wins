package dev.smallwins.puzzle.core.mechanics.network

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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

const val NETWORK_RULES_VERSION = 1
const val NETWORK_GENERATOR_VERSION = 1

object NetworkPlugin :
    ActivityPlugin<
        NetworkLevel,
        NetworkState,
        NetworkAction,
        NetworkWitness,
        NetworkEvaluationMetrics,
        NetworkGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "network",
            rulesVersion = NETWORK_RULES_VERSION,
            aliases = listOf("pipes"),
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

    override fun validateLevel(level: NetworkLevel): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val payload = level.payload
        if (level.identity.mechanicId != manifest.mechanicId) {
            issues += ValidationIssue("identity.mechanic", "$.identity.mechanicId")
        }
        if (level.identity.rulesVersion != NETWORK_RULES_VERSION) {
            issues += ValidationIssue("identity.rules-version", "$.identity.rulesVersion")
        }
        if (payload.rows !in 2..12) issues += ValidationIssue("grid.rows", "$.payload.rows")
        if (payload.cols !in 2..12) issues += ValidationIssue("grid.cols", "$.payload.cols")
        val cellCount = payload.rows * payload.cols
        if (payload.sourceCell !in 0 until cellCount) {
            issues += ValidationIssue("source.out-of-range", "$.payload.sourceCell")
        }
        if (payload.targetCell != null && payload.targetCell !in 0 until cellCount) {
            issues += ValidationIssue("target.out-of-range", "$.payload.targetCell")
        }
        if (payload.ports.size != cellCount) {
            issues += ValidationIssue("ports.length", "$.payload.ports")
        }
        if (payload.initialRotations.size != cellCount || payload.initialRotations.any { it !in 0..3 }) {
            issues += ValidationIssue("rotations.invalid", "$.payload.initialRotations")
        }
        payload.ports.forEachIndexed { cell, ports ->
            if (ports.isEmpty() || ports.size > 4 || ports.any { it !in 0..3 } || ports.distinct().size != ports.size) {
                issues += ValidationIssue("ports.cell", "$.payload.ports[$cell]")
            }
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: NetworkLevel) = NetworkState(level.payload.initialRotations.toList())

    override fun validateState(
        level: NetworkLevel,
        state: NetworkState,
    ): ValidationResult {
        val valid = state.rotations.size == level.payload.ports.size && state.rotations.all { it in 0..3 }
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(ValidationIssue("rotations.invalid", "$.rotations")))
    }

    override fun transition(
        level: NetworkLevel,
        state: NetworkState,
        action: NetworkAction,
    ): Transition<NetworkState> {
        if (action.type != "rotate" || action.cell !in level.payload.ports.indices || action.quarterTurns !in setOf(-1, 1)) {
            return Transition(false, false, state, reason = "invalid-action")
        }
        val rotations = state.rotations.toMutableList()
        rotations[action.cell] = (rotations[action.cell] + action.quarterTurns + 4) % 4
        val next = NetworkState(rotations)
        if (portsAt(level, state, action.cell).sorted() == portsAt(level, next, action.cell).sorted()) {
            return Transition(true, false, state)
        }
        return Transition(
            accepted = true,
            changed = true,
            state = next,
            events =
                listOf(
                    EngineEvent(
                        type = "piece-rotated",
                        data = kotlinx.serialization.json.JsonObject(mapOf("cell" to JsonPrimitive(action.cell))),
                    ),
                ),
        )
    }

    override fun evaluate(
        level: NetworkLevel,
        state: NetworkState,
    ): Evaluation<NetworkEvaluationMetrics> {
        val connections = inspect(level, state)
        val solved = connections.reached.size == level.payload.ports.size && connections.leaks.isEmpty()
        return Evaluation(
            solved = solved,
            progress = if (solved) 1.0 else connections.reached.size.toDouble() / level.payload.ports.size,
            metrics =
                NetworkEvaluationMetrics(
                    reachedCells = connections.reached.size,
                    totalCells = level.payload.ports.size,
                    leaks = connections.leaks.size,
                ),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<NetworkLevel, NetworkWitness, NetworkGeneratorMetrics> {
        require(request.generatorVersion == NETWORK_GENERATOR_VERSION) {
            "Unsupported Network generator version: ${request.generatorVersion}"
        }
        if ((request.parameters["profile"] as? JsonPrimitive)?.contentOrNull == "ludeni-v3") return generateLudeni(request)
        require(request.difficulty.normalized in 0.0..1.0) { "Difficulty must be between 0 and 1" }
        val defaultSize =
            when (request.difficulty.band) {
                DifficultyBand.TUTORIAL -> 2
                DifficultyBand.EASY -> 3
                DifficultyBand.MEDIUM -> 4
                DifficultyBand.HARD -> 5
                DifficultyBand.EXPERT -> 6
            }
        val rows = request.parameters["rows"].asDimensionOr(defaultSize)
        val cols = request.parameters["cols"].asDimensionOr(defaultSize)
        val random = StableRandom("network/v$NETWORK_GENERATOR_VERSION/${request.seed}/${rows}x$cols")
        val ports = List(rows * cols) { mutableListOf<Int>() }
        val seen = mutableSetOf(0)
        val pending = mutableListOf(0)
        while (pending.isNotEmpty()) {
            val cell = pending.last()
            val choice = random.shuffled(neighbours(cell, rows, cols).filterNot { it.cell in seen }).firstOrNull()
            if (choice == null) {
                pending.removeAt(pending.lastIndex)
            } else {
                ports[cell] += choice.direction
                ports[choice.cell] += opposite(choice.direction)
                seen += choice.cell
                pending += choice.cell
            }
        }
        val initialRotations = List(ports.size) { random.nextInt(4) }.toMutableList()
        if (initialRotations.indices.all { sameOrientation(ports[it], initialRotations[it], 0) }) {
            val turnable = ports.indexOfFirst { !sameOrientation(it, 0, 1) }
            if (turnable >= 0) initialRotations[turnable] = 1
        }
        val level =
            PuzzleLevel(
                identity =
                    PuzzleIdentity(
                        mechanicId = "network",
                        productGameId = "network",
                        levelPackId = "generated",
                        levelId = "generated-${stableHash("v$NETWORK_GENERATOR_VERSION/${request.seed}/${rows}x$cols").toString(16)}",
                        schemaVersion = 1,
                        rulesVersion = NETWORK_RULES_VERSION,
                        contentVersion = 1,
                        generatorVersion = NETWORK_GENERATOR_VERSION,
                        seed = request.seed,
                    ),
                difficulty = request.difficulty,
                payload = NetworkPayload(rows, cols, 0, ports.map { it.toList() }, initialRotations),
            )
        return GenerationResult(
            level = level,
            solutionWitness = NetworkState(List(ports.size) { 0 }),
            metrics =
                NetworkGeneratorMetrics(
                    rows = rows,
                    cols = cols,
                    boardCells = rows * cols,
                    deadEnds = ports.count { it.size == 1 },
                    branches = ports.count { it.size >= 3 },
                ),
        )
    }

    override fun solve(level: NetworkLevel): NetworkWitness? = solveWithBudget(level, 20_000)

    override fun hint(
        level: NetworkLevel,
        state: NetworkState,
        used: Int,
    ): PuzzleHint? {
        val answer = solve(level) ?: return null
        val cell = level.payload.ports.indices.firstOrNull {
            !sameOrientation(level.payload.ports[it], state.rotations[it], answer.rotations[it])
        } ?: return null
        val data =
            buildMap {
                put("cell", JsonPrimitive(cell))
                put("row", JsonPrimitive(cell / level.payload.cols))
                put("column", JsonPrimitive(cell % level.payload.cols))
                if (used >= 2) put("targetRotation", JsonPrimitive(answer.rotations[cell]))
            }
        return PuzzleHint(
            kind = if (used == 0) "row" else if (used == 1) "cell" else "orientation",
            data = kotlinx.serialization.json.JsonObject(data),
        )
    }

    fun inspect(
        level: NetworkLevel,
        state: NetworkState,
    ): NetworkConnections = connections(level, state)

    private fun generateLudeni(request: GenerationRequest): GenerationResult<NetworkLevel, NetworkWitness, NetworkGeneratorMetrics> {
        val tutorial = request.difficulty.band == DifficultyBand.TUTORIAL
        val t = request.difficulty.normalized.coerceIn(0.0, 1.0)
        val size = if (tutorial) 3 else ludeniScale(t, 4, 6)
        val random = networkLudeniRandom(request)
        val path = mutableListOf<Int>()
        val edges = mutableListOf<Pair<Int, Int>>()
        if (tutorial) {
            repeat(size) { row ->
                val columns = if (row % 2 == 1) (size - 1 downTo 0) else (0 until size)
                columns.forEach { path += row * size + it }
            }
            path.drop(1).forEachIndexed { index, cell -> edges += path[index] to cell }
        } else {
            val visited = mutableSetOf(0); val stack = mutableListOf(0); path += 0
            while (stack.isNotEmpty()) {
                val active = if (random.nextDouble() < t * .7) random.nextInt(stack.size) else stack.lastIndex
                val current = stack[active]; val row = current / size; val column = current % size
                val candidates = listOf(if (row > 0) current - size else null, if (column < size - 1) current + 1 else null, if (row < size - 1) current + size else null, if (column > 0) current - 1 else null).filterNotNull().filterNot { it in visited }
                val next = random.shuffled(candidates).firstOrNull()
                if (next == null) stack.removeAt(active) else { visited += next; stack += next; path += next; edges += current to next }
            }
            val transform = random.nextInt(8)
            if (transform != 0) {
                path.indices.forEach { index -> path[index] = ludeniTransformIndex(path[index], size, transform) }
                edges.indices.forEach { index -> edges[index] = ludeniTransformIndex(edges[index].first, size, transform) to ludeniTransformIndex(edges[index].second, size, transform) }
            }
        }
        val source = path.first(); val degree = MutableList(size * size) { 0 }
        edges.forEach { (a, b) -> degree[a] += 1; degree[b] += 1 }
        fun direction(from: Int, to: Int) = when (to) { from - size -> 0; from + 1 -> 1; from + size -> 2; else -> 3 }
        val ports = List(size * size) { mutableListOf<Int>() }
        edges.forEach { (a, b) -> ports[a] += direction(a, b); ports[b] += direction(b, a) }
        val target = path.asReversed().firstOrNull { it != source && degree[it] == 1 } ?: path.last()
        val payload = NetworkPayload(size, size, source, ports.map { it.toList() }, List(size * size) { 0 }, target)
        val level = PuzzleLevel(PuzzleIdentity("network", "network", "generated", "generated-${stableHash("ludeni-v3/${request.seed}/${request.variant}").toString(16)}", 1, NETWORK_RULES_VERSION, 1, 1, request.seed), request.difficulty, payload)
        return GenerationResult(level, NetworkState(List(size * size) { 0 }), NetworkGeneratorMetrics(size, size, size * size, ports.count { it.size == 1 }, ports.count { it.size >= 3 }))
    }

    fun solveWithBudget(
        level: NetworkLevel,
        budget: Int,
    ): NetworkWitness? {
        val rotations = MutableList(level.payload.ports.size) { -1 }
        val options =
            level.payload.ports.mapIndexed { cell, ports ->
                val seen = mutableSetOf<List<Int>>()
                (0..3).filter { rotation ->
                    val directions = ports.map { (it + rotation) % 4 }.sorted()
                    directions.none { adjacent(level.payload, cell, it) == null } && seen.add(directions)
                }
            }
        var visited = 0

        fun search(): NetworkState? {
            visited += 1
            if (visited > budget) return null
            var selectedCell = -1
            var selectedCandidates = emptyList<Int>()
            rotations.indices.forEach { cell ->
                if (rotations[cell] != -1) return@forEach
                val candidates =
                    options[cell].filter { rotation ->
                        (0..3).all { direction ->
                            val neighbour = adjacent(level.payload, cell, direction)
                            neighbour == null ||
                                rotations[neighbour] == -1 ||
                                hasPort(level.payload.ports[cell], rotation, direction) ==
                                hasPort(level.payload.ports[neighbour], rotations[neighbour], opposite(direction))
                        }
                    }
                if (candidates.isEmpty()) return null
                if (selectedCell == -1 || candidates.size < selectedCandidates.size) {
                    selectedCell = cell
                    selectedCandidates = candidates
                }
            }
            if (selectedCell == -1) {
                val state = NetworkState(rotations.toList())
                return state.takeIf { evaluate(level, it).solved }
            }
            selectedCandidates.forEach { rotation ->
                rotations[selectedCell] = rotation
                search()?.let { return it }
                if (visited > budget) return null
            }
            rotations[selectedCell] = -1
            return null
        }

        return search()
    }
}

private fun networkLudeniRandom(request: GenerationRequest): StableRandom {
    val mode = if (request.difficulty.band == DifficultyBand.TUTORIAL) "tutorial" else "regular"
    val number = request.difficulty.normalized.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    return StableRandom("network:v3:$mode:$number:${request.seed}:${request.variant ?: "1"}")
}

private fun ludeniScale(t: Double, minimum: Int, maximum: Int) = kotlin.math.floor(minimum + (maximum - minimum) * t + .5).toInt()

private fun ludeniTransformIndex(index: Int, size: Int, transform: Int): Int {
    val row = index / size; val column = index % size
    val point = when (transform) {
        1 -> column to size - 1 - row
        2 -> size - 1 - row to size - 1 - column
        3 -> size - 1 - column to row
        4 -> row to size - 1 - column
        5 -> size - 1 - row to column
        6 -> column to row
        7 -> size - 1 - column to size - 1 - row
        else -> row to column
    }
    return point.first * size + point.second
}

fun portsAt(
    level: NetworkLevel,
    state: NetworkState,
    cell: Int,
): List<Int> = level.payload.ports[cell].map { (it + state.rotations[cell]) % 4 }

fun adjacent(
    payload: NetworkPayload,
    cell: Int,
    direction: Int,
): Int? {
    val row = cell / payload.cols
    val column = cell % payload.cols
    val nextRow = row + listOf(-1, 0, 1, 0)[direction]
    val nextColumn = column + listOf(0, 1, 0, -1)[direction]
    return if (nextRow !in 0 until payload.rows || nextColumn !in 0 until payload.cols) null else nextRow * payload.cols + nextColumn
}

private fun connections(
    level: NetworkLevel,
    state: NetworkState,
): NetworkConnections {
    val reached = mutableSetOf(level.payload.sourceCell)
    val pending = mutableListOf(level.payload.sourceCell)
    while (pending.isNotEmpty()) {
        val cell = pending.removeAt(pending.lastIndex)
        portsAt(level, state, cell).forEach { direction ->
            val neighbour = adjacent(level.payload, cell, direction)
            if (neighbour != null && neighbour !in reached && opposite(direction) in portsAt(level, state, neighbour)) {
                reached += neighbour
                pending += neighbour
            }
        }
    }
    val leaks =
        level.payload.ports.indices.flatMap { cell ->
            portsAt(level, state, cell).mapNotNull { direction ->
                val neighbour = adjacent(level.payload, cell, direction)
                if (neighbour == null || opposite(direction) !in portsAt(level, state, neighbour)) NetworkLeak(cell, direction) else null
            }
        }
    return NetworkConnections(reached.sorted(), leaks)
}

private data class Neighbour(val cell: Int, val direction: Int)

private fun neighbours(
    cell: Int,
    rows: Int,
    cols: Int,
): List<Neighbour> {
    val row = cell / cols
    val column = cell % cols
    return listOf(row - 1 to column, row to column + 1, row + 1 to column, row to column - 1)
        .mapIndexedNotNull { direction, (nextRow, nextColumn) ->
            if (nextRow in 0 until rows && nextColumn in 0 until cols) Neighbour(nextRow * cols + nextColumn, direction) else null
        }
}

private fun opposite(direction: Int) = (direction + 2) % 4

private fun hasPort(
    ports: List<Int>,
    rotation: Int,
    direction: Int,
) = ports.any { (it + rotation) % 4 == direction }

private fun sameOrientation(
    ports: List<Int>,
    first: Int,
    second: Int,
): Boolean {
    fun rotated(amount: Int) = ports.map { (it + amount) % 4 }.sorted()
    return rotated(first) == rotated(second)
}

private fun kotlinx.serialization.json.JsonElement?.asDimensionOr(fallback: Int): Int =
    (this as? JsonPrimitive)?.intOrNull?.takeIf { it in 2..12 } ?: fallback
