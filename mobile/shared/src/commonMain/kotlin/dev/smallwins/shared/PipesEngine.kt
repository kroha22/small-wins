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
    fun initial(level: PipesLevel) = PipesState(level.initialRotations)

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
}

val pipesTutorial = PipesLevel(
    id = "tutorial",
    title = "First flow",
    rows = 2,
    cols = 2,
    sourceCell = 0,
    ports = listOf(listOf(2), listOf(2), listOf(0, 1), listOf(3, 0)),
    initialRotations = listOf(3, 0, 0, 0),
)

val pipesLevelOne = PipesLevel(
    id = "level-1",
    title = "Around the bend",
    rows = 3,
    cols = 3,
    sourceCell = 0,
    ports = listOf(
        listOf(1), listOf(3, 1), listOf(3, 2),
        listOf(1, 2), listOf(2, 3), listOf(0, 2),
        listOf(0), listOf(1, 0), listOf(0, 3),
    ),
    initialRotations = listOf(0, 2, 0, 0, 2, 3, 1, 1, 2),
)
