package dev.smallwins.puzzle.core.mechanics.route

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class RouteNode(
    val id: String,
    val x: Double,
    val y: Double,
    val reusable: Boolean = false,
)

@Serializable
data class RouteConnection(
    val firstNodeId: String,
    val secondNodeId: String,
    val bidirectional: Boolean = true,
)

@Serializable
data class RouteEndpoint(
    val id: String,
    val nodeId: String,
    val routeType: String? = null,
)

@Serializable
data class RouteWaypoint(
    val id: String,
    val nodeId: String,
    val order: Int? = null,
    val required: Boolean = true,
)

@Serializable
data class RoutePathRules(
    val maximumLength: Int? = null,
    val nodesMayRepeat: Boolean = false,
    val pathsMayCross: Boolean = false,
    val pathsMayShareNodes: Boolean = false,
    val targetRequiresAllRequiredWaypoints: Boolean = false,
)

@Serializable
data class RoutePayload(
    val nodes: List<RouteNode>,
    val connections: List<RouteConnection>,
    val starts: List<RouteEndpoint>,
    val targets: List<RouteEndpoint>,
    val waypoints: List<RouteWaypoint>,
    val forbiddenNodeIds: List<String> = emptyList(),
    val pathRules: RoutePathRules = RoutePathRules(),
)

typealias RouteLevel = PuzzleLevel<RoutePayload>

@Serializable
data class RoutePath(
    val startId: String,
    val targetId: String? = null,
    val nodeIds: List<String>,
)

@Serializable
data class RouteState(
    val paths: List<RoutePath> = emptyList(),
    val activeStartId: String? = null,
    val visitedWaypointIds: List<String> = emptyList(),
    val deadEndDetected: Boolean = false,
)

typealias RouteWitness = RouteState

@Serializable
data class RouteAction(
    val type: String,
    val startId: String? = null,
    val nodeId: String? = null,
)

@Serializable
data class RouteEvaluationMetrics(
    val completedPaths: Int,
    val requiredPaths: Int,
    val visitedRequiredWaypoints: Int,
    val requiredWaypoints: Int,
    val deadEndDetected: Boolean,
)

@Serializable
data class RouteGeneratorMetrics(
    val rows: Int,
    val cols: Int,
    val routeNodes: Int,
    val blockedNodes: Int,
)

@Serializable
data class RouteSolveResult(
    val solutions: List<RouteState>,
    val nodes: Int,
    val exceeded: Boolean,
)
