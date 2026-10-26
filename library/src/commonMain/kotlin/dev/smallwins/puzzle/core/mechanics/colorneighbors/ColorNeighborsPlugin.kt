package dev.smallwins.puzzle.core.mechanics.colorneighbors

import dev.smallwins.puzzle.core.ActivityCapability
import dev.smallwins.puzzle.core.ActivityManifest
import dev.smallwins.puzzle.core.ActivityPlugin
import dev.smallwins.puzzle.core.ContinuationRandom
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

const val COLOR_NEIGHBORS_RULES_VERSION = 1
const val COLOR_NEIGHBORS_GENERATOR_VERSION = 1

object ColorNeighborsPlugin :
    ActivityPlugin<
        ColorNeighborsLevel,
        ColorNeighborsState,
        ColorNeighborsAction,
        ColorNeighborsWitness,
        ColorNeighborsEvaluationMetrics,
        ColorNeighborsGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "color-neighbors",
            rulesVersion = COLOR_NEIGHBORS_RULES_VERSION,
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

    override fun validateLevel(level: ColorNeighborsLevel): ValidationResult {
        val payload = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) {
            issues += issue("identity.mechanic", "$.identity.mechanicId")
        }
        if (level.identity.rulesVersion != COLOR_NEIGHBORS_RULES_VERSION) {
            issues += issue("identity.rules-version", "$.identity.rulesVersion")
        }
        if (payload.rows !in 2..5) issues += issue("grid.rows", "$.payload.rows")
        if (payload.cols !in 2..5) issues += issue("grid.cols", "$.payload.cols")
        if (payload.colorCount !in 2..3) issues += issue("colors.count", "$.payload.colorCount")
        if (payload.clues.isEmpty()) issues += issue("clues.empty", "$.payload.clues")
        if (payload.clues.map { it.cell }.distinct().size != payload.clues.size) {
            issues += issue("clues.duplicate-cell", "$.payload.clues")
        }
        val cellCount = payload.rows * payload.cols
        payload.clues.forEachIndexed { index, clue ->
            val validCell = clue.cell in 0 until cellCount
            if (
                !validCell || clue.color !in 0 until payload.colorCount || clue.count < 0 ||
                (validCell && clue.count > neighbors(clue.cell, payload.rows, payload.cols).size)
            ) {
                issues += issue("clue.invalid", "$.payload.clues[$index]")
            }
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: ColorNeighborsLevel): ColorNeighborsState {
        val cells = MutableList<Int?>(level.payload.rows * level.payload.cols) { null }
        level.payload.clues.forEach { cells[it.cell] = it.color }
        return ColorNeighborsState(cells, List(cells.size) { 0 })
    }

    override fun validateState(
        level: ColorNeighborsLevel,
        state: ColorNeighborsState,
    ): ValidationResult {
        val payload = level.payload
        val cellCount = payload.rows * payload.cols
        val allColors = (1 shl payload.colorCount) - 1
        val valid =
            state.cells.size == cellCount && state.excluded.size == cellCount &&
                state.cells.indices.all { index ->
                    val color = state.cells[index]
                    val excluded = state.excluded[index]
                    color == null || color in 0 until payload.colorCount && excluded and (1 shl color) == 0
                } &&
                state.excluded.all { it in 0 until allColors } &&
                payload.clues.all { clue ->
                    state.cells[clue.cell] == clue.color && state.excluded[clue.cell] == 0
                }
        return if (valid) ValidationResult(true) else ValidationResult(false, listOf(issue("state.invalid", "$")))
    }

    override fun transition(
        level: ColorNeighborsLevel,
        state: ColorNeighborsState,
        action: ColorNeighborsAction,
    ): Transition<ColorNeighborsState> {
        val payload = level.payload
        if (action.cell !in state.cells.indices) return rejected(state, "invalid-cell")
        if (payload.clues.any { it.cell == action.cell }) return rejected(state, "fixed-clue")
        if (action.type == "clear") {
            if (state.cells[action.cell] == null && state.excluded[action.cell] == 0) return Transition(true, false, state)
            return accepted(state, action.cell, null, 0)
        }
        val color = action.color
        if (color == null || color !in 0 until payload.colorCount) return rejected(state, "invalid-color")
        return when (action.type) {
            "paint" -> {
                if (state.cells[action.cell] == color && state.excluded[action.cell] == 0) Transition(true, false, state)
                else accepted(state, action.cell, color, 0)
            }
            "exclude" -> {
                val mask = state.excluded[action.cell] xor (1 shl color)
                if (mask == (1 shl payload.colorCount) - 1) rejected(state, "all-colors-excluded")
                else accepted(state, action.cell, null, mask)
            }
            else -> rejected(state, "invalid-action")
        }
    }

    override fun evaluate(
        level: ColorNeighborsLevel,
        state: ColorNeighborsState,
    ): Evaluation<ColorNeighborsEvaluationMetrics> {
        val conflicts = conflicts(level.payload, state)
        val filled = state.cells.count { it != null }
        val solved = filled == state.cells.size && conflicts.isEmpty()
        return Evaluation(
            solved = solved,
            progress = if (solved) 1.0 else filled.toDouble() / state.cells.size,
            metrics = ColorNeighborsEvaluationMetrics(filled, state.cells.size, conflicts),
        )
    }

    override fun generate(
        request: GenerationRequest,
    ): GenerationResult<ColorNeighborsLevel, ColorNeighborsWitness, ColorNeighborsGeneratorMetrics> {
        require(request.generatorVersion == COLOR_NEIGHBORS_GENERATOR_VERSION)
        if ((request.parameters["profile"] as? JsonPrimitive)?.contentOrNull == "ludeni-continuation-v1") return generateLudeniContinuation(request)
        val defaultSize = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL, DifficultyBand.EASY -> 3
            DifficultyBand.MEDIUM -> 4
            DifficultyBand.HARD, DifficultyBand.EXPERT -> 5
        }
        val size = request.parameters["size"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(2, 5)
        val rows = request.parameters["rows"].let { (it as? JsonPrimitive)?.intOrNull ?: size }.coerceIn(2, 5)
        val cols = request.parameters["cols"].let { (it as? JsonPrimitive)?.intOrNull ?: size }.coerceIn(2, 5)
        val defaultColors = if (request.difficulty.band in setOf(DifficultyBand.TUTORIAL, DifficultyBand.EASY)) 2 else 3
        val colorCount = request.parameters["colorCount"].let {
            (it as? JsonPrimitive)?.intOrNull ?: defaultColors
        }.coerceIn(2, 3)
        val random = StableRandom("color-neighbors/v$COLOR_NEIGHBORS_GENERATOR_VERSION/${request.seed}/${rows}x$cols/$colorCount")
        val answer = List(rows * cols) { random.nextInt(colorCount) }
        var clues = answer.mapIndexed { cell, color ->
            ColorNeighborClue(cell, color, neighbors(cell, rows, cols).count { answer[it] == color })
        }
        val clueFloor = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL -> (rows * cols * 0.65).toInt()
            DifficultyBand.EASY -> (rows * cols * 0.55).toInt()
            DifficultyBand.MEDIUM -> (rows * cols * 0.48).toInt()
            DifficultyBand.HARD -> (rows * cols * 0.42).toInt()
            DifficultyBand.EXPERT -> (rows * cols * 0.36).toInt()
        }.coerceAtLeast(1)
        random.shuffled(clues).forEach { candidate ->
            if (clues.size <= clueFloor) return@forEach
            val reduced = clues.filterNot { it == candidate }
            val payload = ColorNeighborsPayload(rows, cols, colorCount, reduced)
            val deduction = deduce(payload, initialStateFor(payload))
            if (!deduction.contradiction && deduction.domains.all(::isSingleton)) clues = reduced
        }
        val payload = ColorNeighborsPayload(rows, cols, colorCount, clues.sortedBy { it.cell })
        val level = generatedLevel(request, payload)
        val solved = solveDetails(level, limit = 2, budget = 200_000)
        require(!solved.exceeded && solved.solutions.size == 1) { "Could not generate a unique Color Neighbors puzzle" }
        return GenerationResult(
            level = level,
            solutionWitness = solved.solutions.first(),
            metrics = ColorNeighborsGeneratorMetrics(rows, cols, colorCount, clues.size, solved.nodes),
        )
    }

    private fun generateLudeniContinuation(
        request: GenerationRequest,
    ): GenerationResult<ColorNeighborsLevel, ColorNeighborsWitness, ColorNeighborsGeneratorMetrics> {
        val size = ((request.parameters["size"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(2, 5)
        val stage = ((request.parameters["stage"] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, 4)
        val colorCount = 3
        val random = ContinuationRandom(request.seed)
        val answer = List(size * size) { random.nextInt(colorCount) }
        var clues = answer.mapIndexed { cell, color -> ColorNeighborClue(cell, color, neighbors(cell, size, size).count { answer[it] == color }) }
        val floor = listOf(16, 15, 14, 13, 1)[stage]
        for (candidate in random.shuffled(clues.toList())) {
            if (clues.size <= floor) break
            val reduced = clues.filterNot { it === candidate }
            val payload = ColorNeighborsPayload(size, size, colorCount, reduced)
            val deduction = deduce(payload, initialStateFor(payload))
            if (!deduction.contradiction && deduction.domains.all(::isSingleton)) clues = reduced
        }
        val payload = ColorNeighborsPayload(size, size, colorCount, clues)
        val level = generatedLevel(request, payload)
        val result = solveDetails(level, limit = 2, budget = 200_000)
        require(!result.exceeded && result.solutions.size == 1) { "Unverified neighbor puzzle" }
        return GenerationResult(level, result.solutions.first(), ColorNeighborsGeneratorMetrics(size, size, colorCount, clues.size, result.nodes))
    }

    override fun solve(level: ColorNeighborsLevel): ColorNeighborsWitness? =
        solveDetails(level, limit = 1, budget = 200_000).solutions.firstOrNull()

    override fun hint(
        level: ColorNeighborsLevel,
        state: ColorNeighborsState,
        used: Int,
    ): PuzzleHint? {
        val inspection = inspect(level, state)
        if (inspection.contradiction) {
            val cell = inspection.conflictClueCells.firstOrNull()
            return PuzzleHint(
                "contradiction",
                JsonObject(buildMap { cell?.let { put("clueCell", JsonPrimitive(it)) } }),
            )
        }
        val cell = state.cells.indices.firstOrNull { state.cells[it] == null && isSingleton(inspection.domains[it]) }
            ?: return null
        val color = singletonColor(inspection.domains[cell])
        val cause = inspection.deductions.lastOrNull { it.cell == cell && it.color == color }
        return PuzzleHint(
            if (used == 0) "forced-color" else "exact-color",
            JsonObject(
                buildMap {
                    put("cell", JsonPrimitive(cell))
                    put("row", JsonPrimitive(cell / level.payload.cols))
                    put("column", JsonPrimitive(cell % level.payload.cols))
                    put("color", JsonPrimitive(color))
                    cause?.let {
                        put("clueCell", JsonPrimitive(it.clueCell))
                        put("rule", JsonPrimitive(it.rule))
                    }
                },
            ),
        )
    }

    fun inspect(
        level: ColorNeighborsLevel,
        state: ColorNeighborsState,
    ): ColorNeighborsInspection {
        val deduction = deduce(level.payload, state)
        return ColorNeighborsInspection(
            domains = deduction.domains,
            deductions = deduction.deductions,
            contradiction = deduction.contradiction,
            conflictClueCells = conflicts(level.payload, state),
        )
    }

    fun solveDetails(
        level: ColorNeighborsLevel,
        partial: ColorNeighborsState = initialState(level),
        limit: Int = 2,
        budget: Int = 200_000,
    ): ColorNeighborsSolveResult {
        if (!validateState(level, partial).valid || limit <= 0 || budget < 0) {
            return ColorNeighborsSolveResult(emptyList(), 0, false)
        }
        val solutions = mutableListOf<ColorNeighborsState>()
        var nodes = 0
        var exceeded = false
        fun visit(state: ColorNeighborsState) {
            if (solutions.size >= limit || exceeded) return
            if (nodes >= budget) {
                exceeded = true
                return
            }
            nodes += 1
            val deduction = deduce(level.payload, state)
            if (deduction.contradiction) return
            val cells = deduction.domains.map { mask -> if (isSingleton(mask)) singletonColor(mask) else null }
            val reduced = ColorNeighborsState(cells, deduction.domains.map { allColors(level.payload) and it.inv() })
            val nextCell = cells.indices.filter { cells[it] == null }.minByOrNull { bitCount(deduction.domains[it]) }
            if (nextCell == null) {
                val complete = reduced.copy(excluded = List(cells.size) { 0 })
                if (evaluate(level, complete).solved) solutions += complete
                return
            }
            for (color in 0 until level.payload.colorCount) {
                if (deduction.domains[nextCell] and (1 shl color) == 0) continue
                val branchCells = cells.toMutableList()
                val branchExcluded = reduced.excluded.toMutableList()
                branchCells[nextCell] = color
                branchExcluded[nextCell] = 0
                visit(ColorNeighborsState(branchCells, branchExcluded))
                if (solutions.size >= limit || exceeded) break
            }
        }
        visit(partial)
        return ColorNeighborsSolveResult(solutions, nodes, exceeded)
    }

    private fun generatedLevel(
        request: GenerationRequest,
        payload: ColorNeighborsPayload,
    ) = PuzzleLevel(
        identity =
            PuzzleIdentity(
                mechanicId = manifest.mechanicId,
                productGameId = manifest.mechanicId,
                levelPackId = "generated",
                levelId = "generated-${stableHash("v$COLOR_NEIGHBORS_GENERATOR_VERSION/${request.seed}/${payload.rows}x${payload.cols}/${payload.colorCount}").toString(16)}",
                schemaVersion = 1,
                rulesVersion = COLOR_NEIGHBORS_RULES_VERSION,
                contentVersion = 1,
                generatorVersion = COLOR_NEIGHBORS_GENERATOR_VERSION,
                seed = request.seed,
            ),
        difficulty = request.difficulty,
        payload = payload,
    )
}

fun neighbors(
    cell: Int,
    rows: Int,
    cols: Int,
): List<Int> = buildList {
    val row = cell / cols
    val column = cell % cols
    for (rowOffset in -1..1) for (columnOffset in -1..1) {
        if (rowOffset == 0 && columnOffset == 0) continue
        val neighborRow = row + rowOffset
        val neighborColumn = column + columnOffset
        if (neighborRow in 0 until rows && neighborColumn in 0 until cols) {
            add(neighborRow * cols + neighborColumn)
        }
    }
}

private data class DeductionResult(
    val domains: List<Int>,
    val deductions: List<ColorNeighborsDeduction>,
    val contradiction: Boolean,
)

private fun deduce(
    payload: ColorNeighborsPayload,
    state: ColorNeighborsState,
): DeductionResult {
    val allColors = allColors(payload)
    val domains = state.cells.mapIndexed { index, color ->
        color?.let { 1 shl it } ?: (allColors and state.excluded[index].inv())
    }.toMutableList()
    val deductions = mutableListOf<ColorNeighborsDeduction>()
    var changed = true
    var contradiction = domains.any { it == 0 }
    while (changed && !contradiction) {
        changed = false
        payload.clues.forEach clueLoop@{ clue ->
            val bit = 1 shl clue.color
            val neighborhood = neighbors(clue.cell, payload.rows, payload.cols)
            val fixed = neighborhood.count { domains[it] == bit }
            val possible = neighborhood.filter { domains[it] and bit != 0 }
            if (fixed > clue.count || possible.size < clue.count) {
                contradiction = true
                return@clueLoop
            }
            val requireColor = possible.size == clue.count
            val excludeColor = fixed == clue.count
            if (!requireColor && !excludeColor) return@clueLoop
            possible.forEach cellLoop@{ cell ->
                if (domains[cell] == bit) return@cellLoop
                val next = if (requireColor) bit else domains[cell] and bit.inv()
                if (next != domains[cell]) {
                    domains[cell] = next
                    deductions += ColorNeighborsDeduction(
                        cell = cell,
                        color = clue.color,
                        clueCell = clue.cell,
                        rule = if (requireColor) "remaining-required" else "count-satisfied",
                    )
                    changed = true
                    if (next == 0) contradiction = true
                }
            }
        }
    }
    return DeductionResult(domains, deductions, contradiction)
}

private fun conflicts(
    payload: ColorNeighborsPayload,
    state: ColorNeighborsState,
): List<Int> = payload.clues.mapNotNull { clue ->
    val bit = 1 shl clue.color
    var minimum = 0
    var maximum = 0
    neighbors(clue.cell, payload.rows, payload.cols).forEach { cell ->
        when {
            state.cells[cell] == clue.color -> {
                minimum += 1
                maximum += 1
            }
            state.cells[cell] == null && state.excluded[cell] and bit == 0 -> maximum += 1
        }
    }
    clue.cell.takeIf { minimum > clue.count || maximum < clue.count }
}

private fun initialStateFor(payload: ColorNeighborsPayload): ColorNeighborsState {
    val cells = MutableList<Int?>(payload.rows * payload.cols) { null }
    payload.clues.forEach { cells[it.cell] = it.color }
    return ColorNeighborsState(cells, List(cells.size) { 0 })
}

private fun accepted(
    state: ColorNeighborsState,
    cell: Int,
    color: Int?,
    excluded: Int,
): Transition<ColorNeighborsState> {
    val cells = state.cells.toMutableList()
    val exclusions = state.excluded.toMutableList()
    cells[cell] = color
    exclusions[cell] = excluded
    val next = ColorNeighborsState(cells, exclusions)
    return Transition(true, next != state, if (next == state) state else next)
}

private fun rejected(state: ColorNeighborsState, reason: String) = Transition(false, false, state, reason)

private fun allColors(payload: ColorNeighborsPayload) = (1 shl payload.colorCount) - 1

private fun isSingleton(mask: Int) = mask > 0 && mask and (mask - 1) == 0

private fun singletonColor(mask: Int): Int {
    var color = 0
    var value = mask
    while (value > 1) {
        value = value shr 1
        color += 1
    }
    return color
}

private fun bitCount(mask: Int): Int {
    var count = 0
    var value = mask
    while (value != 0) {
        count += value and 1
        value = value ushr 1
    }
    return count
}

private fun issue(code: String, path: String) = ValidationIssue(code, path)
