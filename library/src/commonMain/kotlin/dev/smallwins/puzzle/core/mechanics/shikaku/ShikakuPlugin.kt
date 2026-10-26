package dev.smallwins.puzzle.core.mechanics.shikaku

import dev.smallwins.puzzle.core.ActivityCapability
import dev.smallwins.puzzle.core.ActivityManifest
import dev.smallwins.puzzle.core.ActivityPlugin
import dev.smallwins.puzzle.core.DifficultyBand
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

const val SHIKAKU_RULES_VERSION = 1
const val SHIKAKU_GENERATOR_VERSION = 1

object ShikakuPlugin :
    ActivityPlugin<
        ShikakuLevel,
        ShikakuState,
        ShikakuAction,
        ShikakuWitness,
        ShikakuEvaluationMetrics,
        ShikakuGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "shikaku",
            rulesVersion = SHIKAKU_RULES_VERSION,
            aliases = emptyList(),
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

    override fun validateLevel(level: ShikakuLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != SHIKAKU_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (p.rows !in 2..8) issues += issue("grid.rows", "$.payload.rows")
        if (p.cols !in 2..8) issues += issue("grid.cols", "$.payload.cols")
        val size = p.rows * p.cols
        if (p.clues.isEmpty()) issues += issue("clues.empty", "$.payload.clues")
        if (p.clues.map { it.cell }.distinct().size != p.clues.size) issues += issue("clues.duplicate", "$.payload.clues")
        p.clues.forEachIndexed { index, clue ->
            if (clue.cell !in 0 until size || clue.area !in 1..size) issues += issue("clue.invalid", "$.payload.clues[$index]")
        }
        if (p.initialRegions.size != size || p.initialRegions.any { it !in -1 until p.clues.size }) {
            issues += issue("regions.invalid", "$.payload.initialRegions")
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: ShikakuLevel) = ShikakuState(level.payload.initialRegions.toList())

    override fun validateState(
        level: ShikakuLevel,
        state: ShikakuState,
    ): ValidationResult {
        val valid =
            state.regions.size == level.payload.rows * level.payload.cols &&
                state.regions.all { it in -1 until level.payload.clues.size }
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("regions.invalid", "$.regions")))
    }

    override fun transition(
        level: ShikakuLevel,
        state: ShikakuState,
        action: ShikakuAction,
    ): Transition<ShikakuState> {
        val p = level.payload
        if (action.type == "clear") {
            if (action.clue !in p.clues.indices) return rejected(state, "invalid-clue")
            val regions = state.regions.map { if (it == action.clue) -1 else it }
            val next = ShikakuState(regions)
            return Transition(true, next != state, if (next == state) state else next)
        }
        val top = action.top
        val left = action.left
        val bottom = action.bottom
        val right = action.right
        if (
            action.type != "place" || action.clue !in p.clues.indices ||
            top == null || bottom == null || left == null || right == null ||
            top !in 0 until p.rows || bottom !in 0 until p.rows ||
            left !in 0 until p.cols || right !in 0 until p.cols ||
            top > bottom || left > right
        ) return rejected(state, "invalid-rectangle")
        val cells = rectangleCells(p, top, left, bottom, right)
        val clue = p.clues[action.clue]
        if (clue.cell !in cells) return rejected(state, "missing-clue")
        if (cells.size != clue.area) return rejected(state, "area-mismatch")
        if (cells.any { state.regions[it] != -1 && state.regions[it] != action.clue }) return rejected(state, "overlap")
        val regions = state.regions.toMutableList()
        cells.forEach { regions[it] = action.clue }
        val next = ShikakuState(regions)
        return Transition(true, next != state, if (next == state) state else next)
    }

    override fun evaluate(
        level: ShikakuLevel,
        state: ShikakuState,
    ): Evaluation<ShikakuEvaluationMetrics> {
        val covered = state.regions.count { it >= 0 }
        val solved =
            covered == state.regions.size && level.payload.clues.indices.all { index ->
                val cells = regionCells(state, index)
                cells.size == level.payload.clues[index].area &&
                    level.payload.clues[index].cell in cells && isRectangle(level.payload, cells)
            }
        return Evaluation(
            solved,
            if (solved) 1.0 else covered.toDouble() / state.regions.size,
            ShikakuEvaluationMetrics(covered, state.regions.size),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<ShikakuLevel, ShikakuWitness, ShikakuGeneratorMetrics> {
        require(request.generatorVersion == SHIKAKU_GENERATOR_VERSION)
        val defaultSize = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL, DifficultyBand.EASY -> 3
            DifficultyBand.MEDIUM -> 4
            DifficultyBand.HARD, DifficultyBand.EXPERT -> 5
        }
        val rows = request.parameters["rows"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(2, 8)
        val cols = request.parameters["cols"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(2, 8)
        val random = StableRandom("shikaku/v$SHIKAKU_GENERATOR_VERSION/${request.seed}/${rows}x$cols")
        var attempts = 0
        while (attempts < 1_000) {
            attempts += 1
            val clues = List(rows) { row -> ShikakuClue(row * cols + random.nextInt(cols), cols) }
            val payload = ShikakuPayload(rows, cols, clues, List(rows * cols) { -1 })
            val level = generatedLevel(request, payload)
            val result = solveDetails(level, 2, 100_000)
            if (!result.exceeded && result.solutions.size == 1) {
                return GenerationResult(
                    level,
                    result.solutions.first(),
                    ShikakuGeneratorMetrics(rows, cols, clues.size, attempts),
                )
            }
        }
        error("Could not generate a unique Shikaku puzzle")
    }

    override fun solve(level: ShikakuLevel): ShikakuWitness? = solveDetails(level, 1, 100_000).solutions.firstOrNull()

    override fun hint(
        level: ShikakuLevel,
        state: ShikakuState,
        used: Int,
    ): PuzzleHint? {
        val answer = solve(level) ?: return null
        val clue = level.payload.clues.indices.firstOrNull { regionCells(state, it).isEmpty() } ?: return null
        val cells = regionCells(answer, clue)
        val rows = cells.map { it / level.payload.cols }
        val columns = cells.map { it % level.payload.cols }
        val data =
            JsonObject(
                mapOf(
                    "clue" to JsonPrimitive(clue),
                    "area" to JsonPrimitive(level.payload.clues[clue].area),
                    "top" to JsonPrimitive(rows.min()),
                    "left" to JsonPrimitive(columns.min()),
                    "bottom" to JsonPrimitive(rows.max()),
                    "right" to JsonPrimitive(columns.max()),
                ),
            )
        return PuzzleHint(if (used == 0) "area" else "rectangle", data)
    }

    fun solveDetails(
        level: ShikakuLevel,
        limit: Int = 2,
        budget: Int = 100_000,
    ): ShikakuSolveResult {
        val p = level.payload
        val options = p.clues.map { clue ->
            buildList {
                for (top in 0 until p.rows) for (left in 0 until p.cols) {
                    for (bottom in top until p.rows) for (right in left until p.cols) {
                        val cells = rectangleCells(p, top, left, bottom, right)
                        if (cells.size == clue.area && clue.cell in cells) add(cells)
                    }
                }
            }
        }
        val found = mutableListOf<ShikakuState>()
        val regions = MutableList(p.rows * p.cols) { -1 }
        var nodes = 0
        var exceeded = false
        fun search(clue: Int) {
            if (found.size >= limit || exceeded) return
            nodes += 1
            if (nodes > budget) {
                exceeded = true
                return
            }
            if (clue == p.clues.size) {
                if (regions.none { it == -1 }) found += ShikakuState(regions.toList())
                return
            }
            options[clue].forEach { cells ->
                if (cells.any { regions[it] != -1 }) return@forEach
                cells.forEach { regions[it] = clue }
                search(clue + 1)
                cells.forEach { regions[it] = -1 }
            }
        }
        search(0)
        return ShikakuSolveResult(found, nodes, exceeded)
    }

    private fun generatedLevel(
        request: GenerationRequest,
        payload: ShikakuPayload,
    ) = PuzzleLevel(
        identity =
            PuzzleIdentity(
                mechanicId = manifest.mechanicId,
                productGameId = manifest.mechanicId,
                levelPackId = "generated",
                levelId = "generated-${stableHash("v$SHIKAKU_GENERATOR_VERSION/${request.seed}/${payload.rows}x${payload.cols}").toString(16)}",
                schemaVersion = 1,
                rulesVersion = SHIKAKU_RULES_VERSION,
                contentVersion = 1,
                generatorVersion = SHIKAKU_GENERATOR_VERSION,
                seed = request.seed,
            ),
        difficulty = request.difficulty,
        payload = payload,
    )
}

fun rectangleCells(
    payload: ShikakuPayload,
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
) = buildList {
    for (row in top..bottom) for (column in left..right) add(row * payload.cols + column)
}

fun regionCells(
    state: ShikakuState,
    clue: Int,
) = state.regions.indices.filter { state.regions[it] == clue }

private fun isRectangle(
    payload: ShikakuPayload,
    cells: List<Int>,
): Boolean {
    if (cells.isEmpty()) return false
    val rows = cells.map { it / payload.cols }
    val columns = cells.map { it % payload.cols }
    return rectangleCells(payload, rows.min(), columns.min(), rows.max(), columns.max()).size == cells.size
}

private fun issue(
    code: String,
    path: String,
) = ValidationIssue(code, path)

private fun rejected(
    state: ShikakuState,
    reason: String,
) = Transition(false, false, state, reason)
