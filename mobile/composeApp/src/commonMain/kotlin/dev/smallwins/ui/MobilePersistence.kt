package dev.smallwins.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.smallwins.shared.CatId
import dev.smallwins.shared.HabitatMark
import dev.smallwins.shared.HabitatState
import dev.smallwins.shared.PipesState
import dev.smallwins.shared.PurrdokuLevel
import dev.smallwins.shared.PurrdokuState
import dev.smallwins.shared.PuzzlePoint
import dev.smallwins.shared.ShikakuState
import dev.smallwins.shared.ShikakuLevel
import dev.smallwins.shared.ShikakuEngine
import dev.smallwins.shared.UntangleLevel
import dev.smallwins.shared.UntangleState
import dev.smallwins.shared.WaypointsState
import dev.smallwins.shared.WaypointsLevel
import dev.smallwins.shared.WaypointsEngine
import dev.smallwins.shared.HabitatLevel

internal interface KeyValueStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

@Composable
internal expect fun rememberPlatformKeyValueStorage(): KeyValueStorage

internal data class MobileSettings(val reduceMotion: Boolean = false)
internal data class LevelCompletion(val moves: Int, val assisted: Boolean)
internal data class SavedAttempt(
    val gameId: String,
    val levelId: String,
    val generatedSeed: String?,
    val state: String,
    val history: List<String>,
    val moveCount: Int,
    val hintsUsed: Int,
    val portableSession: String? = null,
)

internal interface PortableSessionAdapter<S : Any> {
    fun encode(
        state: S,
        history: List<S>,
        moveCount: Int,
        hintsUsed: Int,
    ): String

    fun restore(raw: String): RestoredGameSession<S>?
}

internal class MobilePortfolioState(private val storage: KeyValueStorage) {
    var settings by mutableStateOf(MobileSettings(storage.read(SETTINGS_KEY) == "reduce-motion"))
        private set
    var completed by mutableStateOf(readCompletions(storage.read(COMPLETIONS_KEY)))
        private set
    var activeAttempt by mutableStateOf(readAttempt(storage.read(ACTIVE_KEY)))
        private set

    fun setReduceMotion(enabled: Boolean) {
        settings = MobileSettings(enabled)
        storage.write(SETTINGS_KEY, if (enabled) "reduce-motion" else "standard-motion")
    }

    fun saveAttempt(attempt: SavedAttempt) {
        activeAttempt = attempt
        storage.write(ACTIVE_KEY, encodeAttempt(attempt))
    }

    fun complete(gameId: String, levelId: String, moves: Int, assisted: Boolean) {
        val key = completionKey(gameId, levelId)
        if (key !in completed) {
            completed = completed + (key to LevelCompletion(moves, assisted))
            storage.write(COMPLETIONS_KEY, encodeCompletions(completed))
        }
        if (activeAttempt?.let { it.gameId == gameId && it.levelId == levelId } == true) {
            activeAttempt = null
            storage.remove(ACTIVE_KEY)
        }
    }

    fun clearAll() {
        listOf(SETTINGS_KEY, COMPLETIONS_KEY, ACTIVE_KEY).forEach(storage::remove)
        settings = MobileSettings()
        completed = emptyMap()
        activeAttempt = null
    }

    fun isComplete(gameId: String, levelId: String) = completionKey(gameId, levelId) in completed

    companion object {
        private const val SETTINGS_KEY = "small-wins:v1:settings"
        private const val COMPLETIONS_KEY = "small-wins:v1:completions"
        private const val ACTIVE_KEY = "small-wins:v1:active-attempt"
    }
}

internal fun completionKey(gameId: String, levelId: String) = "$gameId/$levelId/1"

internal class GameSessionState<S : Any>(
    initial: S,
    restored: RestoredGameSession<S>?,
    private val encoder: (S) -> String,
    private val persistAttempt: (state: String, history: List<String>, moves: Int, hints: Int) -> Unit,
) {
    private val initialState = initial
    private var gestureStart: S? = null
    var state by mutableStateOf(restored?.state ?: initial)
        private set
    var history by mutableStateOf(restored?.history ?: emptyList())
        private set
    var moveCount by mutableStateOf(restored?.moveCount ?: 0)
        private set
    var hintsUsed by mutableStateOf(restored?.hintsUsed ?: 0)
        private set
    var hint by mutableStateOf<String?>(null)
        private set

    fun play(next: S) {
        if (next == state) return
        history = (history + state).takeLast(200)
        state = next
        moveCount += 1
        hint = null
        persist()
    }

    fun beginGesture() {
        gestureStart = state
    }

    fun updateGesture(next: S) {
        state = next
        hint = null
    }

    fun updateAuxiliary(next: S) {
        if (next == state) return
        state = next
        persist()
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start == state) return
        history = (history + start).takeLast(200)
        moveCount += 1
        persist()
    }

    fun cancelGesture() {
        gestureStart?.let { state = it }
        gestureStart = null
    }

    fun undo() {
        val previous = history.lastOrNull() ?: return
        state = previous
        history = history.dropLast(1)
        hint = null
        persist()
    }

    fun restart() {
        state = initialState
        history = emptyList()
        moveCount = 0
        hintsUsed = 0
        hint = null
        persist()
    }

    fun requestHint(provider: (S, Int) -> String?) {
        val nextHint = provider(state, hintsUsed)
        if (nextHint == null) {
            hint = "A reliable hint is not available for this position. No hint was used."
            return
        }
        hint = nextHint
        hintsUsed += 1
        persist()
    }

    fun persist() {
        persistAttempt(encoder(state), history.map(encoder), moveCount, hintsUsed)
    }
}

