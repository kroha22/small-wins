package dev.smallwins.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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
        onNodeWithTag("level-tutorial").performClick()
        onNodeWithTag("pipes-screen-tutorial").assertIsDisplayed()
        onNodeWithText("First flow").assertIsDisplayed()

        onNodeWithTag("pipe-0").performClick()
        onNodeWithText("Everything connects.").performScrollTo().assertIsDisplayed()
        onNodeWithTag("next-level").performScrollTo().performClick()

        onNodeWithTag("pipes-screen-level-1").assertIsDisplayed()
        onNodeWithText("Around the bend").assertIsDisplayed()
    }

    @Test
    fun back_button_returns_to_catalog() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithTag("game-pipes").performClick()
        onNodeWithTag("level-tutorial").performClick()
        onNodeWithTag("back-to-levels").performClick()
        onNodeWithText("Seven web levels, shared natively on Android and iOS.").assertIsDisplayed()
        onNodeWithText("Back").performClick()

        onNodeWithText("A cross-platform puzzle portfolio").assertIsDisplayed()
        onNodeWithTag("game-pipes").assertIsDisplayed()
    }

    @Test
    fun every_native_game_card_opens_its_shared_screen() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        listOf(
            "purrdoku" to "Purrdoku",
            "pipes" to "Burrow Network",
            "untangle" to "Untangle",
            "waypoints" to "Waypoints",
            "shikaku" to "Shikaku",
        ).forEachIndexed { index, (id, title) ->
            onNodeWithTag("game-catalog").performScrollToIndex(if (index == 0) 1 else index + 2)
            onNodeWithTag("game-$id").performClick()
            onNodeWithTag("level-tutorial").performClick()
            onNodeWithText(title).assertIsDisplayed()
            onNodeWithText("Back").performClick()
            onNodeWithText("Back").performClick()
        }
    }

    @Test
    fun level_catalog_can_open_a_deterministic_generated_level() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithTag("game-pipes").performClick()
        onNodeWithTag("level-catalog").performScrollToIndex(8)
        onNodeWithTag("generate-level").performClick()

        onNodeWithText("Fresh network").assertIsDisplayed()
    }

    @Test
    fun settings_expose_motion_and_clear_progress_controls() = runComposeUiTest {
        setContent { SmallWinsRoot() }

        onNodeWithTag("settings").performClick()
        onNodeWithText("Your puzzles stay on this device. No account is needed.").assertIsDisplayed()
        onNodeWithTag("reduce-motion").assertIsDisplayed()
        onNodeWithTag("clear-progress").performClick()
        onNodeWithTag("confirm-clear-progress").assertIsDisplayed()
    }

}
