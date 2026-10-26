package dev.smallwins.puzzle.core.mechanics.colorneighbors

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import dev.smallwins.puzzle.core.SessionCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorNeighborsPluginTest {
    @Test
    fun countsDiagonalNeighborsAndAcceptsContradictoryHypotheses() {
        val level = zeroAroundLevel()
        assertEquals(listOf(0, 1, 2, 3, 5, 6, 7, 8), neighbors(4, 3, 3).sorted())
        var state = ColorNeighborsPlugin.initialState(level)
        val changed = ColorNeighborsPlugin.transition(level, state, ColorNeighborsAction("paint", 0, 0))
        assertTrue(changed.accepted)
        state = changed.state
        assertEquals(listOf(4), ColorNeighborsPlugin.evaluate(level, state).metrics.conflictClueCells)
        assertFalse(ColorNeighborsPlugin.evaluate(level, state).solved)
    }

    @Test
    fun exclusionsToggleButCannotRemoveEveryColor() {
        val level = zeroAroundLevel()
        val initial = ColorNeighborsPlugin.initialState(level)
        val excluded = ColorNeighborsPlugin.transition(level, initial, ColorNeighborsAction("exclude", 0, 0))
        assertTrue(excluded.accepted)
        assertEquals(1, excluded.state.excluded[0])
        val rejected = ColorNeighborsPlugin.transition(level, excluded.state, ColorNeighborsAction("exclude", 0, 1))
        assertFalse(rejected.accepted)
        assertEquals(excluded.state, rejected.state)
        val restored = ColorNeighborsPlugin.transition(level, excluded.state, ColorNeighborsAction("exclude", 0, 0))
        assertEquals(initial, restored.state)
    }

    @Test
    fun solverAndHintUseVisibleConstraints() {
        val level = zeroAroundLevel()
        val solution = assertNotNull(ColorNeighborsPlugin.solve(level))
        assertTrue(ColorNeighborsPlugin.evaluate(level, solution).solved)
        val hint = assertNotNull(ColorNeighborsPlugin.hint(level, ColorNeighborsPlugin.initialState(level), 0))
        assertEquals("forced-color", hint.kind)
        assertEquals(1, ColorNeighborsPlugin.solveDetails(level, limit = 2).solutions.size)
    }

    @Test
    fun generatorIsDeterministicUniqueAndSerializable() {
        val request = GenerationRequest("neighbors-seed", 1, Difficulty(DifficultyBand.MEDIUM, 0.5))
        val first = ColorNeighborsPlugin.generate(request)
        val second = ColorNeighborsPlugin.generate(request)
        assertEquals(first, second)
        assertTrue(ColorNeighborsPlugin.validateLevel(first.level).valid)
        assertTrue(ColorNeighborsPlugin.evaluate(first.level, first.solutionWitness).solved)
        val result = ColorNeighborsPlugin.solveDetails(first.level, limit = 2)
        assertFalse(result.exceeded)
        assertEquals(1, result.solutions.size)
        val encoded = SessionCodec.encode(
            SessionCodec.fresh(first.level.identity, first.solutionWitness, "neighbors-attempt"),
            ColorNeighborsState.serializer(),
        )
        val restored = SessionCodec.restore(
            first.level.identity,
            ColorNeighborsPlugin.initialState(first.level),
            encoded,
            "fallback",
            ColorNeighborsState.serializer(),
        ) { ColorNeighborsPlugin.validateState(first.level, it) }
        assertTrue(restored.restored)
        assertEquals(first.solutionWitness, restored.session.state)
    }

    private fun zeroAroundLevel() = PuzzleLevel(
        identity = PuzzleIdentity("color-neighbors", "color-neighbors", "test", "zero-around", 1, 1, 1),
        difficulty = Difficulty(DifficultyBand.TUTORIAL, 0.0),
        payload = ColorNeighborsPayload(3, 3, 2, listOf(ColorNeighborClue(4, 0, 0))),
    )
}
