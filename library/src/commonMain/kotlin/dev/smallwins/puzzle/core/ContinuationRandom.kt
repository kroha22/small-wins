package dev.smallwins.puzzle.core

class ContinuationRandom(seedText: String) {
    private var state = stableHash(seedText)

    fun nextDouble(): Double {
        state = state * 1664525u + 1013904223u
        return state.toDouble() / 4294967296.0
    }

    fun nextInt(bound: Int): Int = kotlin.math.floor(nextDouble() * bound).toInt()

    fun <T> shuffled(values: List<T>): List<T> =
        values.mapIndexed { index, value -> Triple(value, nextDouble(), index) }
            .sortedWith(compareBy<Triple<T, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
}
