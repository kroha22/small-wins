@file:OptIn(ExperimentalJsExport::class)

package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NetworkAction
import dev.smallwins.puzzle.core.mechanics.network.NetworkLevel
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer

@JsExport
fun mechanicCatalogJson(): String = libraryJson.encodeToString(mechanicCatalog)

@JsExport
fun generate(
    mechanicId: String,
    requestJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> libraryJson.encodeToString(NetworkPlugin.generate(libraryJson.decodeFromString(requestJson)))
        else -> unsupported(mechanicId)
    }

@JsExport
fun validateLevel(
    mechanicId: String,
    levelJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val result =
                runCatching { NetworkPlugin.validateLevel(libraryJson.decodeFromString<NetworkLevel>(levelJson)) }
                    .getOrElse {
                        ValidationResult(
                            valid = false,
                            issues = listOf(ValidationIssue("json.invalid", "$")),
                        )
                    }
            libraryJson.encodeToString(result)
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun initialState(
    mechanicId: String,
    levelJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> libraryJson.encodeToString(NetworkPlugin.initialState(libraryJson.decodeFromString<NetworkLevel>(levelJson)))
        else -> unsupported(mechanicId)
    }

@JsExport
fun validateState(
    mechanicId: String,
    levelJson: String,
    stateJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val state = libraryJson.decodeFromString<NetworkState>(stateJson)
            libraryJson.encodeToString(NetworkPlugin.validateState(level, state))
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun transition(
    mechanicId: String,
    levelJson: String,
    stateJson: String,
    actionJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val state = libraryJson.decodeFromString<NetworkState>(stateJson)
            val action = libraryJson.decodeFromString<NetworkAction>(actionJson)
            libraryJson.encodeToString(NetworkPlugin.transition(level, state, action))
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun evaluate(
    mechanicId: String,
    levelJson: String,
    stateJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val state = libraryJson.decodeFromString<NetworkState>(stateJson)
            libraryJson.encodeToString(NetworkPlugin.evaluate(level, state))
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun inspect(
    mechanicId: String,
    levelJson: String,
    stateJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val state = libraryJson.decodeFromString<NetworkState>(stateJson)
            libraryJson.encodeToString(NetworkPlugin.inspect(level, state))
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun solve(
    mechanicId: String,
    levelJson: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> libraryJson.encodeToString(NetworkPlugin.solve(libraryJson.decodeFromString<NetworkLevel>(levelJson)))
        else -> unsupported(mechanicId)
    }

@JsExport
fun solveWithBudget(
    mechanicId: String,
    levelJson: String,
    budget: Int,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> libraryJson.encodeToString(NetworkPlugin.solveWithBudget(libraryJson.decodeFromString<NetworkLevel>(levelJson), budget))
        else -> unsupported(mechanicId)
    }

@JsExport
fun hint(
    mechanicId: String,
    levelJson: String,
    stateJson: String,
    used: Int,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val state = libraryJson.decodeFromString<NetworkState>(stateJson)
            libraryJson.encodeToString(NetworkPlugin.hint(level, state, used))
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun freshSession(
    mechanicId: String,
    levelJson: String,
    attemptId: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, NetworkPlugin.initialState(level), attemptId),
                NetworkState.serializer(),
            )
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun encodeSession(
    mechanicId: String,
    levelJson: String,
    attemptId: String,
    stateJson: String,
    historyJson: String,
    moveCount: Int,
    hintsUsed: Int,
    revision: Int,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<NetworkState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(NetworkState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(
                session = session,
                expectedIdentity = level.identity,
                stateSerializer = NetworkState.serializer(),
            ) { NetworkPlugin.validateState(level, it) }
        }
        else -> unsupported(mechanicId)
    }

@JsExport
fun restoreSession(
    mechanicId: String,
    levelJson: String,
    sessionJson: String?,
    fallbackAttemptId: String,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "network" -> {
            val level = libraryJson.decodeFromString<NetworkLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = NetworkPlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = NetworkState.serializer(),
                ) { NetworkPlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(NetworkState.serializer()), result)
        }
        else -> unsupported(mechanicId)
    }

private fun unsupported(mechanicId: String): Nothing =
    throw IllegalArgumentException("Unsupported mechanic: $mechanicId")
