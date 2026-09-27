package dev.smallwins.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameCatalogTest {
    @Test
    fun catalog_contains_six_unique_games() {
        assertEquals(6, portfolioGames.size)
        assertEquals(portfolioGames.size, portfolioGames.map { it.id }.toSet().size)
        assertTrue(portfolioGames.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun burrow_network_is_the_only_playable_native_vertical_slice() {
        val playable = portfolioGames.filter { it.nativeStatus == NativeStatus.PLAYABLE }

        assertEquals(listOf("pipes"), playable.map { it.id })
    }
}
