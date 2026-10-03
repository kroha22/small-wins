package dev.smallwins.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun catalogShowsAllSixGamesOnDevice() {
        composeRule.onNodeWithText("Small Wins").assertIsDisplayed()

        listOf(
            "purrdoku",
            "pipes",
            "untangle",
            "waypoints",
            "shikaku",
            "wildlife-survey",
        ).forEachIndexed { index, gameId ->
            composeRule.onNodeWithTag("game-catalog").performScrollToIndex(index + 1)
            composeRule.onNodeWithTag("game-$gameId").assertIsDisplayed()
        }
    }
}
