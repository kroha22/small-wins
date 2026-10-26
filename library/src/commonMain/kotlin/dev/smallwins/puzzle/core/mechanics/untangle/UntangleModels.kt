package dev.smallwins.puzzle.core.mechanics.untangle

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class UntanglePoint(val x: Double, val y: Double)

@Serializable
data class UntangleBounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

@Serializable
data class UntangleNode(
    val id: String,
    val x: Double,
    val y: Double,
    val fixed: Boolean = false,
    val movementBounds: UntangleBounds? = null,
    val groupId: String? = null,
)

@Serializable
data class UntangleEdge(
    val id: String,
    val firstNodeId: String,
    val secondNodeId: String,
    val groupId: String? = null,
    val intersectionsAllowed: Boolean = false,
)

@Serializable
data class UntanglePlacementZone(
    val id: String,
    val bounds: UntangleBounds,
    val allowedNodeIds: List<String>,
)

@Serializable
data class UntanglePayload(
    val nodes: List<UntangleNode>,
    val edges: List<UntangleEdge>,
    val fieldBounds: UntangleBounds = UntangleBounds(8.0, 8.0, 92.0, 92.0),
    val minimumNodeDistance: Double = 12.0,
    val placementZones: List<UntanglePlacementZone> = emptyList(),
    val forbiddenZones: List<UntangleBounds> = emptyList(),
    val intersectionsAllowedWithinGroups: List<String> = emptyList(),
    val epsilon: Double = 0.000001,
    val coordinateScale: Int = 10,
)

typealias UntangleLevel = PuzzleLevel<UntanglePayload>

@Serializable
data class UntangleState(val positions: Map<String, UntanglePoint>)

typealias UntangleWitness = UntangleState

@Serializable
data class UntangleAction(val type: String, val nodeId: String, val x: Double, val y: Double)

@Serializable
data class UntangleConflict(val firstEdgeId: String, val secondEdgeId: String? = null, val nodeId: String? = null)

@Serializable
data class UntangleEvaluationMetrics(
    val conflictCount: Int,
    val conflictEdgeIds: List<String>,
    val conflicts: List<UntangleConflict>,
    val placementsValid: Boolean,
)

@Serializable
data class UntangleGeneratorMetrics(val nodeCount: Int, val edgeCount: Int, val initialConflicts: Int)
