@file:OptIn(ExperimentalJsExport::class)

package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NetworkAction
import dev.smallwins.puzzle.core.mechanics.network.NetworkLevel
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import dev.smallwins.puzzle.core.mechanics.habitat.HabitatAction
import dev.smallwins.puzzle.core.mechanics.habitat.HabitatLevel
import dev.smallwins.puzzle.core.mechanics.habitat.HabitatPlugin
import dev.smallwins.puzzle.core.mechanics.habitat.HabitatState
import dev.smallwins.puzzle.core.mechanics.placement.PlacementAction
import dev.smallwins.puzzle.core.mechanics.placement.PlacementLevel
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPlugin
import dev.smallwins.puzzle.core.mechanics.placement.PlacementState
import dev.smallwins.puzzle.core.mechanics.route.RouteAction
import dev.smallwins.puzzle.core.mechanics.route.RouteLevel
import dev.smallwins.puzzle.core.mechanics.route.RoutePlugin
import dev.smallwins.puzzle.core.mechanics.route.RouteState
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuAction
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuLevel
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuPlugin
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuState
import dev.smallwins.puzzle.core.mechanics.untangle.UntangleAction
import dev.smallwins.puzzle.core.mechanics.untangle.UntangleLevel
import dev.smallwins.puzzle.core.mechanics.untangle.UntanglePlugin
import dev.smallwins.puzzle.core.mechanics.untangle.UntangleState
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
        "habitat-search" -> libraryJson.encodeToString(HabitatPlugin.generate(libraryJson.decodeFromString(requestJson)))
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.generate(libraryJson.decodeFromString(requestJson)))
        "route" -> libraryJson.encodeToString(RoutePlugin.generate(libraryJson.decodeFromString(requestJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.generate(libraryJson.decodeFromString(requestJson)))
        "untangle" -> libraryJson.encodeToString(UntanglePlugin.generate(libraryJson.decodeFromString(requestJson)))
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
        "habitat-search" -> {
            val result =
                runCatching { HabitatPlugin.validateLevel(libraryJson.decodeFromString<HabitatLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
            libraryJson.encodeToString(result)
        }
        "purrdoku" -> {
            val result =
                runCatching { PlacementPlugin.validateLevel(libraryJson.decodeFromString<PlacementLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
            libraryJson.encodeToString(result)
        }
        "route" -> {
            val result =
                runCatching { RoutePlugin.validateLevel(libraryJson.decodeFromString<RouteLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
            libraryJson.encodeToString(result)
        }
        "shikaku" -> {
            val result =
                runCatching { ShikakuPlugin.validateLevel(libraryJson.decodeFromString<ShikakuLevel>(levelJson)) }
                    .getOrElse { ValidationResult(false, listOf(ValidationIssue("json.invalid", "$"))) }
            libraryJson.encodeToString(result)
        }
        "untangle" -> {
            val result =
                runCatching { UntanglePlugin.validateLevel(libraryJson.decodeFromString<UntangleLevel>(levelJson)) }
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
        "habitat-search" -> libraryJson.encodeToString(HabitatPlugin.initialState(libraryJson.decodeFromString<HabitatLevel>(levelJson)))
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.initialState(libraryJson.decodeFromString<PlacementLevel>(levelJson)))
        "route" -> libraryJson.encodeToString(RoutePlugin.initialState(libraryJson.decodeFromString<RouteLevel>(levelJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.initialState(libraryJson.decodeFromString<ShikakuLevel>(levelJson)))
        "untangle" -> libraryJson.encodeToString(UntanglePlugin.initialState(libraryJson.decodeFromString<UntangleLevel>(levelJson)))
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val state = libraryJson.decodeFromString<HabitatState>(stateJson)
            libraryJson.encodeToString(HabitatPlugin.validateState(level, state))
        }
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.validateState(level, state))
        }
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val state = libraryJson.decodeFromString<RouteState>(stateJson)
            libraryJson.encodeToString(RoutePlugin.validateState(level, state))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.validateState(level, state))
        }
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val state = libraryJson.decodeFromString<UntangleState>(stateJson)
            libraryJson.encodeToString(UntanglePlugin.validateState(level, state))
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val state = libraryJson.decodeFromString<HabitatState>(stateJson)
            val action = libraryJson.decodeFromString<HabitatAction>(actionJson)
            libraryJson.encodeToString(HabitatPlugin.transition(level, state, action))
        }
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            val action = libraryJson.decodeFromString<PlacementAction>(actionJson)
            libraryJson.encodeToString(PlacementPlugin.transition(level, state, action))
        }
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val state = libraryJson.decodeFromString<RouteState>(stateJson)
            val action = libraryJson.decodeFromString<RouteAction>(actionJson)
            libraryJson.encodeToString(RoutePlugin.transition(level, state, action))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            val action = libraryJson.decodeFromString<ShikakuAction>(actionJson)
            libraryJson.encodeToString(ShikakuPlugin.transition(level, state, action))
        }
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val state = libraryJson.decodeFromString<UntangleState>(stateJson)
            val action = libraryJson.decodeFromString<UntangleAction>(actionJson)
            libraryJson.encodeToString(UntanglePlugin.transition(level, state, action))
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val state = libraryJson.decodeFromString<HabitatState>(stateJson)
            libraryJson.encodeToString(HabitatPlugin.evaluate(level, state))
        }
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.evaluate(level, state))
        }
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val state = libraryJson.decodeFromString<RouteState>(stateJson)
            libraryJson.encodeToString(RoutePlugin.evaluate(level, state))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.evaluate(level, state))
        }
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val state = libraryJson.decodeFromString<UntangleState>(stateJson)
            libraryJson.encodeToString(UntanglePlugin.evaluate(level, state))
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
        "habitat-search" -> libraryJson.encodeToString(HabitatPlugin.solve(libraryJson.decodeFromString<HabitatLevel>(levelJson)))
        "purrdoku" -> libraryJson.encodeToString(PlacementPlugin.solve(libraryJson.decodeFromString<PlacementLevel>(levelJson)))
        "route" -> libraryJson.encodeToString(RoutePlugin.solve(libraryJson.decodeFromString<RouteLevel>(levelJson)))
        "shikaku" -> libraryJson.encodeToString(ShikakuPlugin.solve(libraryJson.decodeFromString<ShikakuLevel>(levelJson)))
        "untangle" -> libraryJson.encodeToString(UntanglePlugin.solve(libraryJson.decodeFromString<UntangleLevel>(levelJson)))
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
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val state = stateJson?.let { libraryJson.decodeFromString<RouteState>(it) } ?: RoutePlugin.initialState(level)
            libraryJson.encodeToString(RoutePlugin.solveDetails(level, state, limit, budget))
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val state = libraryJson.decodeFromString<HabitatState>(stateJson)
            libraryJson.encodeToString(HabitatPlugin.hint(level, state, used))
        }
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            val state = libraryJson.decodeFromString<PlacementState>(stateJson)
            libraryJson.encodeToString(PlacementPlugin.hint(level, state, used))
        }
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val state = libraryJson.decodeFromString<RouteState>(stateJson)
            libraryJson.encodeToString(RoutePlugin.hint(level, state, used))
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            val state = libraryJson.decodeFromString<ShikakuState>(stateJson)
            libraryJson.encodeToString(ShikakuPlugin.hint(level, state, used))
        }
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val state = libraryJson.decodeFromString<UntangleState>(stateJson)
            libraryJson.encodeToString(UntanglePlugin.hint(level, state, used))
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, HabitatPlugin.initialState(level), attemptId),
                HabitatState.serializer(),
            )
        }
        "purrdoku" -> {
            val level = libraryJson.decodeFromString<PlacementLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, PlacementPlugin.initialState(level), attemptId),
                PlacementState.serializer(),
            )
        }
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, RoutePlugin.initialState(level), attemptId),
                RouteState.serializer(),
            )
        }
        "shikaku" -> {
            val level = libraryJson.decodeFromString<ShikakuLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, ShikakuPlugin.initialState(level), attemptId),
                ShikakuState.serializer(),
            )
        }
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            SessionCodec.encode(
                SessionCodec.fresh(level.identity, UntanglePlugin.initialState(level), attemptId),
                UntangleState.serializer(),
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<HabitatState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(HabitatState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(session, level.identity, HabitatState.serializer()) {
                HabitatPlugin.validateState(level, it)
            }
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
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<RouteState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(RouteState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(session, level.identity, RouteState.serializer()) {
                RoutePlugin.validateState(level, it)
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
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val session =
                PuzzleSession(
                    identity = level.identity,
                    attemptId = attemptId,
                    state = libraryJson.decodeFromString<UntangleState>(stateJson),
                    history = libraryJson.decodeFromString(ListSerializer(UntangleState.serializer()), historyJson),
                    moveCount = moveCount,
                    hintsUsed = hintsUsed,
                    revision = revision,
                )
            SessionCodec.encodeValidated(session, level.identity, UntangleState.serializer()) {
                UntanglePlugin.validateState(level, it)
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
        "habitat-search" -> {
            val level = libraryJson.decodeFromString<HabitatLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = HabitatPlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = HabitatState.serializer(),
                ) { HabitatPlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(HabitatState.serializer()), result)
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
        "route" -> {
            val level = libraryJson.decodeFromString<RouteLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = RoutePlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = RouteState.serializer(),
                ) { RoutePlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(RouteState.serializer()), result)
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
        "untangle" -> {
            val level = libraryJson.decodeFromString<UntangleLevel>(levelJson)
            val result =
                SessionCodec.restore(
                    expectedIdentity = level.identity,
                    initialState = UntanglePlugin.initialState(level),
                    raw = sessionJson,
                    fallbackAttemptId = fallbackAttemptId,
                    stateSerializer = UntangleState.serializer(),
                ) { UntanglePlugin.validateState(level, it) }
            libraryJson.encodeToString(SessionRestoreResult.serializer(UntangleState.serializer()), result)
        }
        else -> unsupported(mechanicId)
    }

private fun unsupported(mechanicId: String): Nothing =
    throw IllegalArgumentException("Unsupported mechanic: $mechanicId")
