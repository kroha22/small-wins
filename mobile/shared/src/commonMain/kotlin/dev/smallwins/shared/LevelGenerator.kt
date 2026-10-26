package dev.smallwins.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Explicit, deterministic generator. The same seed produces the same level on Android and iOS. */
object LevelGenerator {
    const val VERSION = 1

    fun purrdoku(seed: String): PurrdokuLevel {
        val random = StableRandom("purrdoku/$seed")
        val rows = random.shuffled((0..2).toList())
        val columns = random.shuffled((0..2).toList())
        val cats = CatId.entries.take(3)
        val clues = cats.flatMapIndexed { index, cat ->
            listOf(CatClue.InRow(cat, rows[index]), CatClue.InColumn(cat, columns[index]))
        }
        return PurrdokuLevel(
            generatedId("purrdoku", seed), "Fresh cat puzzle", 3, cats,
            zones = List(3) { row -> CatZone("room-$row", "room ${row + 1}", (row * 3 until row * 3 + 3).toSet()) },
            features = emptyList(),
            clues = clues,
        )
    }

    fun pipes(seed: String): PipesLevel {
        return PipesEngine.generate(seed)
    }

    fun untangle(seed: String): UntangleLevel {
        val random = StableRandom("untangle/$seed")
        val count = 5 + random.nextInt(3)
        val solved = List(count) { index ->
            val angle = -PI / 2 + index * 2 * PI / count
            UntangleNode(
                id = ('A'.code + index).toChar().toString(),
                point = PuzzlePoint(tenth(50 + 37 * cos(angle)), tenth(50 + 37 * sin(angle))),
                fixed = index == 0,
            )
        }
        val scrambled = solved.toMutableList()
        scrambled[1] = solved[2].copy(id = solved[1].id)
        scrambled[2] = solved[1].copy(id = solved[2].id)
        val edges = solved.indices.map { index -> UntangleEdge(solved[index].id, solved[(index + 1) % count].id) }
        return UntangleLevel(generatedId("untangle", seed), "Fresh tangle", scrambled, edges)
    }

    fun waypoints(seed: String): WaypointsLevel {
        val random = StableRandom("waypoints/$seed")
        val size = 4 + random.nextInt(2)
        val snake = buildList {
            repeat(size) { row ->
                val columns = if (row % 2 == 0) 0 until size else (size - 1 downTo 0)
                columns.forEach { column -> add(row * size + column) }
            }
        }
        val route = snake.take((size * size * 3) / 4)
        val waypoints = listOf(route[route.size / 3], route[(route.size * 2) / 3]).toSet()
        val blocked = (0 until size * size).filter { it !in route && random.nextInt(4) != 0 }.toSet()
        return WaypointsLevel(
            generatedId("waypoints", seed), "Fresh route", size, size,
            start = route.first(), target = route.last(), waypoints = waypoints, blocked = blocked,
        )
    }

    fun shikaku(seed: String): ShikakuLevel {
        val random = StableRandom("shikaku/$seed")
        val size = 4
        val clueColumn = random.nextInt(size)
        val clues = List(size) { row -> ShikakuClue(row * size + clueColumn, size) }
        return ShikakuLevel(generatedId("shikaku", seed), "Fresh layout", size, size, clues)
    }

    private fun generatedId(gameId: String, seed: String) =
        "generated-${stableHash("v$VERSION/$gameId/$seed").toLong().toString(16)}"
    private fun tenth(value: Double) = (round(value * 10) / 10).toFloat()
}

private class StableRandom(seed: String) {
    private var state = stableHash(seed)

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        state += 0x6D2B79F5u
        var value = state
        value = (value xor (value shr 15)) * (value or 1u)
        value = value xor (value + (value xor (value shr 7)) * (value or 61u))
        val result = value xor (value shr 14)
        return (result.toULong() * bound.toULong() shr 32).toInt()
    }

    fun <T> shuffled(items: List<T>): List<T> {
        val result = items.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val other = nextInt(index + 1)
            val value = result[index]
            result[index] = result[other]
            result[other] = value
        }
        return result
    }
}

private fun stableHash(text: String): UInt {
    var hash = 2166136261u
    text.forEach { character ->
        hash = (hash xor character.code.toUInt()) * 16777619u
    }
    return hash
}
