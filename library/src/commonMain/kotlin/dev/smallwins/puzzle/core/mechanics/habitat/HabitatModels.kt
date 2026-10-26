package dev.smallwins.puzzle.core.mechanics.habitat

import dev.smallwins.puzzle.core.PuzzleLevel
import kotlinx.serialization.Serializable

@Serializable
data class HabitatGroup(
    val id: String,
    val length: Int,
    val species: String,
)

@Serializable
data class HabitatPayload(
    val rows: Int,
    val cols: Int,
    val theme: String,
    val rowCounts: List<Int>,
    val columnCounts: List<Int>,
    val groups: List<HabitatGroup>,
    val occupied: List<Int>,
)

typealias HabitatLevel = PuzzleLevel<HabitatPayload>

@Serializable
enum class HabitatMark { UNKNOWN, FOUND, EMPTY }

@Serializable
data class HabitatState(val marks: List<HabitatMark>)

typealias HabitatWitness = HabitatState

@Serializable
data class HabitatAction(val type: String, val cell: Int, val mark: HabitatMark)

@Serializable
data class HabitatEvaluationMetrics(val found: Int, val total: Int, val observed: Int)

@Serializable
data class HabitatGeneratorMetrics(val rows: Int, val cols: Int, val groups: Int, val occupied: Int)