internal data class RestoredGameSession<S : Any>(
    val state: S,
    val history: List<S>,
    val moveCount: Int,
    val hintsUsed: Int,
)

@Composable
internal fun <S : Any> rememberGameSession(
    portfolio: MobilePortfolioState,
    gameId: String,
    levelId: String,
    generatedSeed: String?,
    initial: S,
    encode: (S) -> String,
    decode: (String) -> S?,
    portable: PortableSessionAdapter<S>? = null,
): GameSessionState<S> {
    val attempt = portfolio.activeAttempt?.takeIf {
        it.gameId == gameId && it.levelId == levelId && it.generatedSeed == generatedSeed
    }
    val restored = remember(gameId, levelId, generatedSeed, attempt) {
        attempt?.let { saved ->
            if (saved.portableSession != null && portable != null) {
                portable.restore(saved.portableSession)
            } else {
                val state = decode(saved.state) ?: return@let null
                val history = saved.history.map { decode(it) }
                if (history.any { it == null }) null else RestoredGameSession(
                    state,
                    history.filterNotNull(),
                    saved.moveCount.coerceAtLeast(history.size),
                    saved.hintsUsed.coerceAtLeast(0),
                )
            }
        }
    }
    val session = remember(gameId, levelId, generatedSeed) {
        GameSessionState(initial, restored, encode) { state, history, moves, hints ->
            val portableSession = portable?.encode(
                state = decode(state) ?: initial,
                history = history.mapNotNull(decode),
                moveCount = moves,
                hintsUsed = hints,
            )
            portfolio.saveAttempt(
                SavedAttempt(gameId, levelId, generatedSeed, state, history, moves, hints, portableSession),
            )
        }
    }
    LaunchedEffect(session) { session.persist() }
    return session
}

internal fun encodePipes(state: PipesState) = state.rotations.joinToString(",")
internal fun decodePipes(raw: String, expectedSize: Int): PipesState? =
    raw.csvInts()?.takeIf { it.size == expectedSize && it.all { rotation -> rotation in 0..3 } }?.let(::PipesState)

internal fun encodePurrdoku(state: PurrdokuState) = CatId.entries.joinToString(",") { state.placements[it]?.toString() ?: "-" }
internal fun decodePurrdoku(raw: String, level: PurrdokuLevel): PurrdokuState? {
    val values = raw.split(',')
    if (values.size != CatId.entries.size) return null
    val placements = CatId.entries.associateWith { cat ->
        values[cat.ordinal].takeIf { it != "-" }?.toIntOrNull()?.takeIf { it in 0 until level.size * level.size }
    }
    if (level.cats.any { cat -> values[cat.ordinal] != "-" && placements[cat] == null }) return null
    val occupied = level.cats.mapNotNull(placements::get)
    if (occupied.size != occupied.toSet().size || occupied.any { cell -> level.features.any { it.cell == cell } }) return null
    return PurrdokuState(level.cats.associateWith { placements[it] })
}

internal fun encodeUntangle(state: UntangleState) = packFields(
    state.positions.entries.sortedBy { it.key }.flatMap { (id, point) -> listOf(id, point.x.toString(), point.y.toString()) },
)
internal fun decodeUntangle(raw: String, level: UntangleLevel): UntangleState? {
    val fields = unpackFields(raw) ?: return null
    if (fields.size != level.nodes.size * 3) return null
    val positions = fields.chunked(3).associate { (id, x, y) ->
        val point = PuzzlePoint(x.toFloatOrNull() ?: return null, y.toFloatOrNull() ?: return null)
        if (id !in level.nodes.map { it.id } || point.x !in level.minimum..level.maximum || point.y !in level.minimum..level.maximum) return null
        id to point
    }
    return positions.takeIf { it.size == level.nodes.size }?.let(::UntangleState)
}

internal fun encodeWaypoints(state: WaypointsState) = state.path.joinToString(",")
internal fun decodeWaypoints(raw: String, level: WaypointsLevel): WaypointsState? {
    val path = raw.csvInts()?.takeIf { it.firstOrNull() == level.start } ?: return null
    var state = WaypointsEngine.initial(level)
    path.drop(1).forEach { cell -> state = runCatching { WaypointsEngine.add(level, state, cell) }.getOrNull() ?: return null }
    return state
}

