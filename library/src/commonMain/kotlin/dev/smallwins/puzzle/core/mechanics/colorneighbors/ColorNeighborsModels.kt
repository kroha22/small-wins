package dev.smallwins.puzzle.core.mechanics.colorneighbors

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class ColorNeighborClue(
    val cell: Int,
    val color: Int,
    val count: Int,
)

@Serializable
data class ColorNeighborsPayload(
    val rows: Int,
    val cols: Int,
    val colorCount: Int,
    val clues: List<ColorNeighborClue>,
)

typealias ColorNeighborsLevel = PuzzleLevel<ColorNeighborsPayload>

@Serializable
data class ColorNeighborsState(
    val cells: List<Int?>,
    val excluded: List<Int>,
)

typealias ColorNeighborsWitness = ColorNeighborsState

@Serializable
data class ColorNeighborsAction(
    val type: String,
    val cell: Int,
    val color: Int? = null,
)

@Serializable
data class ColorNeighborsEvaluationMetrics(
    val filled: Int,
    val total: Int,
    val conflictClueCells: List<Int>,
)

@Serializable
data class ColorNeighborsGeneratorMetrics(
    val rows: Int,
    val cols: Int,
    val colorCount: Int,
    val clueCount: Int,
    val solverNodes: Int,
)

@Serializable
data class ColorNeighborsDeduction(
    val cell: Int,
    val color: Int,
    val clueCell: Int,
    val rule: String,
)

@Serializable
data class ColorNeighborsInspection(
    val domains: List<Int>,
    val deductions: List<ColorNeighborsDeduction>,
    val contradiction: Boolean,
    val conflictClueCells: List<Int>,
)

@Serializable
data class ColorNeighborsSolveResult(
    val solutions: List<ColorNeighborsState>,
    val nodes: Int,
    val exceeded: Boolean,
)
