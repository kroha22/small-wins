package dev.smallwins.puzzle.core.mechanics.habitat

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitatPluginTest {
    private val payload =
        HabitatPayload(
            rows = 4,
            cols = 4,
            theme = "pond",
            rowCounts = listOf(2, 0, 0, 2),
            columnCounts = listOf(1, 1, 1, 1),
            groups = listOf(HabitatGroup("a", 2, "turtle"), HabitatGroup("b", 2, "fish")),
            occupied = listOf(0, 1, 14, 15),
        )
    private val level =
        PuzzleLevel(
            PuzzleIdentity("habitat-search", "wildlife-survey", "tests", "pond", 1, 1, 1),
            Difficulty(DifficultyBand.EASY, 0.25),
            payload,
        )

    @Test
    fun validatesCountsAndGroupTotals() {
        assertTrue(HabitatPlugin.validateLevel(level).valid)
        assertFalse(HabitatPlugin.validateLevel(level.copy(payload = payload.copy(rowCounts = listOf(1, 0, 0, 2)))).valid)
        assertFalse(HabitatPlugin.validateLevel(level.copy(payload = payload.copy(groups = listOf(HabitatGroup("a", 3, "turtle"))))).valid)
    }

    @Test
    fun marksToggleAndAllOccupiedCellsCompleteTheSearch() {
        var state = HabitatPlugin.initialState(level)
        payload.occupied.forEach { cell ->
            state = HabitatPlugin.transition(level, state, HabitatAction("mark", cell, HabitatMark.FOUND)).state
        }
        assertTrue(HabitatPlugin.evaluate(level, state).solved)
        state = HabitatPlugin.transition(level, state, HabitatAction("mark", payload.occupied.first(), HabitatMark.FOUND)).state
        assertFalse(HabitatPlugin.evaluate(level, state).solved)
        assertEquals(HabitatMark.UNKNOWN, state.marks[payload.occupied.first()])
    }

    @Test
    fun hintsProgressFromRowToExactCell() {
        val state = HabitatPlugin.initialState(level)
        assertEquals("scan-row", HabitatPlugin.hint(level, state, 0)?.kind)
        assertEquals("exact-cell", HabitatPlugin.hint(level, state, 1)?.kind)
    }

    @Test
    fun generatedSearchIsStableValidAndSolvedByItsWitness() {
        val request = GenerationRequest("habitat-seed", 1, Difficulty(DifficultyBand.EXPERT, 1.0))
        val generated = HabitatPlugin.generate(request)
        assertEquals(generated, HabitatPlugin.generate(request))
        assertTrue(HabitatPlugin.validateLevel(generated.level).valid)
        assertTrue(HabitatPlugin.evaluate(generated.level, generated.solutionWitness).solved)
    }
}
