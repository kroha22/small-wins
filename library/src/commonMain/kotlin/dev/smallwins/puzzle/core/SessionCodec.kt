package dev.smallwins.puzzle.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

object SessionCodec {
    fun <State> fresh(
        identity: PuzzleIdentity,
        state: State,
        attemptId: String,
    ): PuzzleSession<State> =
        PuzzleSession(
            identity = identity,
            attemptId = attemptId,
            state = state,
            moveCount = 0,
            hintsUsed = 0,
            revision = 0,
        )

    fun <State> encode(
        session: PuzzleSession<State>,
        stateSerializer: KSerializer<State>,
    ): String = libraryJson.encodeToString(PuzzleSession.serializer(stateSerializer), session)

    fun <State> encodeValidated(
        session: PuzzleSession<State>,
        expectedIdentity: PuzzleIdentity,
        stateSerializer: KSerializer<State>,
        validateState: (State) -> ValidationResult,
    ): String {
        val reason = incompatibility(session, expectedIdentity, validateState)
        require(reason == null) { "Invalid puzzle session: $reason" }
        return encode(session, stateSerializer)
    }

    fun <State> restore(
        expectedIdentity: PuzzleIdentity,
        initialState: State,
        raw: String?,
        fallbackAttemptId: String,
        stateSerializer: KSerializer<State>,
        validateState: (State) -> ValidationResult,
    ): SessionRestoreResult<State> {
        val fresh = fresh(expectedIdentity, initialState, fallbackAttemptId)
        if (raw == null) return SessionRestoreResult(false, fresh, SessionRestoreReason.MISSING_SESSION)
        val saved =
            runCatching {
                libraryJson.decodeFromString(PuzzleSession.serializer(stateSerializer), raw)
            }.getOrElse {
                return SessionRestoreResult(false, fresh, SessionRestoreReason.MALFORMED_SESSION)
            }
        val reason = incompatibility(saved, expectedIdentity, validateState)
        return if (reason == null) {
            SessionRestoreResult(true, saved)
        } else {
            SessionRestoreResult(false, fresh, reason)
        }
    }

    private fun <State> incompatibility(
        session: PuzzleSession<State>,
        expectedIdentity: PuzzleIdentity,
        validateState: (State) -> ValidationResult,
    ): SessionRestoreReason? {
        if (session.schemaVersion != SESSION_SCHEMA_VERSION) return SessionRestoreReason.UNSUPPORTED_SCHEMA
        if (!sameLevel(session.identity, expectedIdentity)) return SessionRestoreReason.INCOMPATIBLE_LEVEL
        if (
            session.attemptId.isBlank() ||
            session.history.size > MAX_SESSION_HISTORY ||
            session.moveCount < session.history.size ||
            session.hintsUsed < 0 ||
            session.revision < session.moveCount
        ) {
            return SessionRestoreReason.INVALID_METADATA
        }
        if (!validateState(session.state).valid || session.history.any { !validateState(it).valid }) {
            return SessionRestoreReason.INVALID_STATE
        }
        return null
    }

    private fun sameLevel(
        saved: PuzzleIdentity,
        expected: PuzzleIdentity,
    ): Boolean =
        (canonicalMechanicId(saved.mechanicId) ?: saved.mechanicId) ==
            (canonicalMechanicId(expected.mechanicId) ?: expected.mechanicId) &&
            saved.productGameId == expected.productGameId &&
            saved.levelPackId == expected.levelPackId &&
            saved.levelId == expected.levelId &&
            saved.schemaVersion == expected.schemaVersion &&
            saved.rulesVersion == expected.rulesVersion &&
            saved.contentVersion == expected.contentVersion &&
            saved.generatorVersion == expected.generatorVersion &&
            saved.seed == expected.seed
}
