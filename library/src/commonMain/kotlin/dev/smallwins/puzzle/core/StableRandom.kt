package dev.smallwins.puzzle.core

class StableRandom(seed: String) {
    private var state = stableHash(seed)

    private fun nextUInt(): UInt {
        state += 0x6D2B79F5u
        var value = state
        value = (value xor (value shr 15)) * (value or 1u)
        value = value xor (value + (value xor (value shr 7)) * (value or 61u))
        return value xor (value shr 14)
    }

    fun nextDouble(): Double = nextUInt().toDouble() / 4294967296.0

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return (nextUInt().toULong() * bound.toULong() shr 32).toInt()
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

fun stableHash(text: String): UInt {
    var hash = 2166136261u
    text.forEach { character -> hash = (hash xor character.code.toUInt()) * 16777619u }
    return hash
}
