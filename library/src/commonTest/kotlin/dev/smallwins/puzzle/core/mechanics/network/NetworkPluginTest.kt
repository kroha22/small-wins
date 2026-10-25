package dev.smallwins.puzzle.core.mechanics.network

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class NetworkPluginTest {
    private val request =
        GenerationRequest(
            seed = "portfolio-demo",
            generatorVersion = NETWORK_GENERATOR_VERSION,
            difficulty = Difficulty(DifficultyBand.MEDIUM, 0.5),
        )

    @Test
    fun sameRequestGeneratesSamePortableLevel() {
        assertEquals(NetworkPlugin.generate(request), NetworkPlugin.generate(request))
    }

    @Test
    fun generatedWitnessSolvesLevel() {
        val generated = NetworkPlugin.generate(request)
        assertTrue(NetworkPlugin.validateLevel(generated.level).valid)
        assertTrue(NetworkPlugin.evaluate(generated.level, generated.solutionWitness).solved)
    }

    @Test
    fun solverHandlesAuthoredPiecesWhoseZeroRotationsAreNotTheAnswer() {
        val generated = NetworkPlugin.generate(request)
        val authored =
            generated.level.copy(
                payload =
                    NetworkPayload(
                        rows = 2,
                        cols = 2,
                        sourceCell = 0,
                        ports = List(4) { listOf(0, 1) },
                        initialRotations = listOf(0, 2, 0, 3),
                    ),
            )
        val solution = NetworkPlugin.solve(authored)
        assertTrue(solution != null)
        assertTrue(NetworkPlugin.evaluate(authored, solution).solved)
    }

    @Test
    fun transitionDoesNotMutateInputState() {
        val generated = NetworkPlugin.generate(request)
        val state = NetworkPlugin.initialState(generated.level)
        val before = state.rotations.toList()
        val result = NetworkPlugin.transition(generated.level, state, NetworkAction("rotate", 0, 1))
        assertTrue(result.accepted)
        assertEquals(before, state.rotations)
        if (result.changed) assertNotSame(state, result.state)
    }

    @Test
    fun hintsContainSemanticDataRatherThanCopy() {
        val generated = NetworkPlugin.generate(request)
        val hint = NetworkPlugin.hint(generated.level, NetworkPlugin.initialState(generated.level), 0)
        assertTrue(hint == null || hint.kind == "row")
        if (hint != null) assertTrue("row" in hint.data)
    }

    @Test
    fun malformedSavedStateIsRejected() {
        val generated = NetworkPlugin.generate(request)
        assertFalse(NetworkPlugin.validateState(generated.level, NetworkState(listOf(0))).valid)
    }
}
