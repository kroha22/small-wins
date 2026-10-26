package dev.smallwins.puzzle.core

import dev.smallwins.puzzle.core.mechanics.network.NetworkAction
import dev.smallwins.puzzle.core.mechanics.network.NetworkGeneratorMetrics
import dev.smallwins.puzzle.core.mechanics.network.NetworkLevel
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkPayload
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import dev.smallwins.puzzle.core.mechanics.placement.PlacementAction
import dev.smallwins.puzzle.core.mechanics.placement.PlacementGeneratorMetrics
import dev.smallwins.puzzle.core.mechanics.placement.PlacementLevel
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPlugin
import dev.smallwins.puzzle.core.mechanics.placement.PlacementPayload
import dev.smallwins.puzzle.core.mechanics.placement.PlacementState
import dev.smallwins.puzzle.core.mechanics.route.RouteAction
import dev.smallwins.puzzle.core.mechanics.route.RouteGeneratorMetrics
import dev.smallwins.puzzle.core.mechanics.route.RoutePlugin
import dev.smallwins.puzzle.core.mechanics.route.RoutePayload
import dev.smallwins.puzzle.core.mechanics.route.RouteState
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuAction
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuGeneratorMetrics
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuLevel
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuPlugin
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuPayload
import dev.smallwins.puzzle.core.mechanics.shikaku.ShikakuState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class ConformanceSuite(val cases: List<ConformanceCase>)

@Serializable
private data class ConformanceCase(
    val mechanicId: String,
    val level: JsonObject,
    val invalidLevel: JsonObject,
    val actions: List<JsonObject>,
    val expectedStates: List<JsonObject>,
    val solved: Boolean,
    val generationRequest: JsonObject,
    val expectedGeneration: JsonObject,
)

class ConformanceFixtureTest {
    @Test
    fun everyMechanicPassesTheSamePortableTraceContract() {
        val suite = libraryJson.decodeFromString<ConformanceSuite>(readConformanceFixture("core-v1.json"))
        suite.cases.forEach { fixture ->
            val adapter = fixtureAdapter(fixture.mechanicId)
            assertTrue(adapter.validateLevel(fixture.level), "${fixture.mechanicId} valid level")
            assertFalse(adapter.validateLevel(fixture.invalidLevel), "${fixture.mechanicId} invalid level")
            var state = adapter.initialState(fixture.level)
            fixture.actions.zip(fixture.expectedStates).forEach { (action, expected) ->
                state = adapter.transition(fixture.level, state, action)
                assertEquals(expected, state, "${fixture.mechanicId} transition")
            }
            assertEquals(fixture.solved, adapter.solved(fixture.level, state))
            assertJsonEquivalent(fixture.expectedGeneration, adapter.generate(fixture.generationRequest), "${fixture.mechanicId} generation")
            adapter.assertSessionRoundTripAndVersionRejection(fixture.level, state)
        }
    }
}

private interface FixtureAdapter {
    fun validateLevel(level: JsonObject): Boolean
    fun initialState(level: JsonObject): JsonObject
    fun transition(level: JsonObject, state: JsonObject, action: JsonObject): JsonObject
    fun solved(level: JsonObject, state: JsonObject): Boolean
    fun generate(request: JsonObject): JsonObject
    fun assertSessionRoundTripAndVersionRejection(level: JsonObject, state: JsonObject)
}

private fun fixtureAdapter(mechanicId: String): FixtureAdapter =
    when (mechanicId) {
        "network" -> {
            val level = PuzzleLevel.serializer(NetworkPayload.serializer())
            typedFixtureAdapter(
                level,
                NetworkState.serializer(),
                NetworkAction.serializer(),
                GenerationResult.serializer(level, NetworkState.serializer(), NetworkGeneratorMetrics.serializer()),
                NetworkPlugin,
            )
        }
        "purrdoku" -> {
            val level = PuzzleLevel.serializer(PlacementPayload.serializer())
            typedFixtureAdapter(
                level,
                PlacementState.serializer(),
                PlacementAction.serializer(),
                GenerationResult.serializer(level, PlacementState.serializer(), PlacementGeneratorMetrics.serializer()),
                PlacementPlugin,
            )
        }
        "route" -> {
            val level = PuzzleLevel.serializer(RoutePayload.serializer())
            typedFixtureAdapter(
                level,
                RouteState.serializer(),
                RouteAction.serializer(),
                GenerationResult.serializer(level, RouteState.serializer(), RouteGeneratorMetrics.serializer()),
                RoutePlugin,
            )
        }
        "shikaku" -> {
            val level = PuzzleLevel.serializer(ShikakuPayload.serializer())
            typedFixtureAdapter(
                level,
                ShikakuState.serializer(),
                ShikakuAction.serializer(),
                GenerationResult.serializer(level, ShikakuState.serializer(), ShikakuGeneratorMetrics.serializer()),
                ShikakuPlugin,
            )
        }
        else -> error("No fixture adapter for $mechanicId")
    }

