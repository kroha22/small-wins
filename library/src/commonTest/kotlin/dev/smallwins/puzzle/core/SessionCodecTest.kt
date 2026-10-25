package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NETWORK_GENERATOR_VERSION
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCodecTest {
    private val generated =
        NetworkPlugin.generate(
            GenerationRequest(
                seed = "portable-save",
                generatorVersion = NETWORK_GENERATOR_VERSION,
                difficulty = Difficulty(DifficultyBand.MEDIUM, 0.5),
            ),
        )
    private val level = generated.level

    @Test
    fun validSessionRoundTripsWithHistoryAndCounters() {
        val initial = NetworkPlugin.initialState(level)
        val saved =
            PuzzleSession(
                identity = level.identity,
                attemptId = "attempt-1",
                state = generated.solutionWitness,
                history = listOf(initial),
                moveCount = 3,
                hintsUsed = 1,
                revision = 4,
            )
        val raw = SessionCodec.encode(saved, NetworkState.serializer())
        val restored = restore(raw)

        assertTrue(restored.restored)
        assertEquals(saved, restored.session)
        assertEquals(null, restored.reason)
    }

    @Test
    fun aliasesRemainCompatibleButChangedContentDoesNot() {
        val saved = SessionCodec.fresh(level.identity.copy(mechanicId = "pipes"), generated.solutionWitness, "legacy")
        assertTrue(restore(SessionCodec.encode(saved, NetworkState.serializer())).restored)

        val changed = level.copy(identity = level.identity.copy(contentVersion = level.identity.contentVersion + 1))
        val result =
            SessionCodec.restore(
                expectedIdentity = changed.identity,
                initialState = NetworkPlugin.initialState(changed),
                raw = SessionCodec.encode(saved, NetworkState.serializer()),
                fallbackAttemptId = "fresh",
                stateSerializer = NetworkState.serializer(),
            ) { NetworkPlugin.validateState(changed, it) }

        assertFalse(result.restored)
        assertEquals(SessionRestoreReason.INCOMPATIBLE_LEVEL, result.reason)
        assertEquals("fresh", result.session.attemptId)
    }

    @Test
    fun malformedOrInvalidStateFallsBackWithoutThrowing() {
        val malformed = restore("{broken")
        assertFalse(malformed.restored)
        assertEquals(SessionRestoreReason.MALFORMED_SESSION, malformed.reason)

        val invalid =
            PuzzleSession(
                identity = level.identity,
                attemptId = "broken-state",
                state = NetworkState(listOf(0)),
                moveCount = 0,
                hintsUsed = 0,
                revision = 0,
            )
        val rejected = restore(SessionCodec.encode(invalid, NetworkState.serializer()))
        assertFalse(rejected.restored)
        assertEquals(SessionRestoreReason.INVALID_STATE, rejected.reason)
        assertEquals(NetworkPlugin.initialState(level), rejected.session.state)
    }

    private fun restore(raw: String) =
        SessionCodec.restore(
            expectedIdentity = level.identity,
            initialState = NetworkPlugin.initialState(level),
            raw = raw,
            fallbackAttemptId = "fresh",
            stateSerializer = NetworkState.serializer(),
        ) { NetworkPlugin.validateState(level, it) }
}
