package dev.smallwins.puzzle.core.mechanics.sokoban

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SokobanPluginTest {
    @Test
    fun tutorialWitnessReplaysToCompletion() {
        val generated = SokobanPlugin.generate(GenerationRequest("tutorial", 3, Difficulty(DifficultyBand.TUTORIAL, 0.0)))
        var state = SokobanPlugin.initialState(generated.level)
        generated.solutionWitness.moves.forEach { direction ->
            val transition = SokobanPlugin.transition(generated.level, state, SokobanAction("move", direction))
            assertTrue(transition.accepted)
            state = transition.state
        }
        assertTrue(SokobanPlugin.evaluate(generated.level, state).solved)
    }

    @Test
    fun generatedPuzzleIsDeterministicAndSolverProvesMinimumPushes() {
        val request = GenerationRequest("parity", 3, Difficulty(DifficultyBand.EASY, 0.0), variant = "8")
        val generated = SokobanPlugin.generate(request)
        assertEquals(generated, SokobanPlugin.generate(request))
        assertTrue(SokobanPlugin.validateLevel(generated.level).valid)
        val analysis = SokobanPlugin.analyze(generated.level.payload)
        assertEquals("solved", analysis.status)
        assertTrue((analysis.minimumPushes ?: 0) >= 6)
        var state = SokobanPlugin.initialState(generated.level)
        generated.solutionWitness.moves.forEach { direction ->
            state = SokobanPlugin.transition(generated.level, state, SokobanAction("move", direction)).state
        }
        assertTrue(SokobanPlugin.evaluate(generated.level, state).solved)
    }

    @Test
    fun solverReportsBudgetWithoutCallingPuzzleUnsolvable() {
        val generated = SokobanPlugin.generate(GenerationRequest("budget", 3, Difficulty(DifficultyBand.EASY, 0.0)))
        assertEquals("budget", SokobanPlugin.analyze(generated.level.payload, 0).status)
    }
}
