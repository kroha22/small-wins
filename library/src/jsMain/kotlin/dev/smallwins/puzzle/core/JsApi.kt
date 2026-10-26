@file:OptIn(ExperimentalJsExport::class)

package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NetworkAction
import dev.smallwins.puzzle.core.mechanics.network.NetworkLevel
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import dev.smallwins.puzzle.core.mechanics.placement.PlacementAction
import dev.smallwins.puzzle.core.mechanics.placement.PlacementLevel
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPlugin
import dev.smallwins.puzzle.core.mechanics.placement.PlacementState
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuAction
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuLevel
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuPlugin
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuState
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
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.generate(libraryJson.decodeFromString(requestJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.generate(libraryJson.decodeFromString(requestJson)))
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
        "purrdoku" -> {
            val result =
                runCatching { PlacementPlugin.validateLevel(libraryJson.decodeFromString<PlacementLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
            libraryJson.encodeToString(result)
        }
        "shikaku" -> {
            val result =
                runCatching { ShikakuPlugin.validateLevel(libraryJson.decodeFromString<ShikakuLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
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
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.initialState(libraryJson.decodeFromString<PlacementLevel>(levelJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.initialState(libraryJson.decodeFromString<ShikakuLevel>(levelJson)))
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.validateState(level, state))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.validateState(level, state))
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            val action = libraryJson.decodeFromString<PlacementAction>(actionJson)
            libraryJson.encodeToString(PlacementPlugin.transition(level, state, action))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            val action = libraryJson.decodeFromString<ShikakuAction>(actionJson)
            libraryJson.encodeToString(ShikakuPlugin.transition(level, state, action))
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.evaluate(level, state))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.evaluate(level, state))
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.inspect(level, state))
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
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.solve(libraryJson.decodeFromString<PlacementLevel>(levelJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.solve(libraryJson.decodeFromString<ShikakuLevel>(levelJson)))
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
fun solveDetails(
    mechanicId: String,
    levelJson: String,
    stateJson: String?,
    limit: Int,
    budget: Int,
): String =
    when (canonicalMechanicId(mechanicId)) {
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = stateJson?.let { libraryJson.decodeFromString<PlacementState>(it) } ?: PlacementPlugin.initialState(level)
            libraryJson.encodeToString(PlacementPlugin.solveDetails(level, state, limit, budget))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            libraryJson.encodeToString(ShikakuPlugin.solveDetails(level, limit, budget))
        }
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.hint(level, state, used))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.hint(level, state, used))
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, PlacementPlugin.initialState(level), attemptId),
                PlacementState.serializer(),
            )
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, ShikakuPlugin.initialState(level), attemptId),
                ShikakuState.serializer(),
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<PlacementState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(PlacementState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(session, level.identity, PlacementState.serializer()) {
                PlacementPlugin.validateState(level, it)
            }
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<ShikakuState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(ShikakuState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(session, level.identity, ShikakuState.serializer()) {
                ShikakuPlugin.validateState(level, it)
            }
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
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = PlacementPlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = PlacementState.serializer(),
                ) { PlacementPlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(PlacementState.serializer()), result)
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = ShikakuPlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = ShikakuState.serializer(),
                ) { ShikakuPlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(ShikakuState.serializer()), result)
        }
        else -> unsupported(mechanicId)
    }

private fun unsupported(mechanicId: String): Nothing =
    throw IllegalArgumentException("Unsupported mechanic: $mechanicId")
