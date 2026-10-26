package dev.smallwins.puzzle.core.mechanics.placement

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementPluginTest {
    private val payload =
        PlacementPayload(
            size = 3,
            entities = listOf("ginger", "black", "white").map(::PlacementEntity),
            zones = List(3) { row -> PlacementZone("zone-$row", List(3) { column -> row * 3 + column }) },
            features = listOf(PlacementFeature("lamp", 1), PlacementFeature("bowl", 3), PlacementFeature("box", 7)),
            clues =
                listOf(
                    PlacementClue("inRow", "ginger", value = 0),
                    PlacementClue("inColumn", "ginger", value = 0),
                    PlacementClue("inRow", "black", value = 1),
                    PlacementClue("inColumn", "black", value = 1),
                    PlacementClue("inRow", "white", value = 2),
                    PlacementClue("inColumn", "white", value = 2),
                ),
        )
    private val level =
        PuzzleLevel(
            PuzzleIdentity("purrdoku", "purrdoku", "tests", "placement", 1, 1, 1),
            Difficulty(DifficultyBand.EASY, 0.25),
            payload,
        )

    @Test
    fun authoredLevelHasOneSolutionAndSemanticHints() {
        assertTrue(PlacementPlugin.validateLevel(level).valid)
        val result = PlacementPlugin.solveDetails(level)
        assertEquals(1, result.solutions.size)
        assertTrue(PlacementPlugin.evaluate(level, result.solutions.single()).solved)
        assertEquals("zone", PlacementPlugin.hint(level, PlacementPlugin.initialState(level), 0)?.kind)
        assertEquals("exact-cell", PlacementPlugin.hint(level, PlacementPlugin.initialState(level), 4)?.kind)
    }

    @Test
    fun placementSwapRemovalConflictsAndBlockedFeaturesArePortableRules() {
        val initial = PlacementPlugin.initialState(level)
        val blocked = PlacementPlugin.transition(level, initial, PlacementAction("place", "ginger", 1))
        assertFalse(blocked.accepted)
        assertEquals("feature-occupied", blocked.reason)

        val first = PlacementPlugin.transition(level, initial, PlacementAction("place", "ginger", 0)).state
        val second = PlacementPlugin.transition(level, first, PlacementAction("place", "black", 2)).state
        val swapped = PlacementPlugin.transition(level, second, PlacementAction("place", "ginger", 2)).state
        assertEquals(2, swapped.placements["ginger"])
        assertEquals(0, swapped.placements["black"])
        assertEquals(setOf("ginger", "black"), PlacementPlugin.inspect(level, swapped).conflicts.toSet())

        val removed = PlacementPlugin.transition(level, swapped, PlacementAction("remove", "ginger")).state
        assertNull(removed.placements["ginger"])
        assertEquals(0, removed.placements["black"])
        assertNull(initial.placements["ginger"])
    }

    @Test
    fun solverDistinguishesAmbiguityImpossibilityAndBudgetExhaustion() {
        assertEquals(2, PlacementPlugin.solveDetails(level.copy(payload = payload.copy(clues = emptyList()))).solutions.size)
        val impossible = level.copy(payload = payload.copy(clues = payload.clues + PlacementClue("inRow", "ginger", value = 2)))
        assertTrue(PlacementPlugin.solveDetails(impossible).solutions.isEmpty())
        assertTrue(PlacementPlugin.solveDetails(level, budget = 1).exceeded)
    }

    @Test
    fun seededGenerationIsStableValidAndUnique() {
        val request = GenerationRequest("placement-seed", 1, Difficulty(DifficultyBand.MEDIUM, 0.5))
        val first = PlacementPlugin.generate(request)
        val second = PlacementPlugin.generate(request)
        assertEquals(first, second)
        assertTrue(PlacementPlugin.validateLevel(first.level).valid)
        assertEquals(1, PlacementPlugin.solveDetails(first.level).solutions.size)
    }
}
