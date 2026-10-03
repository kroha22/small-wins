package dev.smallwins.shared

enum class CatId(val label: String, val mark: String) {
    GINGER("Ginger cat", "1"),
    BLACK("Black cat", "2"),
    WHITE("White cat", "3"),
    GRAY("Gray cat", "4"),
}

data class CatZone(val id: String, val label: String, val cells: Set<Int>)
data class CatFeature(val id: String, val label: String, val cell: Int)

sealed interface CatClue {
    val cat: CatId

    data class InRow(override val cat: CatId, val row: Int) : CatClue
    data class InColumn(override val cat: CatId, val column: Int) : CatClue
    data class LeftOf(override val cat: CatId, val other: CatId) : CatClue
    data class RightOf(override val cat: CatId, val other: CatId) : CatClue
    data class Above(override val cat: CatId, val other: CatId) : CatClue
    data class Below(override val cat: CatId, val other: CatId) : CatClue
    data class InZone(override val cat: CatId, val zoneId: String) : CatClue
    data class NotInZone(override val cat: CatId, val zoneId: String) : CatClue
    data class NextToFeature(override val cat: CatId, val featureId: String) : CatClue
    data class NotNextToFeature(override val cat: CatId, val featureId: String) : CatClue
}

data class PurrdokuLevel(
    val id: String,
    val title: String,
    val size: Int,
    val cats: List<CatId>,
    val zones: List<CatZone>,
    val features: List<CatFeature>,
    val clues: List<CatClue>,
)

data class PurrdokuState(val placements: Map<CatId, Int?>)
enum class ClueStatus { UNRESOLVED, SATISFIED, VIOLATED }

object PurrdokuEngine {
    fun initial(level: PurrdokuLevel) = PurrdokuState(level.cats.associateWith { null })

    fun place(level: PurrdokuLevel, state: PurrdokuState, cat: CatId, cell: Int): PurrdokuState {
        require(cat in level.cats) { "Unknown cat" }
        require(cell in 0 until level.size * level.size) { "Cell is outside the board" }
        require(level.features.none { it.cell == cell }) { "That spot belongs to an object" }
        val previous = state.placements[cat]
        val occupant = state.placements.entries.firstOrNull { it.key != cat && it.value == cell }?.key
        return PurrdokuState(
            state.placements + mapOf(cat to cell) + listOfNotNull(occupant?.let { it to previous }),
        )
    }

    fun remove(state: PurrdokuState, cat: CatId) =
        PurrdokuState(state.placements + (cat to null))

    fun status(level: PurrdokuLevel, state: PurrdokuState, clue: CatClue): ClueStatus {
        val cell = state.placements[clue.cat] ?: return ClueStatus.UNRESOLVED
        val row = cell / level.size
        val column = cell % level.size
        val satisfied = when (clue) {
            is CatClue.InRow -> row == clue.row
            is CatClue.InColumn -> column == clue.column
            is CatClue.LeftOf -> {
                val other = state.placements[clue.other] ?: return ClueStatus.UNRESOLVED
                column < other % level.size
            }
            is CatClue.RightOf -> {
                val other = state.placements[clue.other] ?: return ClueStatus.UNRESOLVED
                column > other % level.size
            }
            is CatClue.Above -> {
                val other = state.placements[clue.other] ?: return ClueStatus.UNRESOLVED
                row < other / level.size
            }
            is CatClue.Below -> {
                val other = state.placements[clue.other] ?: return ClueStatus.UNRESOLVED
                row > other / level.size
            }
            is CatClue.InZone -> level.zones.first { it.id == clue.zoneId }.cells.contains(cell)
            is CatClue.NotInZone -> !level.zones.first { it.id == clue.zoneId }.cells.contains(cell)
            is CatClue.NextToFeature -> {
                val feature = level.features.first { it.id == clue.featureId }.cell
                kotlin.math.abs(row - feature / level.size) + kotlin.math.abs(column - feature % level.size) == 1
            }
            is CatClue.NotNextToFeature -> {
                val feature = level.features.first { it.id == clue.featureId }.cell
                kotlin.math.abs(row - feature / level.size) + kotlin.math.abs(column - feature % level.size) != 1
            }
        }
        return if (satisfied) ClueStatus.SATISFIED else ClueStatus.VIOLATED
    }

