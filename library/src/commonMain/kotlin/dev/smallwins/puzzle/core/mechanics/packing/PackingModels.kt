package dev.smallwins.puzzle.core.mechanics.packing

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class PackingPoint(val x: Int, val y: Int)

@Serializable
data class PackingPiece(val id: String, val cells: List<PackingPoint>)

@Serializable
data class PackingPayload(val size: Int, val pieces: List<PackingPiece>)

typealias PackingLevel = PuzzleLevel<PackingPayload>

@Serializable
data class PackingState(
    val rotations: List<Int>,
    val origins: List<Int?>,
)

typealias PackingWitness = PackingState

@Serializable
data class PackingAction(
    val type: String,
    val piece: Int,
    val origin: Int? = null,
)

@Serializable
data class PackingEvaluationMetrics(
    val placedPieces: Int,
    val totalPieces: Int,
    val occupiedCells: Int,
    val totalCells: Int,
)

@Serializable
data class PackingGeneratorMetrics(
    val size: Int,
    val pieceCount: Int,
    val bentPieces: Int,
)
