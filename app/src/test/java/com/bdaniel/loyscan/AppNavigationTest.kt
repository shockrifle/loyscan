package com.bdaniel.loyscan

import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.STORE_ID
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
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
        // A tile shows the card's label; the number is only on the detail screen.
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()

        waitForText("0042")
        composeRule.onNodeWithText("0042").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertDoesNotExist()
    }

    @Test
    fun detailsFullBrightness_isRestoredOnBack() {
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        // The destination is resumed, and brightens, only once the enter transition has finished.
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { brightness == BRIGHTNESS_OVERRIDE_FULL }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun addCard_opensTheStorePicker_pickingAStoreOpensTheScanner_andBackReturnsToThePicker() {
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Choose store")

        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Scan card")

        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithText("Scan card").assertDoesNotExist()
    }

    @Test
    fun addingACardByHand_savesIt_opensIt_andBackReturnsToTheListWithIt() {
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("Card number")

        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Silver card")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("777")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("Silver card")
        composeRule.onNodeWithText("777").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        // Both tiles: their initials are read out as the store's name.
        assertThat(composeRule.onAllNodesWithContentDescription("Test Store").fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun addingACardForANewCustomStore_createsTheStore_savesTheCard_andTheListShowsIt() { // #6
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Other card")
        composeRule.onNodeWithText("Other card").performClick()
        waitForText("New store")

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Create").performScrollTo().performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("Card number")
        composeRule.onNodeWithText("My Shop").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("4242")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("4242")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        composeRule.onNodeWithText("My Shop").assertIsDisplayed()
    }

    @Test
    fun editingACardsLabel_savesIt_showsTheCard_andBackReturnsToTheList() { // #8
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        waitForText("0042")

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit card")
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Silver card")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("Silver card")
        composeRule.onNodeWithText("0042").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.single().loyaltyCard.label).isEqualTo("Silver card")
    }

    @Test
    fun deletingACard_afterConfirming_returnsToTheListWithoutIt() { // #8
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        waitForText("0042")

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        waitForText("My Cards")
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText("Gold card")).fetchSemanticsNodes().isEmpty() }
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }).isEmpty()
    }

    @Test
    fun editingACustomStore_fromThePicker_renamesItForItsCards() { // #53
        runBlocking {
            db.storeDao().insert(aStoreEntity(id = "custom", name = "My Shop", isCustom = true))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), storeId = "custom", number = "4242"))
        }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("My Shop")

        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()
        waitForText("Edit store")
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextClearance()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Gym")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        waitForText("Choose store")
        composeRule.onNodeWithText("My Gym").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Gym")
        composeRule.onNodeWithText("My Shop").assertDoesNotExist()
    }

    @Test
    fun aCustomStoresNewFormat_redrawsItsTypedCard() { // #53
        runBlocking {
            db.storeDao().insert(aStoreEntity(id = "custom", name = "My Shop", isCustom = true))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), storeId = "custom", label = "Gym card", number = "4242"))
        }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("My Shop")
        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()
        waitForText("Edit store")
        // 4242 isn't an EAN-13, so the card is now drawn in the fallback and the detail says so.
        composeRule.onNodeWithText(BarcodeFormat.EAN_13.displayName).performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        waitForText("Choose store")
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("Gym card")
        composeRule.onNodeWithText("Gym card").performClick()
        waitForText("Shown as ${BarcodeFormat.CODE_128.displayName}")
        composeRule.onNodeWithText("Shown as ${BarcodeFormat.CODE_128.displayName}").assertIsDisplayed()
    }

    @Test
    fun deletingACustomStore_fromThePicker_deletesItsCardsToo() { // #53
        runBlocking {
            db.storeDao().insert(aStoreEntity(id = "custom", name = "My Shop", isCustom = true))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), storeId = "custom", number = "4242"))
        }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("My Shop")

        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()
        waitForText("Edit store")
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Its card is deleted with it.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        waitForText("Choose store")
        composeRule.onNodeWithText("My Shop").assertDoesNotExist()
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.map { it.loyaltyCard.storeId })
            .containsExactly(STORE_ID)
    }

    private fun waitForText(text: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private val brightness get() = composeRule.activity.window.attributes.screenBrightness
}

// waitUntil's 1 s default timed out on a slow CI runner (#56).
private const val WAIT_TIMEOUT_MS = 10_000L
