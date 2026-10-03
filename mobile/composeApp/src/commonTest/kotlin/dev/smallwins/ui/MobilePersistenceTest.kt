package dev.smallwins.ui

import dev.smallwins.shared.PipesEngine
import dev.smallwins.shared.pipesTutorial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobilePersistenceTest {
    @Test
    fun settings_progress_and_active_attempt_survive_a_new_controller() {
        val storage = FakeStorage()
        val first = MobilePortfolioState(storage)
        first.setReduceMotion(true)
        first.complete("pipes", "tutorial", moves = 3, assisted = true)
        first.saveAttempt(
            SavedAttempt(
                gameId = "pipes",
                levelId = "tutorial",
                generatedSeed = null,
                state = encodePipes(PipesEngine.initial(pipesTutorial)),
                history = emptyList(),
                moveCount = 2,
                hintsUsed = 1,
            ),
        )

        val restored = MobilePortfolioState(storage)
        assertTrue(restored.settings.reduceMotion)
        assertTrue(restored.isComplete("pipes", "tutorial"))
        assertEquals(3, restored.completed.getValue(completionKey("pipes", "tutorial")).moves)
        assertTrue(restored.completed.getValue(completionKey("pipes", "tutorial")).assisted)
        assertEquals(2, restored.activeAttempt?.moveCount)
        assertEquals(1, restored.activeAttempt?.hintsUsed)
    }

    @Test
    fun clear_removes_settings_progress_and_resume_data() {
        val storage = FakeStorage()
        val portfolio = MobilePortfolioState(storage)
        portfolio.setReduceMotion(true)
        portfolio.complete("pipes", "tutorial", moves = 1, assisted = false)
        portfolio.saveAttempt(SavedAttempt("pipes", "tutorial", null, "0,0,0,0", emptyList(), 1, 0))

        portfolio.clearAll()

        val restored = MobilePortfolioState(storage)
        assertFalse(restored.settings.reduceMotion)
        assertTrue(restored.completed.isEmpty())
        assertNull(restored.activeAttempt)
    }

    @Test
    fun saved_pipe_state_rejects_wrong_dimensions() {
        val encoded = encodePipes(PipesEngine.initial(pipesTutorial))
        assertNotNull(decodePipes(encoded, pipesTutorial.ports.size))
        assertNull(decodePipes(encoded, pipesTutorial.ports.size + 1))
    }

    @Test
    fun restored_session_keeps_history_metrics_and_can_undo() {
        val persisted = mutableListOf<List<String>>()
        val session = GameSessionState(
            initial = 0,
            restored = RestoredGameSession(state = 2, history = listOf(0, 1), moveCount = 2, hintsUsed = 1),
            encoder = { it.toString() },
        ) { state, history, moves, hints -> persisted += history + listOf(state, "$moves/$hints") }

        session.undo()

        assertEquals(1, session.state)
        assertEquals(listOf(0), session.history)
        assertEquals(2, session.moveCount)
        assertEquals(1, session.hintsUsed)
        assertEquals(listOf("0", "1", "2/1"), persisted.single())
    }
}

private class FakeStorage : KeyValueStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
