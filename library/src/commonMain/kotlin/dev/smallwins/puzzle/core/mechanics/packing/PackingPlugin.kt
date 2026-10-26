package dev.smallwins.puzzle.core.mechanics.packing

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

const val PACKING_RULES_VERSION = 1
const val PACKING_GENERATOR_VERSION = 3

object PackingPlugin : ActivityPlugin<PackingLevel, PackingState, PackingAction, PackingWitness, PackingEvaluationMetrics, PackingGeneratorMetrics> {
    override val manifest = ActivityManifest(
        mechanicId = "packing",
        rulesVersion = PACKING_RULES_VERSION,
        aliases = listOf("cargo-packing"),
        capabilities = listOf(
            ActivityCapability.DETERMINISTIC_GENERATION,
            ActivityCapability.DIFFICULTY,
            ActivityCapability.SOLVER,
            ActivityCapability.HINTS,
            ActivityCapability.SERIALIZABLE_STATE,
            ActivityCapability.SOLUTION_WITNESS,
        ),
    )

    override fun validateLevel(level: PackingLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != PACKING_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (p.size !in 2..10) issues += issue("grid.size", "$.payload.size")
        if (p.pieces.isEmpty() || p.pieces.map { it.id }.distinct().size != p.pieces.size) issues += issue("pieces.invalid", "$.payload.pieces")
        if (p.pieces.any { it.id.isBlank() || it.cells.isEmpty() || normalize(it.cells).toSet().size != it.cells.size || !connected(it.cells) }) {
            issues += issue("piece.invalid", "$.payload.pieces")
        }
        if (p.pieces.sumOf { it.cells.size } != p.size * p.size) issues += issue("area.invalid", "$.payload.pieces")
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: PackingLevel) = PackingState(List(level.payload.pieces.size) { 0 }, List(level.payload.pieces.size) { null })

    override fun validateState(level: PackingLevel, state: PackingState): ValidationResult {
        val valid = state.rotations.size == level.payload.pieces.size && state.origins.size == level.payload.pieces.size &&
            state.rotations.all { it in 0..3 } && board(level.payload, state) != null
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("placements.invalid", "$.state")))
    }

    override fun transition(level: PackingLevel, state: PackingState, action: PackingAction): Transition<PackingState> {
        if (action.piece !in level.payload.pieces.indices) return rejected(state, "unknown-piece")
        val rotations = state.rotations.toMutableList()
        val origins = state.origins.toMutableList()
        when (action.type) {
            "rotate" -> {
                if (origins[action.piece] != null) return rejected(state, "piece-placed")
                rotations[action.piece] = (rotations[action.piece] + 1) % 4
            }
            "remove" -> {
                if (origins[action.piece] == null) return Transition(true, false, state)
                origins[action.piece] = null
            }
            "place" -> {
                val origin = action.origin ?: return rejected(state, "missing-origin")
                origins[action.piece] = origin
                val next = PackingState(rotations, origins)
                if (board(level.payload, next) == null) return rejected(state, "invalid-placement")
            }
            else -> return rejected(state, "unknown-action")
        }
        val next = PackingState(rotations, origins)
        return Transition(true, next != state, next, events = listOf(EngineEvent("packing-${action.type}", JsonObject(mapOf("piece" to JsonPrimitive(action.piece))))))
    }

    override fun evaluate(level: PackingLevel, state: PackingState): Evaluation<PackingEvaluationMetrics> {
        val occupied = board(level.payload, state)?.count { it != null } ?: 0
        val placed = state.origins.count { it != null }
        val solved = placed == level.payload.pieces.size && occupied == level.payload.size * level.payload.size
        return Evaluation(solved, occupied.toDouble() / (level.payload.size * level.payload.size), PackingEvaluationMetrics(placed, level.payload.pieces.size, occupied, level.payload.size * level.payload.size))
    }

    override fun generate(request: GenerationRequest): GenerationResult<PackingLevel, PackingWitness, PackingGeneratorMetrics> {
        require(request.generatorVersion == PACKING_GENERATOR_VERSION)
        val difficulty = request.difficulty.normalized.coerceIn(0.0, 1.0)
        val minimumBent = if (request.difficulty.band == DifficultyBand.TUTORIAL) 0 else scale(difficulty, 2, 5)
        repeat(60) { attempt ->
            val result = construct(request, attempt)
            val bent = result.first.pieces.count { piece -> piece.cells.map { it.x }.distinct().size > 1 && piece.cells.map { it.y }.distinct().size > 1 }
            if (bent >= minimumBent) {
                val level = level(request, result.first, attempt)
                return GenerationResult(level, result.second, PackingGeneratorMetrics(result.first.size, result.first.pieces.size, bent))
            }
        }
        error("Packing shape-complexity budget exhausted")
    }

    override fun solve(level: PackingLevel): PackingWitness? {
        val rotations = MutableList(level.payload.pieces.size) { 0 }
        val origins = MutableList<Int?>(level.payload.pieces.size) { null }
        val occupied = mutableSetOf<Int>()
        fun visit(piece: Int): Boolean {
            if (piece == level.payload.pieces.size) return occupied.size == level.payload.size * level.payload.size
            val seen = mutableSetOf<List<PackingPoint>>()
            repeat(4) { rotation ->
                val shape = rotated(level.payload.pieces[piece].cells, rotation)
                if (!seen.add(shape)) return@repeat
                repeat(level.payload.size * level.payload.size) { origin ->
                    val cells = absoluteCells(level.payload.size, shape, origin) ?: return@repeat
                    if (cells.any { it in occupied }) return@repeat
                    occupied += cells; rotations[piece] = rotation; origins[piece] = origin
                    if (visit(piece + 1)) return true
                    occupied.removeAll(cells.toSet()); origins[piece] = null
                }
            }
            return false
        }
        return if (visit(0)) PackingState(rotations, origins) else null
    }

    override fun hint(level: PackingLevel, state: PackingState, used: Int): PuzzleHint? {
        val answer = solve(level) ?: return null
        val piece = state.origins.indices.firstOrNull { state.origins[it] != answer.origins[it] || state.rotations[it] != answer.rotations[it] } ?: return null
        return PuzzleHint(if (used < 2) "piece" else "placement", JsonObject(buildMap {
            put("piece", JsonPrimitive(piece))
            if (used >= 2) { put("rotation", JsonPrimitive(answer.rotations[piece])); put("origin", JsonPrimitive(answer.origins[piece]!!)) }
        }))
    }

    private fun construct(request: GenerationRequest, attempt: Int): Pair<PackingPayload, PackingState> {
        val tutorial = request.difficulty.band == DifficultyBand.TUTORIAL
        val t = request.difficulty.normalized.coerceIn(0.0, 1.0)
        val size = if (tutorial) 3 else scale(t, 4, 6)
        val count = if (tutorial) 3 else scale(t, 4, 8)
        val mode = if (tutorial) "tutorial" else "regular"
        val random = StableRandom("packing:v3:$mode:$t:${request.seed}:packing-attempt:$attempt:1")
        var path = List(size) { y -> List(size) { x -> PackingPoint(if (y % 2 == 1) size - 1 - x else x, y) } }.flatten()
        repeat(250) {
            val first = random.nextDouble() < .5
            val end = if (first) path.first() else path.last()
            val choices = path.indices.filter { k -> distance(path[k], end) == 1 && if (first) k > 1 else k < path.size - 2 }
            if (choices.isNotEmpty()) {
                val k = choices[random.nextInt(choices.size)]
                path = if (first) path.subList(0, k).reversed() + path.subList(k, path.size) else path.subList(0, k + 1) + path.subList(k + 1, path.size).reversed()
            }
        }
        val lengths = MutableList(count) { 2 }
        repeat(size * size - 2 * count) { lengths[random.nextInt(count)] += 1 }
        var cursor = 0
        val shaped = lengths.map { length ->
            val cells = path.subList(cursor, cursor + length); cursor += length
            val minX = cells.minOf { it.x }; val minY = cells.minOf { it.y }
            normalize(cells.map { PackingPoint(it.x - minX, it.y - minY) }) to (minY * size + minX)
        }
        val ordered = random.shuffled(shaped)
        val pieces = ordered.mapIndexed { index, item -> PackingPiece("piece-${index + 1}", item.first) }
        return PackingPayload(size, pieces) to PackingState(List(count) { 0 }, ordered.map { it.second })
    }

    private fun level(request: GenerationRequest, payload: PackingPayload, attempt: Int) = PuzzleLevel(
        PuzzleIdentity("packing", "packing", "generated", "generated-${stableHash("v3/${request.seed}/$attempt").toString(16)}", 1, PACKING_RULES_VERSION, 1, PACKING_GENERATOR_VERSION, request.seed),
        request.difficulty,
        payload,
    )
}

