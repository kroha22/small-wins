package dev.smallwins.shared

import dev.smallwins.puzzle.core.Difficulty
import dev.smallwins.puzzle.core.DifficultyBand
import dev.smallwins.puzzle.core.GenerationRequest
import dev.smallwins.puzzle.core.PuzzleIdentity
import dev.smallwins.puzzle.core.PuzzleLevel
import dev.smallwins.puzzle.core.PuzzleSession
import dev.smallwins.puzzle.core.SessionCodec
import dev.smallwins.puzzle.core.SessionRestoreReason
import dev.smallwins.puzzle.core.mechanics.network.NETWORK_GENERATOR_VERSION
import dev.smallwins.puzzle.core.mechanics.network.NETWORK_RULES_VERSION
import dev.smallwins.puzzle.core.mechanics.network.NetworkAction
import dev.smallwins.puzzle.core.mechanics.network.NetworkLevel
import dev.smallwins.puzzle.core.mechanics.network.NetworkPayload
import dev.smallwins.puzzle.core.mechanics.network.NetworkPlugin
import dev.smallwins.puzzle.core.mechanics.network.NetworkState
import dev.smallwins.puzzle.core.mechanics.network.adjacent
import dev.smallwins.puzzle.core.mechanics.network.portsAt
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

data class PipesLevel(
    val id: String,
    val title: String,
    val rows: Int,
    val cols: Int,
    val sourceCell: Int,
    val ports: List<List<Int>>,
    val initialRotations: List<Int>,
    val portableIdentity: PuzzleIdentity? = null,
)

data class PipesState(val rotations: List<Int>)
data class PipeLeak(val cell: Int, val direction: Int)
data class PipeConnections(val reached: Set<Int>, val leaks: List<PipeLeak>)
data class RestoredPipesSession(
    val restored: Boolean,
    val state: PipesState,
    val history: List<PipesState>,
    val moveCount: Int,
    val hintsUsed: Int,
    val reason: SessionRestoreReason?,
)

object PipesEngine {
    fun initial(level: PipesLevel) = NetworkPlugin.initialState(level.asCore()).asApp()

    fun rotate(level: PipesLevel, state: PipesState, cell: Int, quarterTurns: Int = 1): PipesState {
        require(cell in level.ports.indices) { "Cell is outside the board" }
        require(quarterTurns == 1 || quarterTurns == -1) { "Only quarter turns are supported" }
        val result = NetworkPlugin.transition(
            level.asCore(),
            state.asCore(),
            NetworkAction("rotate", cell, quarterTurns),
        )
        check(result.accepted) { result.reason ?: "Rotation was rejected" }
        return result.state.asApp()
    }

    fun portsAt(level: PipesLevel, state: PipesState, cell: Int): List<Int> =
        portsAt(level.asCore(), state.asCore(), cell)

    fun adjacent(level: PipesLevel, cell: Int, direction: Int): Int? =
        adjacent(level.asCore().payload, cell, direction)

    fun connections(level: PipesLevel, state: PipesState): PipeConnections {
        val result = NetworkPlugin.inspect(level.asCore(), state.asCore())
        return PipeConnections(
            reached = result.reached.toSet(),
            leaks = result.leaks.map { PipeLeak(it.cell, it.direction) },
        )
    }

    fun solved(level: PipesLevel, state: PipesState): Boolean =
        NetworkPlugin.evaluate(level.asCore(), state.asCore()).solved

    fun hint(level: PipesLevel, state: PipesState, used: Int): String? {
        val hint = NetworkPlugin.hint(level.asCore(), state.asCore(), used) ?: return null
        val cell = hint.data.getValue("cell").jsonPrimitive.int
        val row = hint.data.getValue("row").jsonPrimitive.int + 1
        val column = hint.data.getValue("column").jsonPrimitive.int + 1
        if (hint.kind == "row") return "Look in row $row. One of its pipes needs a different turn in a working arrangement."
        if (hint.kind == "cell") return "Focus on the pipe at row $row, column $column. Its openings can be turned to join a complete network."
        val target = hint.data.getValue("targetRotation").jsonPrimitive.int
        val turns = (target - state.rotations[cell] + 4) % 4
        val turnText = when (turns) {
            1 -> "clockwise once"
            2 -> "clockwise twice"
            3 -> "clockwise three times"
            else -> "until it matches the working direction"
        }
        val directions = level.ports[cell]
            .map { (it + target) % 4 }
            .map { listOf("up", "right", "down", "left")[it] }
            .joinToString(" and ")
        return "One working arrangement: turn the pipe at row $row, column $column $turnText, so its openings face $directions."
    }

