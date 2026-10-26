package dev.smallwins.puzzle.core.mechanics.packing

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PackingPluginTest {
    @Test
    fun generatedPackingIsDeterministicValidAndFullyCoveredByWitness() {
        val request = GenerationRequest("packing-seed", 3, Difficulty(DifficultyBand.HARD, 1.0))
        val generated = PackingPlugin.generate(request)
        assertEquals(generated, PackingPlugin.generate(request))
        assertTrue(PackingPlugin.validateLevel(generated.level).valid)
        assertTrue(PackingPlugin.evaluate(generated.level, generated.solutionWitness).solved)
        assertTrue(generated.metrics.bentPieces >= 5)
    }

    @Test
    fun placementRejectsOverlapAndSupportsRotationAndRemoval() {
        val generated = PackingPlugin.generate(GenerationRequest("actions", 3, Difficulty(DifficultyBand.EASY, 0.0)))
        val level = generated.level
        var state = PackingPlugin.initialState(level)
        state = PackingPlugin.transition(level, state, PackingAction("rotate", 0)).state
        assertEquals(1, state.rotations[0])
        val solved = assertNotNull(PackingPlugin.solve(level))
        val first = PackingPlugin.transition(level, state.copy(rotations = solved.rotations), PackingAction("place", 0, solved.origins[0]))
        assertTrue(first.accepted)
        val overlap = PackingPlugin.transition(level, first.state, PackingAction("place", 1, solved.origins[0]))
        assertFalse(overlap.accepted)
        assertTrue(PackingPlugin.transition(level, first.state, PackingAction("remove", 0)).accepted)
    }
}
