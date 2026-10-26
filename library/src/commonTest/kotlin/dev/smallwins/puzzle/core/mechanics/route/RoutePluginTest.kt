package dev.smallwins.puzzle.core.mechanics.route

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutePluginTest {
    private val payload =
        RoutePayload(
            nodes =
                listOf(
                    node("start", 0.0, 1.0), node("top-left", 0.0, 0.0), node("first", 1.0, 0.0),
                    node("top-right", 2.0, 0.0), node("blocked", 1.0, 1.0), node("target", 2.0, 1.0),
                    node("bottom-left", 0.0, 2.0), node("second", 1.0, 2.0), node("bottom-right", 2.0, 2.0),
                ),
            connections =
                listOf(
                    edge("start", "top-left"), edge("top-left", "first"), edge("first", "top-right"), edge("top-right", "target"),
                    edge("first", "second"),
                    edge("start", "blocked"), edge("blocked", "target"), edge("start", "bottom-left"),
                    edge("bottom-left", "second"), edge("second", "bottom-right"), edge("bottom-right", "target"),
                ),
            starts = listOf(RouteEndpoint("start-route", "start", "pip")),
            targets = listOf(RouteEndpoint("target-route", "target", "pip")),
            waypoints =
                listOf(
                    RouteWaypoint("tool-1", "first", order = 1),
                    RouteWaypoint("tool-2", "second", order = 2),
                ),
            forbiddenNodeIds = listOf("blocked"),
            pathRules = RoutePathRules(maximumLength = 8),
        )
    private val level =
        PuzzleLevel(
            PuzzleIdentity("route", "route", "tests", "graph-route", 1, 1, 1),
            Difficulty(DifficultyBand.MEDIUM, 0.5),
            payload,
        )

    @Test
    fun graphRouteSupportsOrderedWaypointsDeadEndsAndTrim() {
        var state = RoutePlugin.transition(level, RoutePlugin.initialState(level), RouteAction("start", startId = "start-route")).state
        val wrongOrder = RoutePlugin.transition(level, state, RouteAction("extend", nodeId = "bottom-left"))
        assertTrue(wrongOrder.accepted)
        val outOfOrder = RoutePlugin.transition(level, wrongOrder.state, RouteAction("extend", nodeId = "second"))
        assertFalse(outOfOrder.accepted)
        assertEquals("waypoint-order", outOfOrder.reason)

        listOf("top-left", "first", "top-right", "target").forEach { nodeId ->
            state = RoutePlugin.transition(level, state, RouteAction("extend", nodeId = nodeId)).state
        }
        assertFalse(RoutePlugin.evaluate(level, state).solved)
        assertTrue(state.deadEndDetected)
        val trimmed = RoutePlugin.transition(level, state, RouteAction("trim", nodeId = "start"))
        assertTrue(trimmed.accepted)
        assertFalse(trimmed.state.deadEndDetected)
        assertEquals(listOf("start"), trimmed.state.paths.single().nodeIds)
    }

    @Test
    fun publicProfileRejectsEarlyTargetAndSolverReturnsACompleteSimplePath() {
        val profile = level.copy(payload = payload.copy(pathRules = payload.pathRules.copy(targetRequiresAllRequiredWaypoints = true)))
        var state = RoutePlugin.transition(profile, RoutePlugin.initialState(profile), RouteAction("start", startId = "start-route")).state
        listOf("top-left", "first", "top-right").forEach { state = RoutePlugin.transition(profile, state, RouteAction("extend", nodeId = it)).state }
        assertEquals("missing-waypoints", RoutePlugin.transition(profile, state, RouteAction("extend", nodeId = "target")).reason)
        val solution = RoutePlugin.solveDetails(profile, limit = 1).solutions.single()
        assertTrue(RoutePlugin.evaluate(profile, solution).solved)
        assertEquals(listOf("tool-1", "tool-2"), solution.visitedWaypointIds)
    }

    @Test
    fun directedConnectionsRouteTypesAndMaximumLengthAreEnforced() {
        val directed =
            RoutePayload(
                nodes = listOf(node("a", 0.0, 0.0), node("b", 1.0, 0.0), node("c", 2.0, 0.0)),
                connections = listOf(RouteConnection("a", "b", false), RouteConnection("b", "c", false)),
                starts = listOf(RouteEndpoint("red-start", "a", "red")),
                targets = listOf(RouteEndpoint("blue-target", "c", "blue")),
                waypoints = emptyList(),
                pathRules = RoutePathRules(maximumLength = 1),
            )
        val directedLevel = level.copy(payload = directed)
        var state = RoutePlugin.transition(directedLevel, RoutePlugin.initialState(directedLevel), RouteAction("start", startId = "red-start")).state
        state = RoutePlugin.transition(directedLevel, state, RouteAction("extend", nodeId = "b")).state
        assertEquals("maximum-length", RoutePlugin.transition(directedLevel, state, RouteAction("extend", nodeId = "c")).reason)
        val reverse = state.copy(paths = listOf(RoutePath("red-start", null, listOf("a", "b", "a"))))
        assertFalse(RoutePlugin.validateState(directedLevel, reverse).valid)

        val routeTypeLevel = directedLevel.copy(payload = directed.copy(pathRules = RoutePathRules()))
        var routeTypeState =
            RoutePlugin.transition(
                routeTypeLevel,
                RoutePlugin.initialState(routeTypeLevel),
                RouteAction("start", startId = "red-start"),
            ).state
        routeTypeState = RoutePlugin.transition(routeTypeLevel, routeTypeState, RouteAction("extend", nodeId = "b")).state
        assertEquals("route-type", RoutePlugin.transition(routeTypeLevel, routeTypeState, RouteAction("extend", nodeId = "c")).reason)
    }

    @Test
    fun independentPathsCannotCrossWhenCrossingIsDisabled() {
        val crossing =
            RoutePayload(
                nodes =
                    listOf(
                        node("left", -1.0, 0.0),
                        node("right", 1.0, 0.0),
                        node("top", 0.0, -1.0),
                        node("bottom", 0.0, 1.0),
                    ),
                connections = listOf(edge("left", "right"), edge("top", "bottom")),
                starts = listOf(RouteEndpoint("horizontal", "left"), RouteEndpoint("vertical", "top")),
                targets = listOf(RouteEndpoint("horizontal-target", "right"), RouteEndpoint("vertical-target", "bottom")),
                waypoints = emptyList(),
                pathRules = RoutePathRules(pathsMayCross = false),
            )
        val crossingLevel = level.copy(payload = crossing)
        var state = RoutePlugin.transition(crossingLevel, RoutePlugin.initialState(crossingLevel), RouteAction("start", startId = "horizontal")).state
        state = RoutePlugin.transition(crossingLevel, state, RouteAction("extend", nodeId = "right")).state
        state = RoutePlugin.transition(crossingLevel, state, RouteAction("start", startId = "vertical")).state

        val rejected = RoutePlugin.transition(crossingLevel, state, RouteAction("extend", nodeId = "bottom"))
        assertFalse(rejected.accepted)
        assertEquals("paths-cross", rejected.reason)
    }

    @Test
    fun generatedRouteIsStableValidAndSolvedByItsWitness() {
        val request = GenerationRequest("route-seed", 1, Difficulty(DifficultyBand.MEDIUM, 0.5))
        val generated = RoutePlugin.generate(request)
        assertEquals(generated, RoutePlugin.generate(request))
        assertTrue(RoutePlugin.validateLevel(generated.level).valid)
        assertTrue(RoutePlugin.evaluate(generated.level, generated.solutionWitness).solved)
    }

    private fun node(id: String, x: Double, y: Double) = RouteNode(id, x, y)
    private fun edge(first: String, second: String) = RouteConnection(first, second)
}
