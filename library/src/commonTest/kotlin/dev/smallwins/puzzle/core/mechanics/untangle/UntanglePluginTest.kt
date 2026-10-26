package dev.smallwins.puzzle.core.mechanics.untangle

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UntanglePluginTest {
    private val payload =
        UntanglePayload(
            nodes =
                listOf(
                    UntangleNode("a", 20.0, 20.0),
                    UntangleNode("b", 80.0, 80.0),
                    UntangleNode("c", 80.0, 20.0),
                    UntangleNode("d", 20.0, 80.0),
                ),
            edges =
                listOf(
                    UntangleEdge("ab", "a", "b"),
                    UntangleEdge("bc", "b", "c"),
                    UntangleEdge("cd", "c", "d"),
                    UntangleEdge("da", "d", "a"),
                ),
        )
    private val level =
        PuzzleLevel(
            PuzzleIdentity("untangle", "untangle", "tests", "cross", 1, 1, 1),
            Difficulty(DifficultyBand.EASY, 0.25),
            payload,
        )

    @Test
    fun detectsCrossingsCollinearOverlapAndForeignNodesOnEdges() {
        assertFalse(UntanglePlugin.evaluate(level, UntanglePlugin.initialState(level)).solved)
        val collinear =
            level.copy(
                payload =
                    payload.copy(
                        edges = listOf(UntangleEdge("ab", "a", "b")),
                    ),
            )
        val state =
            UntangleState(
                mapOf(
                    "a" to UntanglePoint(20.0, 20.0),
                    "b" to UntanglePoint(80.0, 20.0),
                    "c" to UntanglePoint(50.0, 20.0),
                    "d" to UntanglePoint(20.0, 80.0),
                ),
            )
        val metrics = UntanglePlugin.evaluate(collinear, state).metrics
        assertEquals(1, metrics.conflictCount)
        assertEquals("c", metrics.conflicts.single().nodeId)
    }

    @Test
    fun enforcesFixedNodesDistancesAndProductZones() {
        val constrained =
            level.copy(
                payload =
                    payload.copy(
                        nodes = payload.nodes.map { if (it.id == "a") it.copy(fixed = true) else it },
                        forbiddenZones = listOf(UntangleBounds(40.0, 40.0, 60.0, 60.0)),
                        placementZones = listOf(UntanglePlacementZone("right", UntangleBounds(60.0, 8.0, 92.0, 92.0), listOf("b"))),
                    ),
            )
        val state = UntanglePlugin.initialState(constrained)
        assertEquals("fixed-node", UntanglePlugin.transition(constrained, state, UntangleAction("moveNode", "a", 30.0, 30.0)).reason)
        val distanceOnly = level.copy(payload = payload.copy(nodes = constrained.payload.nodes))
        assertEquals(
            "nodes-too-close",
            UntanglePlugin.transition(distanceOnly, UntanglePlugin.initialState(distanceOnly), UntangleAction("moveNode", "b", 20.0, 21.0)).reason,
        )
        assertEquals("forbidden-zone", UntanglePlugin.transition(constrained, state, UntangleAction("moveNode", "b", 50.0, 50.0)).reason)
        assertEquals("placement-zone", UntanglePlugin.transition(constrained, state, UntangleAction("moveNode", "b", 30.0, 70.0)).reason)
    }

    @Test
    fun allowedEdgeGroupsDoNotCountAsConflicts() {
        val grouped =
            level.copy(
                payload =
                    payload.copy(
                        edges =
                            listOf(
                                UntangleEdge("ab", "a", "b", groupId = "wires"),
                                UntangleEdge("cd", "c", "d", groupId = "wires"),
                            ),
                        intersectionsAllowedWithinGroups = listOf("wires"),
                    ),
            )
        assertTrue(UntanglePlugin.evaluate(grouped, UntanglePlugin.initialState(grouped)).solved)
    }

    @Test
    fun generatedPuzzleIsStableAndIncludesASolvedWitness() {
        val request = GenerationRequest("untangle-seed", 1, Difficulty(DifficultyBand.HARD, 0.75))
        val generated = UntanglePlugin.generate(request)
        assertEquals(generated, UntanglePlugin.generate(request))
        assertTrue(UntanglePlugin.validateLevel(generated.level).valid)
        assertFalse(UntanglePlugin.evaluate(generated.level, UntanglePlugin.initialState(generated.level)).solved)
        assertTrue(UntanglePlugin.evaluate(generated.level, generated.solutionWitness).solved)
    }
}
