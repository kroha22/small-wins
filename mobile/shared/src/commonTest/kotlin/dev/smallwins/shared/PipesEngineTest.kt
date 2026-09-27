package dev.smallwins.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipesEngineTest {
    @Test
    fun rotation_is_immutable_and_wraps() {
        val initial = PipesEngine.initial(pipesTutorial)
        val rotated = PipesEngine.rotate(pipesTutorial, initial, cell = 0, quarterTurns = 1)

        assertEquals(3, initial.rotations[0])
        assertEquals(0, rotated.rotations[0])
    }

    @Test
    fun tutorial_can_be_completed() {
        var state = PipesEngine.initial(pipesTutorial)
        assertFalse(PipesEngine.solved(pipesTutorial, state))

        state = PipesEngine.rotate(pipesTutorial, state, 0)

        assertTrue(PipesEngine.solved(pipesTutorial, state))
    }
}
