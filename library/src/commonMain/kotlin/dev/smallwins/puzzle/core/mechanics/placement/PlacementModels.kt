package dev.smallwins.puzzle.core.mechanics.placement

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class PlacementEntity(val id: String)

@Serializable
data class PlacementZone(
    val id: String,
    val cells: List<Int>,
)

@Serializable
data class PlacementFeature(
    val id: String,
    val cell: Int,
)

@Serializable
data class PlacementClue(
    val type: String,
    val entity: String,
    val value: Int? = null,
    val other: String? = null,
    val zone: String? = null,
    val feature: String? = null,
)

@Serializable
data class PlacementPayload(
    val size: Int,
    val entities: List<PlacementEntity>,
    val zones: List<PlacementZone>,
    val features: List<PlacementFeature>,
    val clues: List<PlacementClue>,
)

typealias PlacementLevel = PuzzleLevel<PlacementPayload>

@Serializable
data class PlacementState(val placements: Map<String, Int?>)

typealias PlacementWitness = PlacementState

@Serializable
data class PlacementAction(
    val type: String,
    val entity: String,
    val cell: Int? = null,
)

@Serializable
data class PlacementEvaluationMetrics(
    val placed: Int,
    val entities: Int,
    val conflicts: List<String>,
    val satisfiedClues: Int,
)

@Serializable
data class PlacementGeneratorMetrics(
    val size: Int,
    val clueCount: Int,
    val solverNodes: Int,
)

@Serializable
data class PlacementInspection(
    val clueStatuses: List<String>,
    val conflicts: List<String>,
)

@Serializable
data class PlacementSolveResult(
    val solutions: List<PlacementState>,
    val nodes: Int,
    val exceeded: Boolean,
)