    fun generate(seed: String): PipesLevel = NetworkPlugin.generate(
        GenerationRequest(
            seed = seed,
            generatorVersion = NETWORK_GENERATOR_VERSION,
            difficulty = Difficulty(DifficultyBand.MEDIUM, 0.5),
        ),
    ).level.asApp("Fresh network")

    fun encodeSession(
        level: PipesLevel,
        attemptId: String,
        state: PipesState,
        history: List<PipesState>,
        moveCount: Int,
        hintsUsed: Int,
    ): String {
        val coreLevel = level.asCore()
        return SessionCodec.encodeValidated(
            PuzzleSession(
                identity = coreLevel.identity,
                attemptId = attemptId,
                state = state.asCore(),
                history = history.map { it.asCore() },
                moveCount = moveCount,
                hintsUsed = hintsUsed,
                revision = moveCount,
            ),
            expectedIdentity = coreLevel.identity,
            stateSerializer = NetworkState.serializer(),
        ) { NetworkPlugin.validateState(coreLevel, it) }
    }

    fun restoreSession(
        level: PipesLevel,
        raw: String?,
        fallbackAttemptId: String,
    ): RestoredPipesSession {
        val coreLevel = level.asCore()
        val result = SessionCodec.restore(
            expectedIdentity = coreLevel.identity,
            initialState = NetworkPlugin.initialState(coreLevel),
            raw = raw,
            fallbackAttemptId = fallbackAttemptId,
            stateSerializer = NetworkState.serializer(),
        ) { NetworkPlugin.validateState(coreLevel, it) }
        return RestoredPipesSession(
            restored = result.restored,
            state = result.session.state.asApp(),
            history = result.session.history.map { it.asApp() },
            moveCount = result.session.moveCount,
            hintsUsed = result.session.hintsUsed,
            reason = result.reason,
        )
    }
}

private fun PipesLevel.asCore(): NetworkLevel = PuzzleLevel(
    identity = portableIdentity ?: PuzzleIdentity(
        mechanicId = "network",
        productGameId = "pipes",
        levelPackId = if (id.startsWith("generated-")) "generated" else "mobile-authored",
        levelId = id,
        schemaVersion = 1,
        rulesVersion = NETWORK_RULES_VERSION,
        contentVersion = 1,
    ),
    difficulty = difficultyFor(id),
    payload = NetworkPayload(rows, cols, sourceCell, ports, initialRotations),
)

private fun NetworkLevel.asApp(title: String) = PipesLevel(
    id = identity.levelId,
    title = title,
    rows = payload.rows,
    cols = payload.cols,
    sourceCell = payload.sourceCell,
    ports = payload.ports,
    initialRotations = payload.initialRotations,
    portableIdentity = identity,
)

private fun NetworkState.asApp() = PipesState(rotations)
private fun PipesState.asCore() = NetworkState(rotations)

private fun difficultyFor(id: String): Difficulty = when (id) {
    "tutorial" -> Difficulty(DifficultyBand.TUTORIAL, 0.0)
    "level-1", "level-2" -> Difficulty(DifficultyBand.EASY, 0.25)
    "level-3", "level-4" -> Difficulty(DifficultyBand.MEDIUM, 0.5)
    else -> Difficulty(DifficultyBand.HARD, 0.75)
}

private fun pipesLevel(id: String, title: String, size: Int, ports: List<List<Int>>, rotations: List<Int>) =
    PipesLevel(id, title, size, size, 0, ports, rotations)

