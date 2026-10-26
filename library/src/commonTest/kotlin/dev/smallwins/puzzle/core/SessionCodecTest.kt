package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NETWORK_GENERATOR_VERSION
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import dev.smallwins.puzzle.core.mechanics.placement.PlacementClue
import dev.smallwins.puzzle.core.mechanics.placement.PlacementEntity
import dev.smallwins.puzzle.core.mechanics.placement.PlacementFeature
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPayload
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPlugin
import dev.smallwins.puzzle.core.mechanics.placement.PlacementState
import dev.smallwins.puzzle.core.mechanics.placement.PlacementZone
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

    @Test
    fun everyVersionBoundaryRejectsAndReturnsASafeFreshSession() {
        val saved = SessionCodec.fresh(level.identity, generated.solutionWitness, "old-attempt")
        val raw = SessionCodec.encode(saved, NetworkState.serializer())
        val incompatibleIdentities =
            listOf(
                level.identity.copy(schemaVersion = level.identity.schemaVersion + 1),
                level.identity.copy(rulesVersion = level.identity.rulesVersion + 1),
                level.identity.copy(contentVersion = level.identity.contentVersion + 1),
                level.identity.copy(generatorVersion = (level.identity.generatorVersion ?: 0) + 1),
            )
        incompatibleIdentities.forEach { identity ->
            val result =
                SessionCodec.restore(
                    expectedIdentity = identity,
                    initialState = NetworkPlugin.initialState(level),
                    raw = raw,
                    fallbackAttemptId = "fresh-attempt",
                    stateSerializer = NetworkState.serializer(),
                ) { NetworkPlugin.validateState(level, it) }
            assertFalse(result.restored)
            assertEquals(SessionRestoreReason.INCOMPATIBLE_LEVEL, result.reason)
            assertEquals("fresh-attempt", result.session.attemptId)
            assertEquals(NetworkPlugin.initialState(level), result.session.state)
        }

        val unsupportedEnvelope = raw.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
        val result = restore(unsupportedEnvelope)
        assertFalse(result.restored)
        assertEquals(SessionRestoreReason.UNSUPPORTED_SCHEMA, result.reason)
        assertEquals("fresh", result.session.attemptId)
    }

    @Test
    fun placementAliasSessionRestoresButAnotherRulesVersionDoesNot() {
        val placementLevel =
            PuzzleLevel(
                PuzzleIdentity("purrdoku", "purrdoku", "tests", "portable-placement", 1, 1, 1),
                Difficulty(DifficultyBand.EASY, 0.25),
                PlacementPayload(
                    2,
                    listOf(PlacementEntity("a"), PlacementEntity("b")),
                    listOf(PlacementZone("top", listOf(0, 1)), PlacementZone("bottom", listOf(2, 3))),
                    listOf(PlacementFeature("lamp", 1), PlacementFeature("bowl", 2)),
                    listOf(PlacementClue("inColumn", "a", value = 0)),
                ),
            )
        val saved =
            SessionCodec.fresh(
                placementLevel.identity.copy(mechanicId = "cats"),
                PlacementState(mapOf("a" to 0, "b" to 3)),
                "placement-attempt",
            )
        val raw = SessionCodec.encode(saved, PlacementState.serializer())
        val restored =
            SessionCodec.restore(
                expectedIdentity = placementLevel.identity,
                initialState = PlacementPlugin.initialState(placementLevel),
                raw = raw,
                fallbackAttemptId = "fresh",
                stateSerializer = PlacementState.serializer(),
            ) { PlacementPlugin.validateState(placementLevel, it) }
        assertTrue(restored.restored)

        val incompatible =
            SessionCodec.restore(
                expectedIdentity = placementLevel.identity.copy(rulesVersion = 2),
                initialState = PlacementPlugin.initialState(placementLevel),
                raw = raw,
                fallbackAttemptId = "fresh",
                stateSerializer = PlacementState.serializer(),
            ) { PlacementPlugin.validateState(placementLevel, it) }
        assertFalse(incompatible.restored)
        assertEquals(SessionRestoreReason.INCOMPATIBLE_LEVEL, incompatible.reason)
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
