package dev.smallwins.puzzle.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class DifficultyBand {
    @SerialName("tutorial")
    TUTORIAL,

    @SerialName("easy")
    EASY,

    @SerialName("medium")
    MEDIUM,

    @SerialName("hard")
    HARD,

    @SerialName("expert")
    EXPERT,
}

@Serializable
data class Difficulty(
    val band: DifficultyBand,
    val normalized: Double,
    val parameters: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class PuzzleIdentity(
    val mechanicId: String,
    val productGameId: String,
    val levelPackId: String,
    val levelId: String,
    val schemaVersion: Int,
    val rulesVersion: Int,
    val contentVersion: Int,
    val generatorVersion: Int? = null,
    val seed: String? = null,
)

@Serializable
data class PuzzleLevel<Payload>(
    val identity: PuzzleIdentity,
    val difficulty: Difficulty,
    val payload: Payload,
)

@Serializable
data class GenerationRequest(
    val seed: String,
    val generatorVersion: Int,
    val difficulty: Difficulty,
    val variant: String? = null,
    val parameters: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GenerationResult<Level, Witness, Metrics>(
    val level: Level,
    val solutionWitness: Witness,
    val metrics: Metrics,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ValidationIssue(
    val code: String,
    val path: String,
    val details: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val issues: List<ValidationIssue> = emptyList(),
)

@Serializable
data class EngineEvent(
    val type: String,
    val data: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Transition<State>(
    val accepted: Boolean,
    val changed: Boolean,
    val state: State,
    val reason: String? = null,
    val events: List<EngineEvent> = emptyList(),
)

@Serializable
data class Evaluation<Metrics>(
    val solved: Boolean,
    val progress: Double,
    val metrics: Metrics,
)

@Serializable
data class PuzzleHint(
    val kind: String,
    val data: JsonObject,
)

@Serializable
enum class ActivityCapability {
    @SerialName("deterministic-generation")
    DETERMINISTIC_GENERATION,

    @SerialName("difficulty")
    DIFFICULTY,

    @SerialName("solver")
    SOLVER,

    @SerialName("hints")
    HINTS,

    @SerialName("serializable-state")
    SERIALIZABLE_STATE,

    @SerialName("solution-witness")
    SOLUTION_WITNESS,
}

@Serializable
data class ActivityManifest(
    val mechanicId: String,
    val rulesVersion: Int,
    val aliases: List<String>,
    val capabilities: List<ActivityCapability>,
)

interface ActivityPlugin<Level, State, Action, Witness, EvaluationMetrics, GeneratorMetrics> {
    val manifest: ActivityManifest

    fun validateLevel(level: Level): ValidationResult

    fun initialState(level: Level): State

    fun validateState(level: Level, state: State): ValidationResult

    fun transition(level: Level, state: State, action: Action): Transition<State>

    fun evaluate(level: Level, state: State): Evaluation<EvaluationMetrics>

    fun generate(request: GenerationRequest): GenerationResult<Level, Witness, GeneratorMetrics>

    fun solve(level: Level): Witness?

    fun hint(level: Level, state: State, used: Int): PuzzleHint? = null
}

@Serializable
data class PuzzleSession<State>(
    val identity: PuzzleIdentity,
    val state: State,
    val moveCount: Int,
    val hintCount: Int,
    val updatedAt: String,
)
