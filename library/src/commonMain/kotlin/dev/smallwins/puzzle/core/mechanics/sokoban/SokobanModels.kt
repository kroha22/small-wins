package dev.smallwins.puzzle.core.mechanics.sokoban

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class SokobanPayload(
    val size: Int,
    val player: Int,
    val boxes: List<Int>,
    val goals: List<Int>,
    val innerWalls: List<Int>,
)

typealias SokobanLevel = PuzzleLevel<SokobanPayload>

@Serializable
data class SokobanState(val player: Int, val boxes: List<Int>)

@Serializable
data class SokobanAction(val type: String, val direction: String)

@Serializable
data class SokobanWitness(
    val moves: List<String>,
    val minimumPushes: Int,
)

@Serializable
data class SokobanEvaluationMetrics(
    val boxesOnGoals: Int,
    val totalBoxes: Int,
)

@Serializable
data class SokobanGeneratorMetrics(
    val size: Int,
    val boxes: Int,
    val constructionPulls: Int,
    val minimumPushes: Int,
    val obstacleImpact: Int,
)

@Serializable
data class SokobanAnalysis(
    val status: String,
    val minimumPushes: Int? = null,
    val expanded: Int,
    val choices: Int,
    val moves: List<String> = emptyList(),
)
