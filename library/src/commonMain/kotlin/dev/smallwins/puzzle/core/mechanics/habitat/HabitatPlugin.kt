package dev.smallwins.puzzle.core.mechanics.habitat

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

const val HABITAT_RULES_VERSION = 1
const val HABITAT_GENERATOR_VERSION = 1

object HabitatPlugin :
    ActivityPlugin<
        HabitatLevel,
        HabitatState,
        HabitatAction,
        HabitatWitness,
        HabitatEvaluationMetrics,
        HabitatGeneratorMetrics,
    > {
    override val manifest =
        ActivityManifest(
            mechanicId = "habitat-search",
            rulesVersion = HABITAT_RULES_VERSION,
            aliases = listOf("wildlife-survey"),
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

    override fun validateLevel(level: HabitatLevel): ValidationResult {
        val p = level.payload
        val issues = mutableListOf<ValidationIssue>()
        if (level.identity.mechanicId != manifest.mechanicId) issues += issue("identity.mechanic", "$.identity.mechanicId")
        if (level.identity.rulesVersion != HABITAT_RULES_VERSION) issues += issue("identity.rules-version", "$.identity.rulesVersion")
        if (p.rows !in 2..20 || p.cols !in 2..20) issues += issue("grid.invalid", "$.payload")
        val cells = p.rows * p.cols
        if (p.theme.isBlank()) issues += issue("theme.empty", "$.payload.theme")
        if (
            p.rowCounts.size != p.rows || p.columnCounts.size != p.cols ||
            p.rowCounts.any { it !in 0..p.cols } || p.columnCounts.any { it !in 0..p.rows }
        ) issues += issue("counts.invalid", "$.payload")
        if (
            p.groups.isEmpty() || p.groups.map { it.id }.distinct().size != p.groups.size ||
            p.groups.any { it.id.isBlank() || it.species.isBlank() || it.length !in 1..cells }
        ) issues += issue("groups.invalid", "$.payload.groups")
        if (p.occupied.any { it !in 0 until cells } || p.occupied.distinct().size != p.occupied.size) {
            issues += issue("occupied.invalid", "$.payload.occupied")
        }
        if (p.groups.sumOf { it.length } != p.occupied.size) issues += issue("groups.total", "$.payload.groups")
        if (p.rowCounts.sum() != p.occupied.size || p.columnCounts.sum() != p.occupied.size) {
            issues += issue("counts.total", "$.payload")
        }
        if (p.rowCounts.indices.any { row -> p.rowCounts[row] != p.occupied.count { it / p.cols == row } }) {
            issues += issue("counts.rows", "$.payload.rowCounts")
        }
        if (p.columnCounts.indices.any { column -> p.columnCounts[column] != p.occupied.count { it % p.cols == column } }) {
            issues += issue("counts.columns", "$.payload.columnCounts")
        }
        return ValidationResult(issues.isEmpty(), issues.distinct())
    }

    override fun initialState(level: HabitatLevel) = HabitatState(List(level.payload.rows * level.payload.cols) { HabitatMark.UNKNOWN })

    override fun validateState(level: HabitatLevel, state: HabitatState): ValidationResult =
        if (state.marks.size == level.payload.rows * level.payload.cols) {
            ValidationResult(true)
        } else {
            ValidationResult(false, listOf(issue("marks.invalid", "$.marks")))
        }

    override fun transition(level: HabitatLevel, state: HabitatState, action: HabitatAction): Transition<HabitatState> {
        val cells = level.payload.rows * level.payload.cols
        if (action.type != "mark") return Transition(false, false, state, "unknown-action")
        if (action.cell !in 0 until cells) return Transition(false, false, state, "cell-out-of-bounds")
        val nextMark = if (state.marks[action.cell] == action.mark) HabitatMark.UNKNOWN else action.mark
        if (nextMark == state.marks[action.cell]) return Transition(true, false, state)
        val marks = state.marks.toMutableList()
        marks[action.cell] = nextMark
        return Transition(
            true,
            true,
            HabitatState(marks),
            events = listOf(EngineEvent("cell-marked", JsonObject(mapOf("cell" to JsonPrimitive(action.cell))))),
        )
    }

    override fun evaluate(level: HabitatLevel, state: HabitatState): Evaluation<HabitatEvaluationMetrics> {
        val occupied = level.payload.occupied
        val found = occupied.count { state.marks[it] == HabitatMark.FOUND }
        val observed = state.marks.count { it != HabitatMark.UNKNOWN }
        val solved = found == occupied.size
        return Evaluation(
            solved,
            if (occupied.isEmpty()) 1.0 else found.toDouble() / occupied.size,
            HabitatEvaluationMetrics(found, occupied.size, observed),
        )
    }

    override fun generate(request: GenerationRequest): GenerationResult<HabitatLevel, HabitatWitness, HabitatGeneratorMetrics> {
        require(request.generatorVersion == HABITAT_GENERATOR_VERSION)
        val defaultSize = when (request.difficulty.band) {
            DifficultyBand.TUTORIAL, DifficultyBand.EASY -> 5
            DifficultyBand.MEDIUM -> 6
            DifficultyBand.HARD, DifficultyBand.EXPERT -> 7
        }
        val size = request.parameters["size"].let { (it as? JsonPrimitive)?.intOrNull ?: defaultSize }.coerceIn(4, 10)
        val groupCount = if (request.difficulty.band in setOf(DifficultyBand.HARD, DifficultyBand.EXPERT)) 3 else 2
        val random = StableRandom("habitat/v$HABITAT_GENERATOR_VERSION/${request.seed}/$size/$groupCount")
        val rows = random.shuffled((0 until size).toList()).take(groupCount).sorted()
        val lengths = List(groupCount) { index -> (2 + (index + random.nextInt(2)) % 3).coerceAtMost(size) }
        val groups = lengths.mapIndexed { index, length -> HabitatGroup("group-${index + 1}", length, "species-${index + 1}") }
        val occupied = buildList {
            groups.forEachIndexed { index, group ->
                val start = random.nextInt(size - group.length + 1)
                repeat(group.length) { offset -> add(rows[index] * size + start + offset) }
            }
        }
        val payload =
            HabitatPayload(
                rows = size,
                cols = size,
                theme = request.variant ?: "neutral",
                rowCounts = List(size) { row -> occupied.count { it / size == row } },
                columnCounts = List(size) { column -> occupied.count { it % size == column } },
                groups = groups,
                occupied = occupied,
            )
        val level =
            PuzzleLevel(
                PuzzleIdentity(
                    manifest.mechanicId,
                    manifest.mechanicId,
                    "generated",
                    "generated-${stableHash("v$HABITAT_GENERATOR_VERSION/${request.seed}/$size/$groupCount").toString(16)}",
                    1,
                    HABITAT_RULES_VERSION,
                    1,
                    HABITAT_GENERATOR_VERSION,
                    request.seed,
                ),
                request.difficulty,
                payload,
            )
        val witness = solvedState(payload)
        return GenerationResult(level, witness, HabitatGeneratorMetrics(size, size, groups.size, occupied.size))
    }

    override fun solve(level: HabitatLevel): HabitatWitness = solvedState(level.payload)

    override fun hint(level: HabitatLevel, state: HabitatState, used: Int): PuzzleHint? {
        val p = level.payload
        val cell = p.occupied.firstOrNull { state.marks[it] != HabitatMark.FOUND }
            ?: state.marks.indices.firstOrNull { it !in p.occupied && state.marks[it] != HabitatMark.EMPTY }
            ?: return null
        val found = cell in p.occupied
        return PuzzleHint(
            if (used == 0) "scan-row" else "exact-cell",
            JsonObject(
                mapOf(
                    "cell" to JsonPrimitive(cell),
                    "row" to JsonPrimitive(cell / p.cols),
                    "column" to JsonPrimitive(cell % p.cols),
                    "mark" to JsonPrimitive(if (found) "found" else "empty"),
                ),
            ),
        )
    }
}

private fun solvedState(payload: HabitatPayload) =
    HabitatState(List(payload.rows * payload.cols) { if (it in payload.occupied) HabitatMark.FOUND else HabitatMark.EMPTY })

private fun issue(code: String, path: String) = ValidationIssue(code, path)
