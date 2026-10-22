package com.bdaniel.loyscan

import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Smoke test of the wired-up app: `MainActivity` with real Hilt, navigation, ViewModels and Room (in memory, see
 * `InMemoryDatabaseModule`). Screen details belong in the per-screen tests.
 */
@HiltAndroidTest
// A typical phone: on Robolectric's default 320x470dp screen the detail's number is below the fold.
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            db.storeDao().insert(aStoreEntity(name = "Test Store", logo = null))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(label = "Gold card", number = "0042"))
        }
    }

    @Test
    fun tappingACard_opensItsDetail_andBackReturnsToTheList() {
        composeRule.waitUntil { composeRule.onAllNodes(hasText("Test Store")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Test Store").performClick()

        composeRule.waitUntil { composeRule.onAllNodes(hasText("Gold card")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("0042").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        composeRule.onNodeWithText("Gold card").assertDoesNotExist()
    }

    @Test
    fun detailsFullBrightness_isRestoredOnBack() {
        composeRule.waitUntil { composeRule.onAllNodes(hasText("Test Store")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Test Store").performClick()
        // The destination is resumed, and brightens, only once the enter transition has finished.
        composeRule.waitUntil(5_000) { brightness == BRIGHTNESS_OVERRIDE_FULL }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun addCard_opensTheStorePicker_pickingAStoreOpensTheScanner_andBackReturnsToThePicker() {
        composeRule.onNodeWithContentDescription("Add card").performClick()
        composeRule.waitUntil { composeRule.onAllNodes(hasText("Choose store")).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("Test Store").performClick()
        composeRule.waitUntil { composeRule.onAllNodes(hasText("Scan card")).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil { composeRule.onAllNodes(hasText("Choose store")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Scan card").assertDoesNotExist()
    }

    private val brightness get() = composeRule.activity.window.attributes.screenBrightness
}
