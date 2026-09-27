package dev.smallwins.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SmallWinsRootTest {
    @Test
    fun catalog_shows_all_games_and_native_statuses() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithText("Small Wins").assertIsDisplayed()
        onNodeWithTag("game-purrdoku").assertIsDisplayed()
        onNodeWithTag("game-pipes").assertIsDisplayed()
    }

    @Test
    fun tutorial_can_be_solved_and_next_level_opened() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithTag("game-pipes").performClick()
        onNodeWithTag("pipes-screen-tutorial").assertIsDisplayed()
        onNodeWithText("First flow").assertIsDisplayed()

        onNodeWithTag("pipe-0").performClick()
        onNodeWithText("Everything connects.").assertIsDisplayed()
        onNodeWithTag("next-level").performClick()

        onNodeWithTag("pipes-screen-level-1").assertIsDisplayed()
        onNodeWithText("Around the bend").assertIsDisplayed()
    }

    @Test
    fun back_button_returns_to_catalog() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithTag("game-pipes").performClick()
        onNodeWithTag("back-to-catalog").performClick()

        onNodeWithText("A cross-platform puzzle portfolio").assertIsDisplayed()
        onNodeWithTag("game-pipes").assertIsDisplayed()
    }
}
