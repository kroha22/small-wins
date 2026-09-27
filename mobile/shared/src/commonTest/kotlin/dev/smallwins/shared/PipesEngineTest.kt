package dev.smallwins.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipesEngineTest {
    @Test
    fun initial_state_copies_level_rotations() {
        val mutableRotations = mutableListOf(0, 1, 2, 3)
        val level = pipesTutorial.copy(initialRotations = mutableRotations)

        val state = PipesEngine.initial(level)
        mutableRotations[0] = 2

        assertNotSame(mutableRotations, state.rotations)
        assertEquals(0, state.rotations[0])
    }

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

    @Test
    fun counterclockwise_rotation_wraps_and_does_not_change_other_cells() {
        val initial = PipesEngine.initial(pipesTutorial)

        val rotated = PipesEngine.rotate(pipesTutorial, initial, cell = 1, quarterTurns = -1)

        assertEquals(listOf(3, 3, 0, 0), rotated.rotations)
        assertEquals(listOf(3, 0, 0, 0), initial.rotations)
    }

    @Test
    fun invalid_rotation_requests_are_rejected() {
        val initial = PipesEngine.initial(pipesTutorial)

        assertFailsWith<IllegalArgumentException> { PipesEngine.rotate(pipesTutorial, initial, cell = -1) }
        assertFailsWith<IllegalArgumentException> { PipesEngine.rotate(pipesTutorial, initial, cell = 4) }
        assertFailsWith<IllegalArgumentException> { PipesEngine.rotate(pipesTutorial, initial, cell = 0, quarterTurns = 2) }
    }

    @Test
    fun ports_rotate_clockwise() {
        val state = PipesState(listOf(1, 0, 0, 0))

        assertEquals(listOf(3), PipesEngine.portsAt(pipesTutorial, state, cell = 0))
        assertEquals(listOf(0, 1), PipesEngine.portsAt(pipesTutorial, state, cell = 2))
    }

    @Test
    fun adjacency_respects_board_edges() {
        assertNull(PipesEngine.adjacent(pipesLevelOne, cell = 0, direction = 0))
        assertEquals(1, PipesEngine.adjacent(pipesLevelOne, cell = 0, direction = 1))
        assertEquals(3, PipesEngine.adjacent(pipesLevelOne, cell = 0, direction = 2))
        assertNull(PipesEngine.adjacent(pipesLevelOne, cell = 0, direction = 3))
        assertNull(PipesEngine.adjacent(pipesLevelOne, cell = 8, direction = 1))
        assertNull(PipesEngine.adjacent(pipesLevelOne, cell = 8, direction = 2))
    }

    @Test
    fun tutorial_initial_state_reports_partial_connection_and_leaks() {
        val connections = PipesEngine.connections(pipesTutorial, PipesEngine.initial(pipesTutorial))

        assertEquals(setOf(0), connections.reached)
        assertTrue(connections.leaks.isNotEmpty())
        assertFalse(PipesEngine.solved(pipesTutorial, PipesEngine.initial(pipesTutorial)))
    }

    @Test
    fun first_level_has_a_complete_leak_free_solution() {
        val solvedState = PipesState(List(pipesLevelOne.ports.size) { 0 })
        val connections = PipesEngine.connections(pipesLevelOne, solvedState)

        assertEquals(pipesLevelOne.ports.indices.toSet(), connections.reached)
        assertTrue(connections.leaks.isEmpty())
        assertTrue(PipesEngine.solved(pipesLevelOne, solvedState))
    }

    @Test
    fun bundled_levels_have_consistent_dimensions_and_values() {
        listOf(pipesTutorial, pipesLevelOne).forEach { level ->
            assertTrue(level.id.isNotBlank())
            assertEquals(level.rows * level.cols, level.ports.size)
            assertEquals(level.ports.size, level.initialRotations.size)
            assertTrue(level.sourceCell in level.ports.indices)
            assertTrue(level.ports.flatten().all { it in 0..3 })
            assertTrue(level.initialRotations.all { it in 0..3 })
        }
    }
}
