package dev.smallwins.shared

import kotlin.math.abs
import kotlin.math.hypot

data class PuzzlePoint(val x: Float, val y: Float)
data class UntangleNode(val id: String, val point: PuzzlePoint, val fixed: Boolean = false)
data class UntangleEdge(val first: String, val second: String)
data class UntangleLevel(
    val id: String,
    val title: String,
    val nodes: List<UntangleNode>,
    val edges: List<UntangleEdge>,
    val minimum: Float = 8f,
    val maximum: Float = 92f,
    val minimumNodeDistance: Float = 12f,
)
data class UntangleState(val positions: Map<String, PuzzlePoint>, val moveCount: Int = 0)

object UntangleEngine {
    private const val epsilon = 0.0001f

    fun initial(level: UntangleLevel) = UntangleState(level.nodes.associate { it.id to it.point })

    fun move(level: UntangleLevel, state: UntangleState, nodeId: String, point: PuzzlePoint): UntangleState {
        val node = level.nodes.firstOrNull { it.id == nodeId } ?: error("Unknown node")
        require(!node.fixed) { "This node is fixed" }
        require(point.x in level.minimum..level.maximum && point.y in level.minimum..level.maximum) {
            "Keep the node inside the board"
        }
        require(state.positions.none { (id, other) ->
            id != nodeId && hypot((other.x - point.x).toDouble(), (other.y - point.y).toDouble()) < level.minimumNodeDistance
        }) { "Leave space between the nodes" }
        if (state.positions[nodeId] == point) return state
        return state.copy(positions = state.positions + (nodeId to point), moveCount = state.moveCount + 1)
    }

    fun conflictEdges(level: UntangleLevel, state: UntangleState): Set<Int> {
        val conflicts = mutableSetOf<Int>()
        level.edges.forEachIndexed { firstIndex, first ->
            level.edges.drop(firstIndex + 1).forEachIndexed { offset, second ->
                val secondIndex = firstIndex + offset + 1
                val shared = setOf(first.first, first.second).intersect(setOf(second.first, second.second)).firstOrNull()
                val hit = if (shared == null) {
                    intersects(
                        state.positions.getValue(first.first), state.positions.getValue(first.second),
                        state.positions.getValue(second.first), state.positions.getValue(second.second),
                    )
                } else {
                    val a = if (first.first == shared) first.second else first.first
                    val b = if (second.first == shared) second.second else second.first
                    onSegment(state.positions.getValue(shared), state.positions.getValue(a), state.positions.getValue(b)) ||
                        onSegment(state.positions.getValue(shared), state.positions.getValue(b), state.positions.getValue(a))
                }
                if (hit) {
                    conflicts += firstIndex
                    conflicts += secondIndex
                }
            }
        }
        level.edges.forEachIndexed { index, edge ->
            level.nodes.forEach { node ->
                if (node.id != edge.first && node.id != edge.second &&
                    onSegment(state.positions.getValue(edge.first), state.positions.getValue(edge.second), state.positions.getValue(node.id))
                ) conflicts += index
            }
        }
        return conflicts
    }

    fun solved(level: UntangleLevel, state: UntangleState) = conflictEdges(level, state).isEmpty()

    private fun cross(a: PuzzlePoint, b: PuzzlePoint, c: PuzzlePoint) =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun onSegment(a: PuzzlePoint, b: PuzzlePoint, point: PuzzlePoint): Boolean =
        abs(cross(a, b, point)) < epsilon &&
            point.x >= minOf(a.x, b.x) - epsilon && point.x <= maxOf(a.x, b.x) + epsilon &&
            point.y >= minOf(a.y, b.y) - epsilon && point.y <= maxOf(a.y, b.y) + epsilon

    private fun intersects(a: PuzzlePoint, b: PuzzlePoint, c: PuzzlePoint, d: PuzzlePoint): Boolean {
        val abC = cross(a, b, c)
        val abD = cross(a, b, d)
        val cdA = cross(c, d, a)
        val cdB = cross(c, d, b)
        return (((abC > epsilon && abD < -epsilon) || (abC < -epsilon && abD > epsilon)) &&
            ((cdA > epsilon && cdB < -epsilon) || (cdA < -epsilon && cdB > epsilon))) ||
            onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b)
    }
}

private fun edges(vararg pairs: String) = pairs.map { pair -> UntangleEdge(pair[0].toString(), pair[1].toString()) }
private fun node(id: String, x: Float, y: Float, fixed: Boolean = false) = UntangleNode(id, PuzzlePoint(x, y), fixed)

val untangleLevels = listOf(
    UntangleLevel(
        "tutorial", "Loose ends",
        listOf(node("A", 50f, 13f), node("B", 50f, 87f), node("C", 87f, 50f), node("D", 13f, 50f)),
        edges("AB", "BC", "CD", "DA"),
    ),
    UntangleLevel(
        "level-1", "A simple twist",
        listOf(node("A", 50f, 13f), node("B", 71.7f, 79.9f), node("C", 85.2f, 38.6f), node("D", 28.3f, 79.9f), node("E", 14.8f, 38.6f)),
        edges("AB", "BC", "CD", "DE", "EA"),
    ),
    UntangleLevel(
        "level-2", "Crossed paths",
        listOf(node("A", 50f, 13f), node("B", 71.7f, 79.9f), node("C", 85.2f, 38.6f), node("D", 28.3f, 79.9f), node("E", 14.8f, 38.6f)),
        edges("AB", "BC", "CD", "DE", "EA"),
    ),
    UntangleLevel(
        "level-3", "Pinned in place",
        listOf(node("A", 50f, 13f, true), node("B", 50f, 87f), node("C", 82f, 68.5f), node("D", 82f, 31.5f), node("E", 18f, 68.5f), node("F", 18f, 31.5f)),
        edges("AB", "BC", "CD", "DE", "EF", "FA"),
    ),
    UntangleLevel(
        "level-4", "Room to move",
        listOf(node("A", 50f, 13f, true), node("B", 50f, 87f), node("C", 18f, 68.5f), node("D", 82f, 31.5f), node("E", 82f, 68.5f), node("F", 18f, 31.5f)),
        edges("AB", "BC", "CD", "DE", "EF", "FA", "AC", "AD", "AE"),
    ),
    UntangleLevel(
        "level-5", "A closer look",
        listOf(node("A", 50f, 13f, true), node("B", 66.1f, 83.3f), node("C", 13.9f, 58.2f), node("D", 78.9f, 26.9f), node("E", 33.9f, 83.3f), node("F", 86.1f, 58.2f), node("G", 21.1f, 26.9f)),
        edges("AB", "BC", "CD", "DE", "EF", "FG", "GA", "AC", "AD", "AE", "AF"),
    ),
    UntangleLevel(
        "level-6", "Clear thinking",
        listOf(node("A", 50f, 13f, true), node("B", 50f, 87f), node("C", 13f, 50f), node("D", 23.8f, 76.2f), node("E", 76.2f, 23.8f), node("F", 76.2f, 76.2f), node("G", 87f, 50f), node("H", 23.8f, 23.8f)),
        edges("AB", "BC", "CD", "DE", "EF", "FG", "GH", "HA", "AC", "AD", "AE", "AF", "AG"),
    ),
)

val untangleTutorial = untangleLevels[0]
val untangleLevelOne = untangleLevels[1]