    fun clueText(level: PurrdokuLevel, clue: CatClue): String = when (clue) {
        is CatClue.InRow -> "The ${clue.cat.name.lowercase()} cat is in row ${clue.row + 1}."
        is CatClue.InColumn -> "The ${clue.cat.name.lowercase()} cat is in column ${clue.column + 1}."
        is CatClue.LeftOf -> "The ${clue.cat.name.lowercase()} cat is to the left of the ${clue.other.name.lowercase()} cat."
        is CatClue.RightOf -> "The ${clue.cat.name.lowercase()} cat is to the right of the ${clue.other.name.lowercase()} cat."
        is CatClue.Above -> "The ${clue.cat.name.lowercase()} cat is above the ${clue.other.name.lowercase()} cat."
        is CatClue.Below -> "The ${clue.cat.name.lowercase()} cat is below the ${clue.other.name.lowercase()} cat."
        is CatClue.InZone -> "The ${clue.cat.name.lowercase()} cat is inside the ${level.zones.first { it.id == clue.zoneId }.label}."
        is CatClue.NotInZone -> "The ${clue.cat.name.lowercase()} cat is outside the ${level.zones.first { it.id == clue.zoneId }.label}."
        is CatClue.NextToFeature -> "The ${clue.cat.name.lowercase()} cat is next to the ${level.features.first { it.id == clue.featureId }.label}."
        is CatClue.NotNextToFeature -> "The ${clue.cat.name.lowercase()} cat is not next to the ${level.features.first { it.id == clue.featureId }.label}."
    }

    fun solved(level: PurrdokuLevel, state: PurrdokuState): Boolean {
        val cells = state.placements.values.filterNotNull()
        val rows = cells.map { it / level.size }
        val columns = cells.map { it % level.size }
        val zones = cells.mapNotNull { cell -> level.zones.firstOrNull { cell in it.cells }?.id }
        return cells.size == level.cats.size &&
            cells.toSet().size == cells.size &&
            rows.toSet().size == rows.size &&
            columns.toSet().size == columns.size &&
            zones.toSet().size == zones.size &&
            level.clues.all { status(level, state, it) == ClueStatus.SATISFIED }
    }

    fun solutions(level: PurrdokuLevel, limit: Int = 2): List<PurrdokuState> {
        val found = mutableListOf<PurrdokuState>()
        fun search(state: PurrdokuState) {
            if (found.size >= limit) return
            val cells = state.placements.values.filterNotNull()
            val rows = cells.map { it / level.size }
            val columns = cells.map { it % level.size }
            val rooms = cells.mapNotNull { cell -> level.zones.firstOrNull { cell in it.cells }?.id }
            if (rows.toSet().size != rows.size || columns.toSet().size != columns.size || rooms.toSet().size != rooms.size) return
            if (level.clues.any { status(level, state, it) == ClueStatus.VIOLATED }) return
            val cat = level.cats.firstOrNull { state.placements[it] == null }
            if (cat == null) {
                if (solved(level, state)) found += state
                return
            }
            repeat(level.size * level.size) { cell ->
                if (cell !in cells && level.features.none { it.cell == cell }) {
                    search(place(level, state, cat, cell))
                }
            }
        }
        search(initial(level))
        return found
    }

