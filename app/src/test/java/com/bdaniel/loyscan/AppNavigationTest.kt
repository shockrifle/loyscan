package com.bdaniel.loyscan

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
}
