package dev.smallwins.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameCatalogTest {
    @Test
    fun catalog_contains_five_unique_games() {
        assertEquals(5, portfolioGames.size)
        assertEquals(portfolioGames.size, portfolioGames.map { it.id }.toSet().size)
        assertTrue(portfolioGames.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun every_catalog_game_has_a_playable_native_vertical_slice() {
        val playable = portfolioGames.filter { it.nativeStatus == NativeStatus.PLAYABLE }

        assertEquals(portfolioGames.map { it.id }, playable.map { it.id })
    }
}