    fun hint(level: PurrdokuLevel, state: PurrdokuState, used: Int): String? {
        val answer = solutions(level, 2).singleOrNull() ?: return null
        val remaining = level.cats.filter { state.placements[it] != answer.placements[it] }
        if (remaining.isEmpty()) return null
        val cat = remaining[if (used < 4) 0 else (used - 4) % remaining.size]
        val cell = answer.placements.getValue(cat) ?: return null
        val row = cell / level.size
        val column = cell % level.size
        val zone = level.zones.firstOrNull { cell in it.cells }
        val area = zone?.let { "inside the ${it.label}" } ?: "in row ${row + 1}"
        val lead = "The ${cat.name.lowercase()} cat"
        if (used == 0) return "Extra clue 1: $lead belongs $area. Look for its spot there."
        if (used == 1) {
            val candidates = zone?.cells.orEmpty().filter { candidate ->
                candidate % level.size == column && level.features.none { it.cell == candidate }
            }
            val countText = if (candidates.size > 1) {
                " This still leaves ${candidates.size} possible squares; the next clues separate them."
            } else {
                " This identifies the only available square in that room and column."
            }
            return "Extra clue 2: $lead belongs $area, in column ${column + 1}.$countText"
        }
        val areaCells = zone?.cells ?: (0 until level.size * level.size).toSet()
        val rows = areaCells.map { it / level.size }.distinct().sorted()
        val rowCells = areaCells.filter { it / level.size == row }.sorted()
        val rowIndex = rows.indexOf(row)
        val columnIndex = rowCells.indexOf(cell)
        val rowWords = when {
            rows.size == 1 -> "only"
            rowIndex == 0 -> "top"
            rowIndex == rows.lastIndex -> "bottom"
            rows.size == 3 -> "middle"
            rowIndex == 1 -> "upper middle"
            else -> "lower middle"
        }
        val columnWords = when {
            rowCells.size == 1 -> "only"
            columnIndex == 0 -> "leftmost"
            columnIndex == rowCells.lastIndex -> "rightmost"
            rowCells.size == 3 -> "middle"
            columnIndex == 1 -> "left-hand middle"
            else -> "right-hand middle"
        }
        val areaName = zone?.let { "the ${it.label}" } ?: "the board"
        if (used == 2) {
            val feature = level.features.minByOrNull {
                kotlin.math.abs(row - it.cell / level.size) + kotlin.math.abs(column - it.cell % level.size)
            }
            if (feature != null) {
                val vertical = when {
                    row < feature.cell / level.size -> "above"
                    row > feature.cell / level.size -> "below"
                    else -> null
                }
                val horizontal = when {
                    column < feature.cell % level.size -> "to the left of"
                    column > feature.cell % level.size -> "to the right of"
                    else -> null
                }
                val relation = listOfNotNull(vertical, horizontal).joinToString(" and ")
                return "Closer clue: $lead belongs ${if (relation.isEmpty()) "on the square with" else relation} the ${feature.label}. Use that object as your landmark."
            }
            return "Closer clue: look in the $rowWords row of $areaName for the ${cat.name.lowercase()} cat."
        }
        if (used == 3) return "One more clue: in $areaName, find its $rowWords row. Choose the $columnWords square in that row for the ${cat.name.lowercase()} cat."
        val blockers = level.cats.filter { other ->
            val otherCell = state.placements[other]
            other != cat && otherCell != null && (otherCell / level.size == row || otherCell % level.size == column)
        }
        val moveFirst = if (blockers.isEmpty()) "" else " First remove ${blockers.joinToString(" and ") { "the ${it.name.lowercase()} cat" }} to free its row and column."
        return "Exact spot: place the ${cat.name.lowercase()} cat at row ${row + 1}, column ${column + 1}.$moveFirst"
    }
}

private val threeCatRooms = listOf(
    CatZone("kitchen", "kitchen", setOf(0, 1, 3, 4)),
    CatZone("living-room", "living room", setOf(2, 5)),
    CatZone("bedroom", "bedroom", setOf(6, 7, 8)),
)
private val fourCatRooms = listOf(
    CatZone("kitchen", "kitchen", setOf(0, 1, 4, 5)),
    CatZone("living-room", "living room", setOf(2, 3, 6, 7)),
    CatZone("bedroom", "bedroom", setOf(8, 9, 12, 13)),
    CatZone("study", "study", setOf(10, 11, 14, 15)),
)
private fun cats(count: Int) = CatId.entries.take(count)
private fun threeFeatures(window: Int, armchair: Int, bed: Int) = listOf(
    CatFeature("window", "window", window),
    CatFeature("armchair", "armchair", armchair),
    CatFeature("bed", "bed", bed),
)
private fun fourFeatures(bowl: Int, window: Int, armchair: Int, plant: Int) = listOf(
    CatFeature("bowl", "food bowl", bowl),
    CatFeature("window", "window", window),
    CatFeature("armchair", "armchair", armchair),
    CatFeature("plant", "plant", plant),
)

