package dev.smallwins.puzzle.core.mechanics.shikaku

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class ShikakuClue(
    val cell: Int,
    val area: Int,
)

@Serializable
data class ShikakuPayload(
    val rows: Int,
    val cols: Int,
    val clues: List<ShikakuClue>,
    val initialRegions: List<Int>,
)

typealias ShikakuLevel = PuzzleLevel<ShikakuPayload>

@Serializable
data class ShikakuState(val regions: List<Int>)

typealias ShikakuWitness = ShikakuState

@Serializable
data class ShikakuAction(
    val type: String,
    val clue: Int,
    val top: Int? = null,
    val left: Int? = null,
    val bottom: Int? = null,
    val right: Int? = null,
)

@Serializable
data class ShikakuEvaluationMetrics(
    val covered: Int,
    val total: Int,
)

@Serializable
data class ShikakuGeneratorMetrics(
    val rows: Int,
    val cols: Int,
    val regions: Int,
    val attempts: Int,
)

@Serializable
data class ShikakuSolveResult(
    val solutions: List<ShikakuState>,
    val nodes: Int,
    val exceeded: Boolean,
)