val pipesLevels = listOf(
    pipesLevel(
        "tutorial", "First flow", 2,
        listOf(listOf(2), listOf(2), listOf(0, 1), listOf(3, 0)),
        listOf(3, 0, 0, 0),
    ),
    pipesLevel(
        "level-1", "Around the bend", 3,
        listOf(
            listOf(1), listOf(3, 1), listOf(3, 2), listOf(1, 2), listOf(2, 3),
            listOf(0, 2), listOf(0), listOf(1, 0), listOf(0, 3),
        ),
        listOf(0, 2, 0, 0, 2, 3, 1, 1, 2),
    ),
    pipesLevel(
        "level-2", "Little loop", 3,
        listOf(
            listOf(2), listOf(1, 2), listOf(2, 3), listOf(0, 2), listOf(0),
            listOf(2, 0), listOf(0, 1), listOf(3, 1), listOf(3, 0),
        ),
        listOf(1, 0, 0, 1, 3, 2, 2, 2, 1),
    ),
    pipesLevel(
        "level-3", "Branching out", 4,
        listOf(
            listOf(1), listOf(3, 2), listOf(2, 1), listOf(3), listOf(1, 2), listOf(0, 3),
            listOf(1, 0), listOf(2, 3), listOf(0, 1), listOf(3, 1), listOf(3, 2), listOf(2, 0),
            listOf(1), listOf(1, 3), listOf(0, 1, 3), listOf(3, 0),
        ),
        listOf(2, 0, 1, 0, 1, 3, 2, 0, 1, 0, 0, 1, 1, 2, 0, 3),
    ),
    pipesLevel(
        "level-4", "Water garden", 4,
        listOf(
            listOf(2), listOf(2, 1), listOf(3, 1), listOf(3, 2), listOf(0, 2), listOf(2, 0),
            listOf(1, 2), listOf(0, 3), listOf(0, 2), listOf(2, 0), listOf(0, 2), listOf(2),
            listOf(0, 1), listOf(3, 0), listOf(0, 1), listOf(3, 0),
        ),
        listOf(3, 2, 3, 0, 3, 3, 2, 1, 0, 2, 2, 2, 1, 3, 2, 2),
    ),
    pipesLevel(
        "level-5", "Hidden current", 5,
        listOf(
            listOf(2), listOf(2, 1), listOf(3, 1), listOf(3, 1), listOf(3, 2),
            listOf(0, 1), listOf(3, 0), listOf(2), listOf(1, 2), listOf(0, 3),
            listOf(2), listOf(1, 2), listOf(2, 3, 0), listOf(0, 1), listOf(3, 2),
            listOf(1, 2, 0), listOf(0, 3), listOf(2, 0), listOf(1, 2), listOf(0, 3),
            listOf(0, 1), listOf(3), listOf(1, 0), listOf(0, 3, 1), listOf(3),
        ),
        listOf(3, 3, 2, 0, 1, 1, 0, 0, 1, 3, 1, 3, 3, 2, 1, 0, 1, 1, 1, 0, 0, 0, 1, 3, 2),
    ),
    pipesLevel(
        "level-6", "Everything connects", 5,
        listOf(
            listOf(1), listOf(3, 1), listOf(3, 1), listOf(3, 2), listOf(2),
            listOf(1, 2), listOf(2, 1, 3), listOf(3, 2), listOf(0, 1), listOf(3, 0, 2),
            listOf(0, 2), listOf(2, 0), listOf(0, 1), listOf(3), listOf(0, 2),
            listOf(0, 2), listOf(1, 0), listOf(1, 3), listOf(2, 3), listOf(0, 2),
            listOf(0, 1), listOf(3, 1), listOf(3), listOf(1, 0), listOf(0, 3),
        ),
        listOf(1, 1, 0, 1, 1, 0, 1, 2, 3, 1, 0, 0, 1, 0, 2, 3, 0, 3, 3, 1, 2, 2, 3, 0, 2),
    ),
)

val pipesTutorial = pipesLevels[0]
val pipesLevelOne = pipesLevels[1]