val purrdokuLevels = listOf(
    PurrdokuLevel(
        "tutorial", "Meet the cats", 3, cats(3),
        listOf(
            CatZone("kitchen", "kitchen", setOf(0, 1, 2)),
            CatZone("living-room", "living room", setOf(3, 4, 5)),
            CatZone("bedroom", "bedroom", setOf(6, 7, 8)),
        ),
        threeFeatures(1, 5, 7),
        listOf(
            CatClue.InZone(CatId.GINGER, "kitchen"),
            CatClue.InColumn(CatId.BLACK, 1),
            CatClue.NextToFeature(CatId.WHITE, "armchair"),
        ),
    ),
    PurrdokuLevel(
        "level-1", "Sunny spots", 3, cats(3), threeCatRooms, threeFeatures(1, 2, 6),
        listOf(
            CatClue.NextToFeature(CatId.WHITE, "window"),
            CatClue.RightOf(CatId.BLACK, CatId.WHITE),
            CatClue.InZone(CatId.BLACK, "bedroom"),
        ),
    ),
    PurrdokuLevel(
        "level-2", "Quiet corners", 3, cats(3), threeCatRooms, threeFeatures(0, 2, 7),
        listOf(
            CatClue.InZone(CatId.GINGER, "bedroom"),
            CatClue.InColumn(CatId.GINGER, 0),
            CatClue.InZone(CatId.BLACK, "living-room"),
            CatClue.NextToFeature(CatId.BLACK, "armchair"),
        ),
    ),
    PurrdokuLevel(
        "level-3", "Window seat", 4, cats(4), fourCatRooms, fourFeatures(1, 6, 12, 14),
        listOf(
            CatClue.NextToFeature(CatId.GINGER, "plant"),
            CatClue.InZone(CatId.GINGER, "study"),
            CatClue.LeftOf(CatId.WHITE, CatId.GRAY),
            CatClue.NotInZone(CatId.BLACK, "kitchen"),
            CatClue.NextToFeature(CatId.WHITE, "bowl"),
            CatClue.InColumn(CatId.BLACK, 3),
        ),
    ),
    PurrdokuLevel(
        "level-4", "A little company", 4, cats(4), fourCatRooms, fourFeatures(4, 2, 8, 14),
        listOf(
            CatClue.NextToFeature(CatId.GINGER, "window"),
            CatClue.LeftOf(CatId.WHITE, CatId.GINGER),
            CatClue.InColumn(CatId.GRAY, 1),
            CatClue.NotNextToFeature(CatId.BLACK, "bowl"),
            CatClue.InZone(CatId.WHITE, "study"),
            CatClue.InZone(CatId.BLACK, "bedroom"),
        ),
    ),
    PurrdokuLevel(
        "level-5", "House rules", 4, cats(4), fourCatRooms, fourFeatures(1, 2, 8, 14),
        listOf(
            CatClue.NotNextToFeature(CatId.WHITE, "window"),
            CatClue.Below(CatId.WHITE, CatId.GRAY),
            CatClue.InZone(CatId.GINGER, "bedroom"),
            CatClue.InZone(CatId.BLACK, "study"),
            CatClue.NextToFeature(CatId.GINGER, "plant"),
            CatClue.InColumn(CatId.GRAY, 3),
            CatClue.InColumn(CatId.GINGER, 1),
        ),
    ),
    PurrdokuLevel(
        "level-6", "Perfect neighbours", 4, cats(4), fourCatRooms, fourFeatures(4, 2, 8, 11),
        listOf(
            CatClue.Above(CatId.GRAY, CatId.WHITE),
            CatClue.InZone(CatId.GINGER, "study"),
            CatClue.NextToFeature(CatId.BLACK, "bowl"),
            CatClue.NotNextToFeature(CatId.GRAY, "window"),
            CatClue.NotNextToFeature(CatId.GINGER, "plant"),
            CatClue.LeftOf(CatId.GINGER, CatId.GRAY),
        ),
    ),
)

val purrdokuTutorial = purrdokuLevels[0]
val purrdokuLevelOne = purrdokuLevels[1]
