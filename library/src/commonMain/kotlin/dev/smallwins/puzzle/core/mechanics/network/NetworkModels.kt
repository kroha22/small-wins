package dev.smallwins.puzzle.core.mechanics.network

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class NetworkPayload(
    val rows: Int,
    val cols: Int,
    val sourceCell: Int,
    val ports: List<List<Int>>,
    val initialRotations: List<Int>,
    val targetCell: Int? = null,
)

typealias NetworkLevel = PuzzleLevel<NetworkPayload>

@Serializable
data class NetworkState(
    val rotations: List<Int>,
)

typealias NetworkWitness = NetworkState

@Serializable
data class NetworkAction(
    val type: String,
    val cell: Int,
    val quarterTurns: Int,
)

@Serializable
data class NetworkEvaluationMetrics(
    val reachedCells: Int,
    val totalCells: Int,
    val leaks: Int,
)

@Serializable
data class NetworkGeneratorMetrics(
    val rows: Int,
    val cols: Int,
    val boardCells: Int,
    val deadEnds: Int,
    val branches: Int,
)

@Serializable
data class NetworkLeak(
    val cell: Int,
    val direction: Int,
)

@Serializable
data class NetworkConnections(
    val reached: List<Int>,
    val leaks: List<NetworkLeak>,
)
