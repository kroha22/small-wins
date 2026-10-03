package dev.smallwins.shared

data class WaypointsLevel(
    val id: String,
    val title: String,
    val rows: Int,
    val cols: Int,
    val start: Int,
    val target: Int,
    val waypoints: Set<Int>,
    val blocked: Set<Int>,
)
data class WaypointsState(val path: List<Int>)

object WaypointsEngine {
    fun initial(level: WaypointsLevel) = WaypointsState(listOf(level.start))

    fun neighbours(level: WaypointsLevel, cell: Int): List<Int> {
        val row = cell / level.cols
        val col = cell % level.cols
        return listOf(row - 1 to col, row to col + 1, row + 1 to col, row to col - 1)
            .filter { (r, c) -> r in 0 until level.rows && c in 0 until level.cols }
            .map { (r, c) -> r * level.cols + c }
    }

    fun add(level: WaypointsLevel, state: WaypointsState, cell: Int): WaypointsState {
        require(cell in 0 until level.rows * level.cols) { "Cell is outside the board" }
        val end = state.path.last()
        if (cell == end) return state
        require(cell in neighbours(level, end)) { "Choose a neighbouring cell" }
        require(cell !in level.blocked) { "This cell is blocked" }
        require(cell !in state.path) { "The path cannot visit a cell twice" }
        require(cell != level.target || level.waypoints.all { it in state.path }) { "Collect every carrot first" }
        return WaypointsState(state.path + cell)
    }

    fun undo(state: WaypointsState) = if (state.path.size > 1) WaypointsState(state.path.dropLast(1)) else state
    fun solved(level: WaypointsLevel, state: WaypointsState) =
        state.path.last() == level.target && level.waypoints.all { it in state.path }

    fun completion(level: WaypointsLevel, state: WaypointsState, budget: Int = 25_000): List<Int>? {
        val path = state.path.toMutableList()
        val seen = state.path.toMutableSet()
        var visited = 0
        fun search(): List<Int>? {
            if (++visited > budget) return null
            if (path.last() == level.target) return if (level.waypoints.all(seen::contains)) path.toList() else null
            val reachable = mutableSetOf(path.last())
            val pending = mutableListOf(path.last())
            while (pending.isNotEmpty()) {
                val cell = pending.removeAt(pending.lastIndex)
                neighbours(level, cell).forEach { candidate ->
                    if (candidate !in seen && candidate !in level.blocked && reachable.add(candidate)) pending += candidate
                }
            }
            val required = listOf(level.target) + level.waypoints.filterNot(seen::contains)
            if (required.any { it !in reachable }) return null
            val options = neighbours(level, path.last()).filter { candidate ->
                candidate !in seen && candidate !in level.blocked &&
                    (candidate != level.target || level.waypoints.all(seen::contains))
            }
            for (candidate in options) {
                path += candidate
                seen += candidate
                val answer = search()
                if (answer != null) return answer
                seen -= candidate
                path.removeAt(path.lastIndex)
            }
            return null
        }
        return search()
    }

    fun hint(level: WaypointsLevel, state: WaypointsState, used: Int): String? {
        val answer = completion(level, state)
            ?: return "This path is a dead end: no complete route remains. Undo the last step, then ask again to check the shorter path."
        val next = answer.getOrNull(state.path.size) ?: return null
        val target = answer.drop(state.path.size).firstOrNull { it in level.waypoints && it !in state.path } ?: level.target
        val row = target / level.cols + 1
        val column = target % level.cols + 1
        val targetName = if (target == level.target) "the flag" else "a carrot on a working route"
        return when (used) {
            0 -> "Look toward row $row. ${if (target == level.target) "The flag" else "A remaining carrot"} there can be reached as part of a complete route."
            1 -> "Aim for row $row, column $column, $targetName. Keep a way through to the finish."
            else -> "Next step: add row ${next / level.cols + 1}, column ${next % level.cols + 1}. A complete route through every remaining carrot still exists after this move."
        }
    }
}

val waypointsLevels = listOf(
    WaypointsLevel("tutorial", "Take a little walk", 3, 3, 0, 5, setOf(3, 7), setOf(1, 2, 4)),
    WaypointsLevel("level-1", "Scenic route", 4, 4, 0, 1, setOf(6, 7), setOf(12, 13, 15)),
    WaypointsLevel("level-2", "Three small stops", 4, 4, 0, 8, setOf(6, 15), setOf(1, 2, 3, 13)),
    WaypointsLevel("level-3", "The long way", 5, 5, 0, 14, setOf(4, 12, 18), setOf(5, 10, 15, 20, 21, 22)),
    WaypointsLevel("level-4", "A garden path", 5, 5, 0, 10, setOf(16, 14, 22), setOf(2, 3, 4, 7, 8, 9, 18)),
    WaypointsLevel("level-5", "Room for a detour", 6, 6, 0, 13, setOf(9, 5, 22, 19), setOf(24, 30, 31, 34, 35)),
    WaypointsLevel("level-6", "Homeward bound", 6, 6, 0, 34, setOf(2, 20, 3, 16), setOf(5, 31)),
)

val waypointsTutorial = waypointsLevels[0]
val waypointsLevelOne = waypointsLevels[1]
