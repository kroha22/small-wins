package dev.smallwins.app

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun startWithEmptyLocalState() {
        composeRule.activity
            .getSharedPreferences("small-wins", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun catalogShowsAllFiveGamesOnDevice() {
        composeRule.onNodeWithText("Small Wins").assertIsDisplayed()

        listOf(
            "purrdoku",
            "pipes",
            "untangle",
            "waypoints",
            "shikaku",
        ).forEachIndexed { index, gameId ->
            composeRule.onNodeWithTag("game-catalog").performScrollToIndex(index + 1)
            composeRule.onNodeWithTag("game-$gameId").assertIsDisplayed()
        }
    }

    @Test
    fun unfinishedAttemptRestoresAfterActivityRecreation() {
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").performClick()
        composeRule.onNodeWithTag("level-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("level-level-1").performClick()

        composeRule.onNodeWithText("Around the bend").assertIsDisplayed()
        composeRule.onNodeWithText("3 / 9 connected").assertIsDisplayed()
        composeRule.onNodeWithTag("pipe-0").performClick()
        composeRule.onNodeWithText("1 / 9 connected").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Around the bend").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 9 connected").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithText("3 / 9 connected").assertIsDisplayed()
    }

    @Test
    fun completedAttemptIsRecordedWithoutAContinueCard() {
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").performClick()
        composeRule.onNodeWithTag("level-tutorial").performClick()

        composeRule.onNodeWithTag("pipe-0").performClick()
        composeRule.onNodeWithText("Everything connects.").performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("back-to-levels").performScrollTo().performClick()
        composeRule.onNodeWithText("DONE").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onAllNodesWithTag("resume-attempt").assertCountEquals(0)
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithText("1 / 7").assertIsDisplayed()
    }

    @Test
    fun clearProgressRemovesCompletionsActiveAttemptAndSettings() {
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").performClick()
        composeRule.onNodeWithTag("level-tutorial").performClick()
        composeRule.onNodeWithTag("pipe-0").performClick()
        composeRule.onNodeWithText("Everything connects.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("back-to-levels").performScrollTo().performClick()

        composeRule.onNodeWithTag("level-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("level-level-1").performClick()
        composeRule.onNodeWithTag("pipe-0").performClick()
        composeRule.onNodeWithTag("back-to-levels").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("resume-attempt").assertIsDisplayed()

        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(0)
        composeRule.onNodeWithTag("settings").performClick()
        composeRule.onNodeWithTag("reduce-motion").performClick()
        composeRule.onNodeWithTag("clear-progress").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-clear-progress").performClick()

        composeRule.onAllNodesWithTag("resume-attempt").assertCountEquals(0)
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").assertTextContains("0 / 7")
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(0)
        composeRule.onNodeWithTag("settings").performClick()
        composeRule.onNodeWithTag("reduce-motion").assertIsOff()
    }

    @Test
    fun systemBackReturnsFromPuzzleToLevelsThenCatalog() {
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").performClick()
        composeRule.onNodeWithTag("level-tutorial").performClick()
        composeRule.onNodeWithText("Burrow Network").assertIsDisplayed()

        dispatchBack()
        composeRule.onNodeWithTag("level-catalog").assertIsDisplayed()

        dispatchBack()
        composeRule.onNodeWithTag("game-catalog").assertIsDisplayed()
    }

    @Test
    fun everyGameOpensItsTutorialOnDevice() {
        listOf(
            Triple("purrdoku", 1, "Purrdoku"),
            Triple("pipes", 2, "Burrow Network"),
            Triple("untangle", 3, "Untangle"),
            Triple("waypoints", 4, "Waypoints"),
            Triple("shikaku", 5, "Shikaku"),
        ).forEach { (gameId, catalogIndex, title) ->
            composeRule.onNodeWithTag("game-catalog").performScrollToIndex(catalogIndex)
            composeRule.onNodeWithTag("game-$gameId").performClick()
            composeRule.onNodeWithTag("level-tutorial").performClick()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            dispatchBack()
            dispatchBack()
        }
    }

    @Test
    fun generatedPuzzleRestoresWithTheSameSeed() {
        composeRule.onNodeWithTag("game-catalog").performScrollToIndex(2)
        composeRule.onNodeWithTag("game-pipes").performClick()
        composeRule.onNodeWithTag("level-catalog").performScrollToIndex(8)
        composeRule.onNodeWithTag("generate-level").performClick()

        composeRule.onNodeWithText("Fresh network").assertIsDisplayed()
        composeRule.onNodeWithTag("pipe-0").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Fresh network").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performScrollTo().assertIsEnabled()
    }

    private fun dispatchBack() {
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
