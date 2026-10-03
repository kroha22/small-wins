package dev.smallwins.shared

data class ShikakuClue(val cell: Int, val area: Int)
data class ShikakuLevel(val id: String, val title: String, val rows: Int, val cols: Int, val clues: List<ShikakuClue>)
data class ShikakuState(val regions: List<Int>)

object ShikakuEngine {
    fun initial(level: ShikakuLevel) = ShikakuState(List(level.rows * level.cols) { -1 })

    fun rectangleCells(level: ShikakuLevel, first: Int, second: Int): Set<Int> {
        require(first in 0 until level.rows * level.cols && second in 0 until level.rows * level.cols)
        val top = minOf(first / level.cols, second / level.cols)
        val bottom = maxOf(first / level.cols, second / level.cols)
        val left = minOf(first % level.cols, second % level.cols)
        val right = maxOf(first % level.cols, second % level.cols)
        return buildSet {
            for (row in top..bottom) for (column in left..right) add(row * level.cols + column)
        }
    }

    fun place(level: ShikakuLevel, state: ShikakuState, first: Int, second: Int): ShikakuState {
        val cells = rectangleCells(level, first, second)
        val clues = level.clues.withIndex().filter { (_, clue) -> clue.cell in cells }
        require(clues.size == 1) { "Every region must contain one clue" }
        val (clueIndex, clue) = clues.single()
        require(cells.size == clue.area) { "The rectangle area must match its clue" }
        require(cells.none { state.regions[it] >= 0 && state.regions[it] != clueIndex }) { "Regions cannot overlap" }
        val next = state.regions.toMutableList()
        next.indices.filter { next[it] == clueIndex }.forEach { next[it] = -1 }
        cells.forEach { next[it] = clueIndex }
        return ShikakuState(next)
    }

    fun clear(state: ShikakuState, cell: Int): ShikakuState {
        val region = state.regions[cell]
        if (region < 0) return state
        return ShikakuState(state.regions.map { if (it == region) -1 else it })
    }

    fun solved(level: ShikakuLevel, state: ShikakuState): Boolean = state.regions.all { it >= 0 } &&
        level.clues.indices.all { clueIndex ->
            val cells = state.regions.indices.filter { state.regions[it] == clueIndex }
            cells.isNotEmpty() && run {
                val topLeft = cells.minOf { it / level.cols } * level.cols + cells.minOf { it % level.cols }
                val bottomRight = cells.maxOf { it / level.cols } * level.cols + cells.maxOf { it % level.cols }
                cells.size == level.clues[clueIndex].area &&
                    level.clues[clueIndex].cell in cells &&
                rectangleCells(level, topLeft, bottomRight) == cells.toSet()
            }
        }

    fun solutions(level: ShikakuLevel, limit: Int = 2): List<ShikakuState> {
        val options = level.clues.map { clue ->
            buildList {
                repeat(level.rows) { top ->
                    repeat(level.cols) { left ->
                        for (bottom in top until level.rows) for (right in left until level.cols) {
                            val cells = rectangleCells(level, top * level.cols + left, bottom * level.cols + right)
                            if (cells.size == clue.area && clue.cell in cells) add(cells)
                        }
                    }
                }
            }
        }
        val found = mutableListOf<ShikakuState>()
        val regions = MutableList(level.rows * level.cols) { -1 }
        fun search(clueIndex: Int) {
            if (found.size >= limit) return
            if (clueIndex == level.clues.size) {
                val state = ShikakuState(regions.toList())
                if (solved(level, state)) found += state
                return
            }
            options[clueIndex].forEach { cells ->
                if (cells.all { regions[it] < 0 }) {
                    cells.forEach { regions[it] = clueIndex }
                    search(clueIndex + 1)
                    cells.forEach { regions[it] = -1 }
                }
            }
        }
        search(0)
        return found
    }

    fun hint(level: ShikakuLevel, state: ShikakuState, used: Int): String? {
        val answer = solutions(level, 1).firstOrNull() ?: return null
        val clueIndex = level.clues.indices.firstOrNull { index -> state.regions.none { it == index } } ?: return null
        val clue = level.clues[clueIndex]
        val cells = answer.regions.indices.filter { answer.regions[it] == clueIndex }
        if (used == 0) return "Start with clue ${clue.area}. Its rectangle includes ${cells.size} squares."
        val first = cells.first()
        return "Try the rectangle containing clue ${clue.area} at row ${first / level.cols + 1}, column ${first % level.cols + 1}."
    }
}

val shikakuLevels = listOf(
    ShikakuLevel("tutorial", "A gentle rectangle", 3, 3, listOf(ShikakuClue(3, 2), ShikakuClue(5, 4), ShikakuClue(6, 2), ShikakuClue(8, 1))),
    ShikakuLevel("level-1", "First tidy split", 3, 3, listOf(ShikakuClue(0, 1), ShikakuClue(2, 2), ShikakuClue(5, 3), ShikakuClue(8, 3))),
    ShikakuLevel("level-2", "Three small spaces", 3, 3, listOf(ShikakuClue(2, 3), ShikakuClue(3, 4), ShikakuClue(5, 1), ShikakuClue(8, 1))),
    ShikakuLevel("level-3", "Keep the edges neat", 3, 3, listOf(ShikakuClue(2, 6), ShikakuClue(6, 1), ShikakuClue(8, 2))),
    ShikakuLevel("level-4", "A balanced plan", 3, 3, listOf(ShikakuClue(2, 3), ShikakuClue(5, 2), ShikakuClue(7, 4))),
    ShikakuLevel("level-5", "Pack it cleanly", 3, 3, listOf(ShikakuClue(0, 6), ShikakuClue(6, 1), ShikakuClue(7, 1), ShikakuClue(8, 1))),
    ShikakuLevel("level-6", "The final layout", 3, 3, listOf(ShikakuClue(0, 1), ShikakuClue(1, 2), ShikakuClue(5, 3), ShikakuClue(8, 3))),
)

val shikakuTutorial = shikakuLevels[0]
val shikakuLevelOne = shikakuLevels[1]