private fun <Level : PuzzleLevel<*>, State, Action, Witness, Metrics, GeneratorMetrics> typedFixtureAdapter(
    levelSerializer: kotlinx.serialization.KSerializer<Level>,
    stateSerializer: kotlinx.serialization.KSerializer<State>,
    actionSerializer: kotlinx.serialization.KSerializer<Action>,
    generationSerializer: kotlinx.serialization.KSerializer<GenerationResult<Level, Witness, GeneratorMetrics>>,
    plugin: ActivityPlugin<Level, State, Action, Witness, Metrics, GeneratorMetrics>,
) = object : FixtureAdapter {
    override fun validateLevel(level: JsonObject) = plugin.validateLevel(decode(level, levelSerializer)).valid

    override fun initialState(level: JsonObject) = encode(plugin.initialState(decode(level, levelSerializer)), stateSerializer)

    override fun transition(level: JsonObject, state: JsonObject, action: JsonObject): JsonObject {
        val result = plugin.transition(decode(level, levelSerializer), decode(state, stateSerializer), decode(action, actionSerializer))
        assertTrue(result.accepted)
        return encode(result.state, stateSerializer)
    }

    override fun solved(level: JsonObject, state: JsonObject) =
        plugin.evaluate(decode(level, levelSerializer), decode(state, stateSerializer)).solved

    override fun generate(request: JsonObject) =
        libraryJson.encodeToJsonElement(
            generationSerializer,
            plugin.generate(libraryJson.decodeFromJsonElement<GenerationRequest>(request)),
        ) as JsonObject

    override fun assertSessionRoundTripAndVersionRejection(level: JsonObject, state: JsonObject) {
        val typedLevel = decode(level, levelSerializer)
        val typedState = decode(state, stateSerializer)
        val saved = SessionCodec.fresh(typedLevel.identity, typedState, "fixture-attempt")
        val raw = SessionCodec.encode(saved, stateSerializer)
        val restored = SessionCodec.restore(typedLevel.identity, plugin.initialState(typedLevel), raw, "fresh", stateSerializer) {
            plugin.validateState(typedLevel, it)
        }
        assertTrue(restored.restored)
        assertEquals(typedState, restored.session.state)
        val rejected = SessionCodec.restore(
            typedLevel.identity.copy(contentVersion = typedLevel.identity.contentVersion + 1),
            plugin.initialState(typedLevel),
            raw,
            "fresh",
            stateSerializer,
        ) { plugin.validateState(typedLevel, it) }
        assertFalse(rejected.restored)
        assertEquals(SessionRestoreReason.INCOMPATIBLE_LEVEL, rejected.reason)
    }
}

private fun <T> decode(element: JsonElement, serializer: kotlinx.serialization.KSerializer<T>) =
    libraryJson.decodeFromJsonElement(serializer, element)

private fun <T> encode(value: T, serializer: kotlinx.serialization.KSerializer<T>) =
    libraryJson.encodeToJsonElement(serializer, value) as JsonObject

expect fun readConformanceFixture(name: String): String

private fun assertJsonEquivalent(expected: JsonElement, actual: JsonElement, context: String) {
    when {
        expected is JsonObject && actual is JsonObject -> {
            assertEquals(expected.keys, actual.keys, "$context keys")
            expected.forEach { (key, value) -> assertJsonEquivalent(value, actual.getValue(key), "$context.$key") }
        }
        expected is JsonArray && actual is JsonArray -> {
            assertEquals(expected.size, actual.size, "$context size")
            expected.indices.forEach { index -> assertJsonEquivalent(expected[index], actual[index], "$context[$index]") }
        }
        expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString &&
            expected.doubleOrNull != null && actual.doubleOrNull != null ->
            assertEquals(expected.doubleOrNull, actual.doubleOrNull, context)
        else -> assertEquals(expected, actual, context)
    }
}