internal fun encodeShikaku(state: ShikakuState) = state.regions.joinToString(",")
internal fun decodeShikaku(raw: String, level: ShikakuLevel): ShikakuState? {
    val regions = raw.csvInts()?.takeIf {
        it.size == level.rows * level.cols && it.all { region -> region in -1 until level.clues.size }
    } ?: return null
    val state = ShikakuState(regions)
    regions.filter { it >= 0 }.toSet().forEach { region ->
        val cells = regions.indices.filter { regions[it] == region }
        val topLeft = cells.minOf { it / level.cols } * level.cols + cells.minOf { it % level.cols }
        val bottomRight = cells.maxOf { it / level.cols } * level.cols + cells.maxOf { it % level.cols }
        val clue = level.clues[region]
        if (cells.size != clue.area || clue.cell !in cells || ShikakuEngine.rectangleCells(level, topLeft, bottomRight) != cells.toSet()) return null
    }
    return state
}

internal data class HabitatSessionState(val game: HabitatState, val notes: Set<Int>)
internal fun encodeHabitat(state: HabitatSessionState) = packFields(
    listOf(state.game.marks.joinToString(",") { it.name }, state.notes.sorted().joinToString(",")),
)
internal fun decodeHabitat(raw: String, level: HabitatLevel): HabitatSessionState? {
    val expectedSize = level.rows * level.cols
    val fields = unpackFields(raw)?.takeIf { it.size == 2 } ?: return null
    val marks = fields[0].split(',').map { runCatching { HabitatMark.valueOf(it) }.getOrNull() }
    if (marks.size != expectedSize || marks.any { it == null }) return null
    val notes = if (fields[1].isBlank()) emptySet() else fields[1].csvInts()?.toSet() ?: return null
    if (notes.any { it !in 0 until expectedSize }) return null
    val validMarks = marks.filterNotNull()
    if (validMarks.indices.any { cell ->
            (validMarks[cell] == HabitatMark.FOUND && cell !in level.occupied) ||
                (validMarks[cell] == HabitatMark.EMPTY && cell in level.occupied)
        }
    ) return null
    return HabitatSessionState(HabitatState(validMarks), notes)
}

private fun String.csvInts(): List<Int>? =
    if (isBlank()) emptyList() else split(',').map { it.toIntOrNull() ?: return null }

private fun encodeAttempt(attempt: SavedAttempt) = packFields(
    listOf(
        "2", attempt.gameId, attempt.levelId, attempt.generatedSeed.orEmpty(), attempt.state,
        attempt.moveCount.toString(), attempt.hintsUsed.toString(), attempt.history.size.toString(),
        attempt.portableSession.orEmpty(),
    ) + attempt.history,
)

private fun readAttempt(raw: String?): SavedAttempt? {
    val fields = raw?.let(::unpackFields) ?: return null
    if (fields.size < 8 || fields[0] !in setOf("1", "2")) return null
    val moves = fields[5].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val hints = fields[6].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val historySize = fields[7].toIntOrNull()?.takeIf { it in 0..200 } ?: return null
    val historyOffset = if (fields[0] == "2") 9 else 8
    if (fields.size != historyOffset + historySize) return null
    return SavedAttempt(
        fields[1],
        fields[2],
        fields[3].ifEmpty { null },
        fields[4],
        fields.drop(historyOffset),
        moves,
        hints,
        if (fields[0] == "2") fields[8].ifEmpty { null } else null,
    )
}

private fun encodeCompletions(completions: Map<String, LevelCompletion>) = packFields(
    listOf("1") + completions.entries.sortedBy { it.key }.flatMap { (key, result) ->
        listOf(key, result.moves.toString(), result.assisted.toString())
    },
)

private fun readCompletions(raw: String?): Map<String, LevelCompletion> {
    val fields = raw?.let(::unpackFields) ?: return emptyMap()
    if (fields.firstOrNull() != "1" || (fields.size - 1) % 3 != 0) return emptyMap()
    return fields.drop(1).chunked(3).mapNotNull { (key, moves, assisted) ->
        val moveCount = moves.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
        val wasAssisted = assisted.toBooleanStrictOrNull() ?: return@mapNotNull null
        key to LevelCompletion(moveCount, wasAssisted)
    }.toMap()
}

private fun packFields(fields: List<String>) = buildString {
    fields.forEach { field -> append(field.length).append(':').append(field) }
}

private fun unpackFields(raw: String): List<String>? {
    val fields = mutableListOf<String>()
    var offset = 0
    while (offset < raw.length) {
        val separator = raw.indexOf(':', offset).takeIf { it >= offset } ?: return null
        val length = raw.substring(offset, separator).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val start = separator + 1
        val end = start + length
        if (end > raw.length) return null
        fields += raw.substring(start, end)
        offset = end
    }
    return fields
}
