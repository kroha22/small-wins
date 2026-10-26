package dev.smallwins.puzzle.core.mechanics.shikaku

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShikakuPluginTest {
    private val payload =
        ShikakuPayload(
            rows = 2,
            cols = 3,
            clues = listOf(ShikakuClue(0, 2), ShikakuClue(2, 2), ShikakuClue(4, 2)),
            initialRegions = List(6) { -1 },
        )
    private val level =
        PuzzleLevel(
            PuzzleIdentity("shikaku", "shikaku", "tests", "rectangles", 1, 1, 1),
            Difficulty(DifficultyBand.EASY, 0.25),
            payload,
        )

    @Test
    fun transitionRejectsWrongAreaMissingClueAndOverlapWithoutMutation() {
        val initial = ShikakuPlugin.initialState(level)
        assertEquals("area-mismatch", ShikakuPlugin.transition(level, initial, ShikakuAction("place", 0, 0, 0, 1, 1)).reason)
        assertEquals("missing-clue", ShikakuPlugin.transition(level, initial, ShikakuAction("place", 0, 0, 1, 1, 1)).reason)
        val first = ShikakuPlugin.transition(level, initial, ShikakuAction("place", 0, 0, 0, 0, 1)).state
        val overlap = ShikakuPlugin.transition(level, first, ShikakuAction("place", 1, 0, 1, 0, 2))
        assertFalse(overlap.accepted)
        assertEquals("overlap", overlap.reason)
        val cleared = ShikakuPlugin.transition(level, first, ShikakuAction("clear", 0))
        assertTrue(cleared.accepted)
        assertEquals(initial, cleared.state)
        assertEquals(List(6) { -1 }, initial.regions)
    }

    @Test
    fun solverAndHintsUseExactRectangleSemantics() {
        val result = ShikakuPlugin.solveDetails(level)
        assertEquals(2, result.solutions.size)
        assertEquals("area", ShikakuPlugin.hint(level, ShikakuPlugin.initialState(level), 0)?.kind)
        assertEquals("rectangle", ShikakuPlugin.hint(level, ShikakuPlugin.initialState(level), 1)?.kind)
    }

    @Test
    fun completeTilingEvaluatesSolved() {
        var state = ShikakuPlugin.initialState(level)
        listOf(
            ShikakuAction("place", 0, 0, 0, 0, 1),
            ShikakuAction("place", 1, 0, 2, 1, 2),
            ShikakuAction("place", 2, 1, 0, 1, 1),
        ).forEach { state = ShikakuPlugin.transition(level, state, it).state }
        assertTrue(ShikakuPlugin.evaluate(level, state).solved)
    }

    @Test
    fun seededGenerationIsStableValidAndUnique() {
        val request = GenerationRequest("shikaku-seed", 1, Difficulty(DifficultyBand.MEDIUM, 0.5))
        val first = ShikakuPlugin.generate(request)
        assertEquals(first, ShikakuPlugin.generate(request))
        assertTrue(ShikakuPlugin.validateLevel(first.level).valid)
        assertEquals(1, ShikakuPlugin.solveDetails(first.level).solutions.size)
    }
}
