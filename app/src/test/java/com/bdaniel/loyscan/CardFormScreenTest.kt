package com.bdaniel.loyscan

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakePhotoCamera
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.writeOnePixelPng
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.PHOTO_CROP_TAG
import com.bdaniel.loyscan.ui.PhotoCamera
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CardFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Example rule: 8 digits shown in two groups, the last one a Luhn check digit (12345674 is valid).
    private val rule = CardNumberRule(
        id = "example",
        format = BarcodeFormat.CODE_128,
        minLength = 8,
        maxLength = 8,
        checkDigit = CardNumberRule.CheckDigit(CheckDigitAlgorithm.LUHN, CardNumberRule.CheckDigit.Mode.VERIFY),
        display = CardNumberRule.Display(pattern = "#### ####"),
    )
    private val store = aStore(id = "ruled", name = "Ruled Store", color = "#112233", cardNumberRule = rule)
    // The same rule, but the number must start with 9.
    private val nineStore = aStore(id = "nine", name = "Nine Store", cardNumberRule = rule.copy(coreStartsWith = listOf("9")))
    private val cards = FakeLoyaltyCardRepository()
    private val photos = FakeCardPhotos()
    private val stores = FakeStoreRepository(
        listOf(
            store,
            aStore(id = "plain", name = "Plain Store"),
            nineStore,
            aStore(id = "ean", name = "Ean Store", defaultBarcodeFormat = BarcodeFormat.EAN_13),
        )
    )
    private val opened = mutableListOf<Pair<String, String>>()
    private var backs = 0

    @Test
    fun showsTheStore_andAnEmptyNumberCantBeSaved() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithText("Ruled Store").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun aValidNumber_showsHowTheCardWillShowIt_andCanBeSaved() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345674")

        composeRule.onNodeWithText("Shown as 1234 5674").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun aWrongCheckDigit_saysSo_andOffersSaveAnyway() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345675")

        composeRule.onNodeWithText("The check digit doesn't match. Check the number for a typo.").assertIsDisplayed()
        composeRule.onNodeWithText("Save anyway").assertIsEnabled()
    }

    @Test
    fun aNumberTheStoresBarcodeCantHold_saysSo_andOffersSaveAnyway() { // #47 review
        setScreen(CardForm("ean"))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("1234")

        composeRule.onNodeWithText("This isn't a valid EAN-13 number.").assertIsDisplayed()
        composeRule.onNodeWithText("Save anyway").assertIsEnabled()
    }

    @Test
    fun aNumberOneDigitShortOfTheStoresBarcode_isNotCalledUndrawable() { // #47 review, round 3: ZXing would draw it
        setScreen(CardForm("ean"))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("590123412345")

        composeRule.onNodeWithText("This isn't a valid EAN-13 number.").assertIsDisplayed()
    }

    @Test
    fun aScanAnotherStoresRuleReads_offersThatStore_andTappingItMakesTheFormThatStores() { // #6
        setScreen(CardForm("nine", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128))
        composeRule.onNodeWithText("This barcode doesn't look like a Nine Store card.").assertIsDisplayed()
        composeRule.onNodeWithText("It looks like a card from:").assertIsDisplayed()

        composeRule.onNodeWithText("Ruled Store").performScrollTo().performClick()

        composeRule.onNodeWithText("Nine Store").assertDoesNotExist()
        composeRule.onNodeWithText("It looks like a card from:").assertDoesNotExist()
        composeRule.onNodeWithText("Ruled Store").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun aScanForAStoreWithoutARule_offersTheStoresWhoseRuleReadsIt_withoutAProblem() {
        setScreen(CardForm("plain", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128))

        composeRule.onNodeWithText("It looks like a card from:").assertIsDisplayed()
        composeRule.onNodeWithText("Ruled Store").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun aScanTheStoreDoesNotUse_saysSo_andOffersSaveAnyway() {
        setScreen(CardForm("ruled", scannedContent = "ABC", scannedFormat = BarcodeFormat.QR_CODE))

        composeRule.onNodeWithText("This barcode doesn't look like a Ruled Store card.").assertIsDisplayed()
        composeRule.onNodeWithText("It looks like a card from:").assertDoesNotExist() // no other store reads it
        composeRule.onNodeWithText("Save anyway").assertIsEnabled()
    }

    @Test
    fun aScan_showsItsNumberAndFormat() {
        setScreen(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.EAN_13))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).assertTextContains("1234 5674")
        composeRule.onNodeWithText("Scanned · EAN-13").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aScannedQrCode_namesItsFormatInHungarian() { // #72; the line's prefix moves with the form
        setScreen(CardForm("plain", scannedContent = "ABC-123", scannedFormat = BarcodeFormat.QR_CODE))

        composeRule.onNodeWithText("Scanned · QR-kód").assertIsDisplayed()
    }

    @Test
    fun aScanShownAsPrinted_isReadOnly_andCanBeTypedInstead() {
        setScreen(CardForm("plain", scannedContent = "0109501101530003" + "\u001D" + "10ABC", scannedFormat = BarcodeFormat.GS1_128))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG)
            .assertTextContains("(01)09501101530003(10)ABC")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))

        composeRule.onNodeWithText("Type the number instead").performClick()
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("777")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).assertTextContains("777")
        composeRule.onNodeWithText("Type the number instead").assertDoesNotExist()
    }

    @Test
    fun saving_storesTheCard_andOpensIt() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithTag(CARD_LABEL_TAG).performTextInput("Gold card")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345674")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.waitForIdle()
        val saved = cards.cards.value.single()
        assertThat(saved.label).isEqualTo("Gold card")
        assertThat(opened).containsExactly(saved.id to "#112233")
    }

    @Test
    fun aFailedSave_saysSo_andStaysOpen() { // #49
        cards.addFailure = IllegalStateException("insert failed")
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345674")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.onNodeWithText("The card couldn't be saved. Try again.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsEnabled()
        assertThat(opened).isEmpty()
    }

    @Test
    fun editingACard_saysSo_andSavingOpensTheCard() { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674"))
        setScreen(CardForm("ruled", cardId = cardId(1)))

        composeRule.onNodeWithText("Edit card").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).assertTextContains("Gold card")
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345682")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertThat(cards.cards.value.single().number).isEqualTo("12345682")
        assertThat(opened).containsExactly(cardId(1) to "#112233")
    }

    @Test
    fun editingACard_showsItsNote_andSavesTheChangedNote() { // #9
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", note = "Expires in May"))
        setScreen(CardForm("ruled", cardId = cardId(1)))

        composeRule.onNodeWithTag(CARD_NOTE_TAG).performScrollTo().assertTextContains("Expires in May")
        composeRule.onNodeWithTag(CARD_NOTE_TAG).performTextClearance()
        composeRule.onNodeWithTag(CARD_NOTE_TAG).performTextInput("PIN hint: birthday")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertThat(cards.cards.value.single().note).isEqualTo("PIN hint: birthday")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_theStoreChooser_isTranslated() { // #72; the form behind it is translated in its own slice
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674"))
        setScreen(CardForm("ruled", cardId = cardId(1)))
        val shown = ShownTexts(composeRule)

        composeRule.onNodeWithText("Change store").performClick()
        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")
        shown.collect()

        shown.assertSeen("Válassz boltot", "Bezárás", "Nincs ilyen bolt: „xyz”")
    }

    @Test
    fun editingACard_canMoveItToAnotherStore_foundBySearch() { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674"))
        setScreen(CardForm("ruled", cardId = cardId(1)))

        composeRule.onNodeWithText("Change store").performClick()
        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("nine")
        composeRule.onNodeWithText("Plain Store").assertDoesNotExist()
        composeRule.onNodeWithText("Nine Store").performClick()

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Ruled Store").assertDoesNotExist()
        composeRule.onNodeWithText("Nine Store card numbers don't start like this.").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_LABEL_TAG).assertTextContains("Gold card")
        composeRule.onNodeWithText("Save anyway").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertThat(cards.cards.value.single().store.id).isEqualTo("nine")
        assertThat(opened).containsExactly(cardId(1) to nineStore.color)
    }

    @Test
    fun theStoreChooser_canBeClosedWithoutChangingTheStore() { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674"))
        setScreen(CardForm("ruled", cardId = cardId(1)))

        composeRule.onNodeWithText("Change store").performClick()
        composeRule.onNodeWithText("Plain Store").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()

        composeRule.onNodeWithText("Plain Store").assertDoesNotExist()
        composeRule.onNodeWithText("Ruled Store").assertIsDisplayed()
    }

    @Test
    fun addingACard_saysAddCard_andDoesNotOfferToChangeTheStore() { // #8: back returns to the picker
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithText("Add card").assertIsDisplayed()
        composeRule.onNodeWithText("Change store").assertDoesNotExist()
    }

    @Test
    fun back_goesBack() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun aStoreRemovedWhileTheFormIsOpen_saysItIsNoLongerAvailable_withoutSave() { // #51
        setScreen(CardForm("plain"))
        composeRule.onNodeWithText("Plain Store").assertIsDisplayed()

        stores.stores.value = stores.stores.value.filterNot { it.id == "plain" }

        composeRule.onNodeWithText("This store is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).assertDoesNotExist()
    }

    @Test
    fun addingAFrontPhoto_picksAPicture_showsIt_andSavesItWithTheCard() { // #10
        setScreen(CardForm("ruled"))
        composeRule.onNodeWithTag(CARD_NUMBER_TAG).performTextInput("12345674")

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        // The gallery picks a picture; the crop screen opens on it, and framing it stores the photo.
        composeRule.onNodeWithText("Crop").performClick()

        composeRule.onNodeWithContentDescription("Front photo").assertExists()
        composeRule.onNodeWithText("Add front photo").assertDoesNotExist()
        composeRule.onNodeWithText("Add back photo").assertExists()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertThat(cards.cards.value.single().frontPhotoPath).isEqualTo("photo-1")
    }

    @Test
    fun theCropScreen_framesAPhoto_toTheCardRatio() { // #10: the crop screen shows the picture and stores the framed part
        // A real staged file, so the crop screen's picture loads and Crop computes a frame of it.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("staged-1").writeOnePixelPng()
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        val loader = EntryPointAccessors.fromApplication(context, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
        composeRule.onNodeWithText("Crop").performClick()

        composeRule.onNodeWithContentDescription("Front photo").assertExists()
        assertThat(photos.cropped.single().first).isEqualTo("staged-1")
    }

    @Test
    fun theCropScreen_zoomAndPan_storeACardRatioPartOfThePicture() { // #10
        // Exercises the gesture → crop path: a zoom and a pan, then Crop, store a framed part. This does not reproduce
        // the device-only stale-size bug (in Robolectric the picture loads before the gesture starts), so it isn't a
        // guard for it; the keyed `pointerInput` fix for that is reasoned, not checked here. See the decision page.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("staged-1").writeOnePixelPng()
        setScreen(CardForm("ruled"))
        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        val loader = EntryPointAccessors.fromApplication(context, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }

        // Zoom in (two fingers spread), then drag far past the edge.
        composeRule.onNodeWithTag(PHOTO_CROP_TAG).performTouchInput {
            down(0, center - Offset(20f, 0f))
            down(1, center + Offset(20f, 0f))
            moveTo(0, center - Offset(160f, 0f))
            moveTo(1, center + Offset(160f, 0f))
            up(0)
            up(1)
        }
        composeRule.onNodeWithTag(PHOTO_CROP_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(2000f, 2000f))
            up()
        }
        composeRule.onNodeWithText("Crop").performClick()

        val crop = photos.cropped.single().second
        assertThat(crop.right - crop.left).isLessThan(1f) // zoomed in, so less than the whole width is kept
        val ratio = (crop.right - crop.left) / (crop.bottom - crop.top) // the 1×1 picture makes this the crop's own ratio
        assertThat(ratio).isWithin(0.03f).of(CARD_ASPECT_RATIO) // the pan stayed in the picture, so the frame kept its ratio
    }

    @Test
    fun theCropScreen_canBeCancelled_withoutAddingAPhoto() { // #10
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Add front photo").assertExists()
        composeRule.onNodeWithContentDescription("Front photo").assertDoesNotExist()
        assertThat(photos.stored).isEmpty()
    }

    @Test
    fun editingACardWhosePhotoFileIsMissing_offersToAddThatPhoto() { // #87: a cloud restore brings the row, not the file
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", backPhotoPath = "lost"))
        setScreen(CardForm("ruled", cardId = cardId(1)))

        composeRule.onNodeWithText("Add back photo").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("Back photo").assertDoesNotExist()
    }

    @Test
    fun editingACard_showsItsPhoto_whichCanBeReplacedOrRemoved() { // #10
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", backPhotoPath = "back"))
        photos.stored += "back"
        setScreen(CardForm("ruled", cardId = cardId(1)))
        composeRule.onNodeWithContentDescription("Back photo").assertExists()

        composeRule.onNodeWithContentDescription("Replace back photo").performScrollTo().performClick()
        // The gallery picks a replacement; the crop screen opens on it, and framing it replaces the photo.
        composeRule.onNodeWithText("Crop").performClick()
        assertThat(photos.staged).containsExactly(PICKED)
        composeRule.onNodeWithContentDescription("Back photo").assertExists()

        composeRule.onNodeWithContentDescription("Remove back photo").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Back photo").assertDoesNotExist()
        composeRule.onNodeWithText("Add back photo").assertExists()
    }

    @Test
    fun aPictureThatCantBeCopiedIn_saysSo() { // #10
        photos.importFailure = FileNotFoundException("gone")
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()

        composeRule.onNodeWithText("The photo couldn't be added. Try again.").assertExists()
        composeRule.onNodeWithText("Add front photo").assertExists()
    }

    @Test
    fun withACameraApp_addingAPhoto_offersTheCameraOrTheGallery() { // #10
        setScreen(CardForm("ruled"), FakePhotoCamera())

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Take photo").assertIsDisplayed()
        composeRule.onNodeWithText("Choose from gallery").performClick()
        composeRule.onNodeWithText("Use whole photo").performClick()

        assertThat(photos.staged).containsExactly(PICKED)
        composeRule.onNodeWithContentDescription("Front photo").assertExists()
    }

    @Test
    fun withoutACameraApp_addingAPhoto_opensTheGalleryAtOnce() { // #10
        setScreen(CardForm("ruled"), FakePhotoCamera(available = false))

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()

        composeRule.onNodeWithText("Take photo").assertDoesNotExist()
        assertThat(photos.staged).containsExactly(PICKED) // the gallery opened at once and staged the pick for the crop screen
    }

    @Test
    fun takingAPhoto_opensTheCameraAppOnACapture_andShowsThePhotoTaken() { // #10
        val camera = FakePhotoCamera()
        setScreen(CardForm("ruled"), camera)

        composeRule.onNodeWithText("Add back photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Take photo").performClick()
        // The camera app takes a photo; the crop screen opens on it, and framing it stores the photo.
        composeRule.onNodeWithText("Use whole photo").performClick()

        assertThat(camera.taken).containsExactly(photos.captures.single().uri)
        composeRule.onNodeWithContentDescription("Back photo").assertExists()
        assertThat(photos.stored).containsExactly("photo-1")
    }

    @Test
    fun replacingAPhoto_offersTheCameraToo() { // #10
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", frontPhotoPath = "front"))
        photos.stored += "front"
        val camera = FakePhotoCamera()
        setScreen(CardForm("ruled", cardId = cardId(1)), camera)

        composeRule.onNodeWithContentDescription("Replace front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Take photo").performClick()
        composeRule.onNodeWithText("Use whole photo").performClick()

        assertThat(camera.taken).hasSize(1)
        assertThat(photos.cropped).containsExactly(photos.captures.single().name to PhotoCrop.WHOLE)
    }

    @Test
    fun aCameraDenied_saysWhyItIsNeeded_untilTheNextPhoto() { // #10
        val camera = FakePhotoCamera().apply { answer = PhotoCamera.Result.DENIED }
        setScreen(CardForm("ruled"), camera)

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Take photo").performClick()
        composeRule.onNodeWithText(CAMERA_NEEDED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").assertDoesNotExist()
        assertThat(photos.stored).isEmpty()

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Choose from gallery").performClick()
        composeRule.onNodeWithText(CAMERA_NEEDED).assertDoesNotExist()
    }

    @Test
    fun aCameraDeniedForGood_offersTheSettings() { // #10
        val camera = FakePhotoCamera().apply { answer = PhotoCamera.Result.DENIED_PERMANENTLY }
        setScreen(CardForm("ruled"), camera)

        composeRule.onNodeWithText("Add front photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Take photo").performClick()
        composeRule.onNodeWithText(CAMERA_TURNED_OFF).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").performScrollTo().performClick()

        assertThat(camera.settingsOpened).isEqualTo(1)
    }

    private fun setScreen(route: CardForm, camera: FakePhotoCamera = FakePhotoCamera(available = false)) {
        val viewModel = CardFormViewModel(
            SavedStateHandle(route = route),
            stores,
            cards,
            Clock.fixed(SOME_INSTANT, ZoneOffset.UTC),
            photos,
        )
        val directions = CardFormDirections(toCard = { id, color -> opened += id to color }, toBack = { backs++ })
        composeRule.setContent {
            CardFormScreen(
                directions = directions,
                viewModel = viewModel,
                // The system picker, answered at once with PICKED.
                photoPicker = { onPicked -> PicturePicker { onPicked(PICKED) } },
                photoCamera = { onResult -> camera.also { it.onResult = onResult } },
            )
        }
    }

    private companion object {
        val PICKED: Uri = Uri.parse("content://media/picker/0/1")
        const val CAMERA_NEEDED = "LoyScan needs the camera to take a photo of your card."
        const val CAMERA_TURNED_OFF = "Camera access is turned off for LoyScan. You can allow it in the app's settings."
    }
}