internal fun normalize(cells: List<PackingPoint>): List<PackingPoint> {
    val minX = cells.minOf { it.x }; val minY = cells.minOf { it.y }
    return cells.map { PackingPoint(it.x - minX, it.y - minY) }.sortedWith(compareBy<PackingPoint> { it.y }.thenBy { it.x })
}

internal fun rotated(cells: List<PackingPoint>, turns: Int): List<PackingPoint> {
    var result = normalize(cells)
    repeat((turns % 4 + 4) % 4) { result = normalize(result.map { PackingPoint(-it.y, it.x) }) }
    return result
}

private fun board(payload: PackingPayload, state: PackingState): List<Int?>? {
    val result = MutableList<Int?>(payload.size * payload.size) { null }
    state.origins.forEachIndexed { index, origin ->
        if (origin == null) return@forEachIndexed
        val cells = absoluteCells(payload.size, rotated(payload.pieces[index].cells, state.rotations[index]), origin) ?: return null
        if (cells.any { result[it] != null }) return null
        cells.forEach { result[it] = index }
    }
    return result
}

private fun absoluteCells(size: Int, shape: List<PackingPoint>, origin: Int): List<Int>? {
    if (origin !in 0 until size * size) return null
    val ox = origin % size; val oy = origin / size
    val points = shape.map { PackingPoint(ox + it.x, oy + it.y) }
    if (points.any { it.x !in 0 until size || it.y !in 0 until size }) return null
    return points.map { it.y * size + it.x }
}

private fun connected(cells: List<PackingPoint>): Boolean {
    if (cells.isEmpty()) return false
    val remaining = cells.toSet(); val seen = mutableSetOf(cells.first()); val queue = mutableListOf(cells.first())
    var cursor = 0
    while (cursor < queue.size) {
        val p = queue[cursor++]
        listOf(PackingPoint(p.x + 1, p.y), PackingPoint(p.x - 1, p.y), PackingPoint(p.x, p.y + 1), PackingPoint(p.x, p.y - 1)).forEach {
            if (it in remaining && seen.add(it)) queue += it
        }
    }
    return seen.size == cells.size
}

private fun distance(a: PackingPoint, b: PackingPoint) = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)
private fun scale(t: Double, a: Int, b: Int) = kotlin.math.round(a + (b - a) * t).toInt()
private fun issue(code: String, path: String) = ValidationIssue(code, path)
private fun rejected(state: PackingState, reason: String) = Transition(false, false, state, reason)
