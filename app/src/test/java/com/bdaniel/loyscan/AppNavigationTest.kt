package com.bdaniel.loyscan

import com.bdaniel.loyscan.ui.CARD_TILE_PHOTO_TAG
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.STORE_ID
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.writeOnePixelPng
import com.google.common.truth.Truth.assertThat
import coil.ImageLoader
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
import org.robolectric.shadows.ShadowLooper

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

    @Inject
    lateinit var photos: FileCardPhotos

    @Inject
    lateinit var imageLoader: ImageLoader

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
    fun onAPhone_onlyTheCardRotates() { // #63
        waitForText("Gold card")
        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_PORTRAIT)

        composeRule.onNodeWithText("Gold card").performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { requestedOrientation == SCREEN_ORIENTATION_UNSPECIFIED }

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { requestedOrientation == SCREEN_ORIENTATION_PORTRAIT }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Choose store")
        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun about_opensFromTheList_saysLoyScanIsNotAffiliated_andBackReturnsToTheList() { // #150
        waitForText("Gold card")
        composeRule.onNodeWithContentDescription("About").performClick()
        waitForText("Version ${BuildConfig.VERSION_NAME}")

        composeRule.onNodeWithText("not affiliated with or endorsed by the retailers", substring = true).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForTextToGo("Version ${BuildConfig.VERSION_NAME}")
        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
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

        waitForTheCardFormToClose()
        waitForText("Silver card")
        composeRule.onNodeWithText("777").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        // Both tiles: each is described as its store and label.
        assertThat(composeRule.onAllNodesWithContentDescription("Test Store, ", substring = true).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun addingACard_offersAnother_whichOpensThePicker_andBackReturnsToTheNewCard() { // #6
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithText("Test Store").performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("Card number")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("777")
        composeRule.onNodeWithText("Save").performClick()
        waitForTheCardFormToClose()

        waitForText("Add another")
        composeRule.onNodeWithText("Add another").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText("Choose store")).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText("777").assertIsDisplayed()
        composeRule.onNodeWithText("Card added").assertDoesNotExist() // offered once
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

        waitForTheCardFormToClose()
        waitForText("4242")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        composeRule.onNodeWithContentDescription("My Shop").assertIsDisplayed() // the tile, read as its store (#183)
    }

    @Test
    fun editingACardsLabel_savesIt_showsTheCard_andBackReturnsToTheList() { // #8
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        waitForText("0042")

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForTheCardFormToLoad()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Silver card")
        composeRule.onNodeWithText("Save").performClick()

        waitForTheCardFormToClose()
        waitForText("Silver card")
        composeRule.onNodeWithText("0042").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.single().loyaltyCard.label).isEqualTo("Silver card")
    }

    @Test
    fun aNoteWrittenInTheCardForm_isShownOnTheCard_andKeptByTheNextEdit() { // #9
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        waitForText("0042")

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForTheCardFormToLoad()
        composeRule.onNodeWithTag(CARD_NOTE_TAG).performScrollTo().performTextInput("PIN hint: birthday")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        waitForTheCardFormToClose()
        waitForText("PIN hint: birthday")

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForTheCardFormToLoad()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Silver card")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        waitForTheCardFormToClose()
        waitForText("Silver card")

        composeRule.onNodeWithTag(CARD_NOTE_TAG).performScrollTo().assertTextEquals("PIN hint: birthday")
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.single().loyaltyCard.note)
            .isEqualTo("PIN hint: birthday")
    }

    @Test
    fun movingACardToAnotherStore_savesIt_andTheCardShowsThatStore() { // #8
        runBlocking { db.storeDao().insert(aStoreEntity(id = "other", name = "Other Store")) }
        waitForText("Gold card")
        composeRule.onNodeWithText("Gold card").performClick()
        waitForText("0042")

        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForText("Change store")
        composeRule.onNodeWithText("Change store").performClick()
        waitForText("Other Store")
        composeRule.onNodeWithText("Other Store").performClick()
        composeRule.onNodeWithText("Save").performClick()

        waitForTheCardFormToClose()
        waitForText("Other Store")
        composeRule.onNodeWithText("Test Store").assertDoesNotExist()
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.single().loyaltyCard.storeId).isEqualTo("other")
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
    fun aCardsPhoto_isShownOnTheCard() { // #10
        storedPhoto("front")
        runBlocking { db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), label = "Photo card", frontPhotoPath = "front")) }
        waitForText("Photo card")

        composeRule.onNodeWithText("Photo card").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription("Front photo")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aCardsFrontPhoto_isLoadedOntoItsTile_byTheAppsImageLoader() { // #10
        storedPhoto("front")
        runBlocking { db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), label = "Photo card", frontPhotoPath = "front")) }

        // Only the app's loader can map a card photo to its file; Coil's default one would fail it and cache nothing.
        waitForThePhotoToLoad()
        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun deletingACardFromTheList_deletesItsPhotoFiles() { // #10
        val photo = storedPhoto("front")
        runBlocking { db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), label = "Photo card", frontPhotoPath = "front")) }
        waitForText("Photo card")
        // Windows can't delete a file the loader is still reading: the tile's load has to be done first.
        waitForThePhotoToLoad()

        composeRule.onNodeWithText("Photo card").performTouchInput { longClick() }
        waitForText("1 selected")
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText("Photo card")).fetchSemanticsNodes().isEmpty() }
        // The file goes once the repository resumes after the rows' delete, on the main looper.
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); !photo.exists() }
    }

    @Test
    fun selectingCardsOnTheList_andDeletingThem_keepsTheOthers() { // #7: multi-select
        runBlocking {
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), label = "Silver card"))
            db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(3), label = "Bronze card"))
        }
        waitForText("Bronze card")

        composeRule.onNodeWithText("Gold card").performTouchInput { longClick() }
        composeRule.onNodeWithText("Bronze card").performClick()
        waitForText("2 selected")
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        waitForText("My Cards")
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText("Gold card")).fetchSemanticsNodes().isEmpty() }
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.map { it.loyaltyCard.label }).containsExactly("Silver card")
    }

    @Test
    fun editingTheOneSelectedCard_fromTheList_savesIt_andShowsTheCard() { // #7
        waitForText("Gold card")

        composeRule.onNodeWithText("Gold card").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Edit").performClick()
        waitForTheCardFormToLoad()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Silver card")
        composeRule.onNodeWithText("Save").performClick()

        waitForTheCardFormToClose()
        waitForText("0042")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("My Cards")
        composeRule.onNodeWithText("Silver card").assertIsDisplayed()
        composeRule.onNodeWithText("1 selected").assertDoesNotExist()
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
        waitForTheStoreFormToLoad()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextClearance()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Gym")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        waitForTheStoreFormToClose()
        waitForText("Choose store")
        waitForText("My Gym") // the picker's row can recompose after its title shows (#118)
        composeRule.onNodeWithText("My Gym").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForTextToGo("Choose store")
        waitForTile("My Gym")
        waitForTileToGo("My Shop") // #62
    }

    @Test
    fun aCatalogueStoresLogoScreen_opensFromThePicker_andBackReturnsToIt() { // #151
        runBlocking { db.storeDao().insert(aStoreEntity(id = "hu-shop", name = "Shop")) }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Shop")

        composeRule.onNodeWithContentDescription("Logo of Shop").performClick()
        waitForText("Store logo")
        waitForText("Every card of this store shows this logo.")
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForTextToGo("Store logo")
        waitForText("Choose store")
    }

    @Test
    fun aCustomStoresLogoScreen_opensFromItsForm_andBackReturnsToTheFormAsLeft() { // #151
        runBlocking { db.storeDao().insert(aStoreEntity(id = "custom", name = "My Shop", isCustom = true)) }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("My Shop")
        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()
        waitForTheStoreFormToLoad()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextReplacement("My Shop 2") // not saved

        composeRule.onNodeWithText("Change logo").performScrollTo().performClick()
        waitForText("Every card of this store shows this logo.")
        composeRule.onNodeWithText("Store logo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForTextToGo("Store logo")
        waitForTheStoreFormToLoad()
        composeRule.onNodeWithText("Edit store").assertIsDisplayed()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).assert(hasText("My Shop 2"))
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
        waitForTheStoreFormToLoad()
        // 4242 isn't an EAN-13, so the card is now drawn in the fallback and the detail says so.
        composeRule.onNodeWithText(BarcodeFormat.EAN_13.displayName).performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        waitForTheStoreFormToClose()
        waitForText("Choose store")
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForText("Gym card")
        composeRule.onNodeWithText("Gym card").performClick()
        waitForText("Shown as ${BarcodeFormat.CODE_128.displayName}")
        composeRule.onNodeWithText("Shown as ${BarcodeFormat.CODE_128.displayName}").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aCustomStoresNewColour_isShownOnItsOpenCard_onReturnFromThePicker() { // #226
        runBlocking { db.storeDao().insert(aStoreEntity(id = "custom", name = "My Shop", color = "#1E5AA8", isCustom = true)) }
        composeRule.onNodeWithContentDescription("Add card").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithText("My Shop").performClick()
        waitForText("Enter manually")
        composeRule.onNodeWithText("Enter manually").performClick()
        waitForText("Card number")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("777")
        composeRule.onNodeWithText("Save").performClick()
        waitForTheCardFormToClose()

        // The card's "Add another" leaves it on the back stack under the picker.
        waitForText("Add another")
        composeRule.onNodeWithText("Add another").performClick()
        waitForText("Choose store")
        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()
        waitForTheStoreFormToLoad()
        composeRule.onNodeWithContentDescription("Orange").performScrollTo().performClick() // #EF6C00
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        waitForTheStoreFormToClose()
        waitForText("Choose store")
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitForTextToGo("Choose store")
        waitForText("777")
        composeRule.waitForIdle()
        val header = composeRule.onNodeWithTag(STORE_HEADER_TAG).captureToImage().toPixelMap()
        assertThat(header[4, 4].toArgb()).isEqualTo(0xFFEF6C00.toInt()) // the corner, clear of the store's name
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
        waitForTheStoreFormToLoad()
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Its card is deleted with it.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        waitForTheStoreFormToClose()
        waitForText("Choose store")
        waitForTextToGo("My Shop")
        assertThat(runBlocking { db.loyaltyCardDao().getAllWithStore().first() }.map { it.loyaltyCard.storeId })
            .containsExactly(STORE_ID)
    }

    /**
     * The card form shows "Edit card" as soon as it knows it is editing, before the card has loaded and its fields are
     * drawn, so typing right after the title can find no field (#123). Only the loaded form has the label field.
     */
    private fun waitForTheCardFormToLoad() = waitForTag(CARD_LABEL_TAG)

    /** As [waitForTheCardFormToLoad], for the custom store form: "Edit store" shows before the store has loaded. */
    private fun waitForTheStoreFormToLoad() = waitForTag(CUSTOM_STORE_NAME_TAG)

    private fun waitForTag(tag: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    /**
     * The form's own fields hold the label and number the card screen then shows, so waiting for those can pass on the
     * form. Back pressed there while the save runs leaves the form, and the save then opens the card over the list.
     */
    private fun waitForTheCardFormToClose() =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText("Card number")).fetchSemanticsNodes().isEmpty() }

    /**
     * As [waitForTheCardFormToClose], for the custom store form: its field holds the store's name. Keyed on the title,
     * which stays until the form leaves; the fields go as soon as the store is deleted.
     */
    private fun waitForTheStoreFormToClose() = waitForTextToGo("Edit store")

    private fun waitForText(text: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** A screen that is leaving can still show [text] for a while, so asserting it is gone at once is racy (#62). */
    private fun waitForTextToGo(text: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

    /** A list tile is read as its store's name, and an unlabelled card's tile draws no text (#183). */
    private fun waitForTile(store: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasContentDescription(store)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTileToGo(store: String) =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(hasContentDescription(store)).fetchSemanticsNodes().isEmpty() }

    /** Waits until the app's image loader has decoded a photo and closed its file. Its result comes on the main looper. */
    private fun waitForThePhotoToLoad() =
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (imageLoader.memoryCache?.size ?: 0) > 0 }

    /** A photo file where the app keeps card photos, named [name]: a real 1×1 PNG, so the card screen can show it. */
    private fun storedPhoto(name: String) = photos.file(name).writeOnePixelPng()

    private val requestedOrientation get() = composeRule.activity.requestedOrientation

    private val brightness get() = composeRule.activity.window.attributes.screenBrightness
}
