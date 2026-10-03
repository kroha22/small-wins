package dev.smallwins.shared

enum class HabitatMark { UNKNOWN, FOUND, EMPTY }
data class HabitatGroup(val id: String, val length: Int, val species: String)
data class HabitatLevel(
    val id: String,
    val title: String,
    val rows: Int,
    val cols: Int,
    val theme: String,
    val rowCounts: List<Int>,
    val columnCounts: List<Int>,
    val groups: List<HabitatGroup>,
    val occupied: List<Int>,
)
data class HabitatState(val marks: List<HabitatMark>)

object HabitatEngine {
    fun initial(level: HabitatLevel) = HabitatState(List(level.rows * level.cols) { HabitatMark.UNKNOWN })

    fun mark(level: HabitatLevel, state: HabitatState, cell: Int, mark: HabitatMark): HabitatState {
        require(cell in 0 until level.rows * level.cols) { "Cell is outside the board" }
        require(mark == HabitatMark.UNKNOWN || (mark == HabitatMark.FOUND) == (cell in level.occupied)) {
            "Survey marks must match the observed cell"
        }
        if (state.marks[cell] == HabitatMark.FOUND) return state
        val next = state.marks.toMutableList()
        next[cell] = if (next[cell] == mark) HabitatMark.UNKNOWN else mark
        return HabitatState(next)
    }

    fun solved(level: HabitatLevel, state: HabitatState) = level.occupied.all { state.marks[it] == HabitatMark.FOUND }
    fun foundCount(state: HabitatState) = state.marks.count { it == HabitatMark.FOUND }

    fun hint(level: HabitatLevel, state: HabitatState, used: Int): String? {
        val cell = level.occupied.firstOrNull { state.marks[it] != HabitatMark.FOUND }
            ?: state.marks.indices.firstOrNull { it !in level.occupied && state.marks[it] != HabitatMark.EMPTY }
            ?: return null
        val row = cell / level.cols + 1
        val column = cell % level.cols + 1
        return if (used == 0) {
            "Scan row $row. One marked cell belongs to a hidden ${level.theme.lowercase()} group."
        } else {
            "Mark row $row, column $column as ${if (cell in level.occupied) "found" else "empty"}."
        }
    }
}

val habitatLevels = listOf(
    HabitatLevel(
        "tutorial", "A gentle survey", 5, 5, "Pond Life",
        listOf(3, 0, 0, 2, 0), listOf(1, 1, 1, 1, 1),
        listOf(HabitatGroup("pond-a", 3, "turtle"), HabitatGroup("pond-b", 2, "fish")),
        listOf(0, 1, 2, 18, 19),
    ),
    HabitatLevel(
        "level-1", "By the water", 5, 5, "Pond Life",
        listOf(2, 1, 0, 1, 1), listOf(1, 1, 1, 1, 1),
        listOf(HabitatGroup("fish-a", 2, "fish"), HabitatGroup("fish-b", 3, "turtle")),
        listOf(0, 1, 12, 20, 23),
    ),
    HabitatLevel(
        "level-2", "Under the hedge", 5, 5, "Burrows",
        listOf(1, 2, 0, 1, 1), listOf(1, 1, 1, 1, 1),
        listOf(
            HabitatGroup("hare-a", 2, "squirrel"), HabitatGroup("hare-b", 1, "rabbit"),
            HabitatGroup("hare-c", 1, "rabbit"), HabitatGroup("hare-d", 1, "rabbit"),
        ),
        listOf(4, 5, 6, 15, 24),
    ),
    HabitatLevel(
        "level-3", "A quiet pond", 6, 6, "Pond Life",
        listOf(2, 0, 1, 1, 0, 2), listOf(1, 1, 1, 1, 1, 1),
        listOf(
            HabitatGroup("turtle-a", 2, "turtle"), HabitatGroup("turtle-b", 2, "turtle"),
            HabitatGroup("turtle-c", 2, "turtle"),
        ),
        listOf(0, 1, 14, 15, 28, 29),
    ),
    HabitatLevel(
        "level-4", "Under the roots", 6, 6, "Burrows",
        listOf(1, 1, 2, 0, 1, 1), listOf(1, 1, 1, 1, 1, 1),
        listOf(HabitatGroup("track-a", 4, "squirrel"), HabitatGroup("track-b", 2, "squirrel")),
        listOf(0, 1, 13, 14, 28, 30),
    ),
    HabitatLevel(
        "level-5", "Deep water", 6, 6, "Pond Life",
        listOf(2, 0, 1, 1, 1, 1), listOf(1, 1, 1, 1, 1, 1),
        listOf(HabitatGroup("den-a", 3, "turtle"), HabitatGroup("den-b", 3, "turtle")),
        listOf(0, 1, 14, 20, 28, 34),
    ),
    HabitatLevel(
        "level-6", "The hidden warren", 6, 6, "Burrows",
        listOf(1, 2, 0, 1, 0, 2), listOf(1, 1, 1, 1, 1, 1),
        listOf(HabitatGroup("meadow-a", 4, "squirrel"), HabitatGroup("meadow-b", 2, "squirrel")),
        listOf(4, 6, 7, 18, 30, 32),
    ),
)

val habitatTutorial = habitatLevels[0]
val habitatLevelOne = habitatLevels[1]
