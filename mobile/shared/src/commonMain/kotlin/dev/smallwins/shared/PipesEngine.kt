package dev.smallwins.shared

data class PipesLevel(
    val id: String,
    val title: String,
    val rows: Int,
    val cols: Int,
    val sourceCell: Int,
    val ports: List<List<Int>>,
    val initialRotations: List<Int>,
)

data class PipesState(val rotations: List<Int>)
data class PipeLeak(val cell: Int, val direction: Int)
data class PipeConnections(val reached: Set<Int>, val leaks: List<PipeLeak>)

object PipesEngine {
    fun initial(level: PipesLevel) = PipesState(level.initialRotations.toList())

    fun rotate(level: PipesLevel, state: PipesState, cell: Int, quarterTurns: Int = 1): PipesState {
        require(cell in level.ports.indices) { "Cell is outside the board" }
        require(quarterTurns == 1 || quarterTurns == -1) { "Only quarter turns are supported" }
        val rotations = state.rotations.toMutableList()
        rotations[cell] = (rotations[cell] + quarterTurns + 4) % 4
        return PipesState(rotations)
    }

    fun portsAt(level: PipesLevel, state: PipesState, cell: Int): List<Int> =
        level.ports[cell].map { (it + state.rotations[cell]) % 4 }

    fun adjacent(level: PipesLevel, cell: Int, direction: Int): Int? {
        val row = cell / level.cols
        val col = cell % level.cols
        val nextRow = row + listOf(-1, 0, 1, 0)[direction]
        val nextCol = col + listOf(0, 1, 0, -1)[direction]
        return if (nextRow !in 0 until level.rows || nextCol !in 0 until level.cols) {
            null
        } else {
            nextRow * level.cols + nextCol
        }
    }

    fun connections(level: PipesLevel, state: PipesState): PipeConnections {
        val reached = mutableSetOf(level.sourceCell)
        val pending = mutableListOf(level.sourceCell)
        while (pending.isNotEmpty()) {
            val cell = pending.removeAt(pending.lastIndex)
            portsAt(level, state, cell).forEach { direction ->
                val neighbour = adjacent(level, cell, direction)
                if (neighbour != null && neighbour !in reached &&
                    (direction + 2) % 4 in portsAt(level, state, neighbour)
                ) {
                    reached += neighbour
                    pending += neighbour
                }
            }
        }

        val leaks = level.ports.indices.flatMap { cell ->
            portsAt(level, state, cell).mapNotNull { direction ->
                val neighbour = adjacent(level, cell, direction)
                if (neighbour == null || (direction + 2) % 4 !in portsAt(level, state, neighbour)) {
                    PipeLeak(cell, direction)
                } else {
                    null
                }
            }
        }
        return PipeConnections(reached, leaks)
    }

    fun solved(level: PipesLevel, state: PipesState): Boolean {
        val connections = connections(level, state)
        return connections.reached.size == level.ports.size && connections.leaks.isEmpty()
    }

    fun hint(level: PipesLevel, state: PipesState, used: Int): String? {
        val answer = PipesState(List(level.ports.size) { 0 })
        val cell = level.ports.indices.firstOrNull {
            portsAt(level, state, it).sorted() != portsAt(level, answer, it).sorted()
        } ?: return null
        val row = cell / level.cols + 1
        val column = cell % level.cols + 1
        if (used == 0) return "Look in row $row. One of its pipes needs a different turn in a working arrangement."
        if (used == 1) return "Focus on the pipe at row $row, column $column. Its openings can be turned to join a complete network."
        val turns = (4 - state.rotations[cell]) % 4
        val turnText = when (turns) {
            1 -> "clockwise once"
            2 -> "clockwise twice"
            3 -> "clockwise three times"
            else -> "until it matches the working direction"
        }
        val directions = portsAt(level, answer, cell).map { listOf("up", "right", "down", "left")[it] }.joinToString(" and ")
        return "One working arrangement: turn the pipe at row $row, column $column $turnText, so its openings face $directions."
    }
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
