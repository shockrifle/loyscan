package com.bdaniel.loyscan

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.WithFontScale
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import com.bdaniel.loyscan.testing.writeOnePixelPng
import com.bdaniel.loyscan.testing.writeWidePng
import com.bdaniel.loyscan.ui.LogoSearch
import com.bdaniel.loyscan.ui.PHOTO_CROP_FRAME_TAG
import com.bdaniel.loyscan.ui.PHOTO_CROP_TAG
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import kotlin.math.abs

/** A store's logo screen (#151): the logo on the brand colour, set from a picture through a square crop, or removed. */
@RunWith(AndroidJUnit4::class)
class StoreLogoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stores = FakeStoreRepository(listOf(aStore(id = "shop", name = "Shop", color = "#336699")))
    private val photos = FakeCardPhotos()
    private val cards = FakeLoyaltyCardRepository()
    private val picked: Uri = Uri.parse("content://media/picker/0/1")
    private val searches = mutableListOf<String>()
    private var backs = 0

    /** The store's name in the preview, not elsewhere on the screen. */
    private val previewName = hasText("Shop") and hasAnyAncestor(hasTestTag(STORE_LOGO_PREVIEW_TAG))

    @Test
    fun withoutALogo_showsTheStoresNameOnItsColour_andOffersNoRemove() {
        setScreen()

        composeRule.onNode(previewName).assertIsDisplayed()
        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Choose image").assertIsDisplayed()
        composeRule.onNodeWithText("Find a logo online").assertIsDisplayed()
        composeRule.onNodeWithText("Remove logo").assertDoesNotExist()
        composeRule.onNodeWithText("Use a card's photo").assertDoesNotExist() // no card of it has a front photo
    }

    @Test
    fun withALogo_showsIt_inPlaceOfTheName_andOffersRemove() {
        givenALogo()
        setScreen()
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_IMAGE_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(previewName).assertDoesNotExist()
        composeRule.onNodeWithText("Remove logo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAnImage_opensASquareCrop_andCropSetsTheLogo() {
        setScreen()

        composeRule.onNodeWithText("Choose image").performClick()
        val frame = composeRule.onNodeWithTag(PHOTO_CROP_FRAME_TAG).fetchSemanticsNode().size
        assertThat(abs(frame.width - frame.height)).isAtMost(1)
        composeRule.onNodeWithText("Crop").performClick()

        assertThat(photos.croppedLogos.map { it.first }).containsExactly("staged-1")
        assertThat(stores.stores.value.single().logo).isEqualTo("logo-1")
        composeRule.onNodeWithTag(PHOTO_CROP_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Remove logo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aPictureInTheRoute_opensTheSquareCrop() { // #151: a picture shared to LoyScan
        photos.stored += "shared"
        val viewModel = StoreLogoViewModel(SavedStateHandle(route = StoreLogo("shop", picture = "shared")), stores, cards, photos)
        composeRule.setContent { StoreLogoScreen(directions(), viewModel, testPicker(), testLogoSearch()) }

        val frame = composeRule.onNodeWithTag(PHOTO_CROP_FRAME_TAG).fetchSemanticsNode().size
        assertThat(abs(frame.width - frame.height)).isAtMost(1)
        composeRule.onNodeWithText("Crop").performClick()

        assertThat(stores.stores.value.single().logo).isEqualTo("logo-1")
    }

    @Test
    fun theCrop_zoomsOut_soAWideLogoFitsTheSquareWhole() {
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("staged-1").writeWidePng()
        setScreen()
        composeRule.onNodeWithText("Choose image").performClick()
        waitForAnImageToLoad()

        // Two fingers pinch together: the picture shrinks until it fits the frame, and no further.
        composeRule.onNodeWithTag(PHOTO_CROP_TAG).performTouchInput {
            down(0, center - Offset(120f, 0f))
            down(1, center + Offset(120f, 0f))
            moveTo(0, center - Offset(10f, 0f))
            moveTo(1, center + Offset(10f, 0f))
            up(0)
            up(1)
        }
        composeRule.onNodeWithText("Crop").performClick()

        val crop = photos.croppedLogos.single().second
        assertThat(crop.left).isWithin(0.01f).of(0f)
        assertThat(crop.right).isWithin(0.01f).of(1f)
        assertThat(crop.top).isWithin(0.01f).of(-0.5f) // a 2:1 picture fitted in a square: the frame is twice its height
        assertThat(crop.bottom).isWithin(0.01f).of(1.5f)
    }

    @Test
    fun theCrop_offersTheWholeImage_andCanBeCancelled() {
        setScreen()

        composeRule.onNodeWithText("Choose image").performClick()
        composeRule.onNodeWithText("Use whole image").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag(PHOTO_CROP_TAG).assertDoesNotExist()
        assertThat(photos.croppedLogos).isEmpty()
        assertThat(stores.stores.value.single().logo).isNull()
    }

    @Test
    fun aCardsPhoto_withOneCard_opensASquareCropOnIt_andCropSetsTheLogo() { // #151
        givenCardPhotos("Mine")
        setScreen()

        composeRule.onNodeWithText("Use a card's photo").performScrollTo().performClick()
        val frame = composeRule.onNodeWithTag(PHOTO_CROP_FRAME_TAG).fetchSemanticsNode().size
        assertThat(abs(frame.width - frame.height)).isAtMost(1)
        composeRule.onNodeWithText("Crop").performClick()

        assertThat(photos.stagedPhotos).containsExactly("front-1")
        assertThat(stores.stores.value.single().logo).isEqualTo("logo-1")
        assertThat(photos.stored).contains("front-1") // the card keeps its photo
    }

    @Test
    fun aCardsPhoto_withSeveralCards_asksWhichCard_andTheChoiceOpensTheCrop() { // #151
        givenCardPhotos("Mine", "Partner's")
        setScreen()

        composeRule.onNodeWithText("Use a card's photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Which card's photo?").assertIsDisplayed()
        composeRule.onNodeWithText("Mine").assertIsDisplayed()
        composeRule.onNodeWithText("Partner's").performClick()
        composeRule.onNodeWithText("Crop").performClick()

        composeRule.onNodeWithText("Which card's photo?").assertDoesNotExist()
        assertThat(photos.stagedPhotos).containsExactly("front-2")
        assertThat(stores.stores.value.single().logo).isEqualTo("logo-1")
    }

    @Test
    fun theWhichCardQuestion_canBeCancelled() { // #151
        givenCardPhotos("Mine", "Partner's")
        setScreen()

        composeRule.onNodeWithText("Use a card's photo").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Which card's photo?").assertDoesNotExist()
        composeRule.onNodeWithTag(PHOTO_CROP_TAG).assertDoesNotExist()
        assertThat(photos.stagedPhotos).isEmpty()
    }

    @Test
    fun theWhichCardQuestion_closesWhenNoCardPhotoIsLeft() { // #151: the cards deleted meanwhile, e.g. from another screen
        givenCardPhotos("Mine", "Partner's")
        setScreen()
        composeRule.onNodeWithText("Use a card's photo").performScrollTo().performClick()

        cards.cards.value = emptyList()

        composeRule.onNodeWithText("Which card's photo?").assertDoesNotExist()
        composeRule.onNodeWithText("Use a card's photo").assertDoesNotExist()
    }

    @Test
    fun theWhichCardQuestion_isStillOpenAfterProcessDeath_whenTheStoreLoadsBeforeTheCards() { // #151 review
        givenCardPhotos("Mine", "Partner's")
        val restoration = StateRestorationTester(composeRule)
        var viewModel = StoreLogoViewModel(SavedStateHandle(route = StoreLogo("shop")), stores, cards, photos)
        restoration.setContent { StoreLogoScreen(directions(), viewModel, testPicker(), testLogoSearch()) }
        composeRule.onNodeWithText("Use a card's photo").performScrollTo().performClick()

        // A new process: a new ViewModel whose cards come after its store.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        cards.loadGate = gate
        viewModel = StoreLogoViewModel(SavedStateHandle(route = StoreLogo("shop")), stores, cards, photos)
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        gate.complete(Unit)

        composeRule.onNodeWithText("Which card's photo?").assertIsDisplayed()
    }

    @Test
    fun findALogoOnline_searchesForTheStoresName() {
        setScreen()

        composeRule.onNodeWithText("Find a logo online").performClick()

        assertThat(searches).containsExactly("Shop")
    }

    @Test
    fun removeLogo_bringsBackTheName() {
        givenALogo()
        setScreen()

        composeRule.onNodeWithText("Remove logo").performScrollTo().performClick()

        assertThat(stores.stores.value.single().logo).isNull()
        composeRule.onNode(previewName).assertIsDisplayed()
        composeRule.onNodeWithText("Remove logo").assertDoesNotExist()
    }

    @Test
    fun aPictureThatCantBeUsed_saysSo() {
        photos.importFailure = IOException("not a picture")
        setScreen()

        composeRule.onNodeWithText("Choose image").performClick()

        composeRule.onNodeWithText("The picture couldn't be used. Try again.").assertIsDisplayed()
    }

    @Test
    fun whileAPictureIsCopiedIn_theButtonsWait() {
        photos.importGate = kotlinx.coroutines.CompletableDeferred()
        givenALogo()
        setScreen()

        composeRule.onNodeWithText("Choose image").performClick()

        composeRule.onNodeWithText("Choose image").assertIsNotEnabled()
        composeRule.onNodeWithText("Remove logo").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aStoreThatIsGone_saysSo_andOffersNothing() {
        stores.stores.value = emptyList()
        setScreen()

        composeRule.onNodeWithText("This store is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose image").assertDoesNotExist()
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextOnTheLogoScreen_isTranslated() {
        givenALogo()
        givenCardPhotos("Mine", "Partner's")
        setScreen()
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithText("Kép kiválasztása").performClick()
        shown.collect()
        composeRule.onNodeWithText("Mégse").performClick()
        photos.importFailure = IOException("not a picture")
        composeRule.onNodeWithText("Kép kiválasztása").performClick()
        shown.collect()
        composeRule.onNodeWithText("Kártya fotójának használata").performScrollTo().performClick()
        shown.collect()

        shown.assertSeen(
            "Bolt logója", "Vissza", "Kép kiválasztása", "Logó keresése az interneten", "Logó eltávolítása",
            "Ez a logó látszik a bolt minden kártyáján.", "Az egész kép", "Kivágás", "Mégse",
            "Nem sikerült felhasználni a képet. Próbáld újra.", "Kártya fotójának használata", "Melyik kártya fotója?",
        )
        shown.assertEveryTextIsTranslated("Shop", "Mine", "Partner's")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // Robolectric's default screen is 320dp wide
        givenALogo()
        givenCardPhotos("Mine")
        setScreen(fontScale = 2f)

        composeRule.assertTextFitsAndDoesNotOverlap()
    }

    private fun givenALogo() {
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeOnePixelPng()
        runBlocking { stores.setLogo("shop", "logo") }
        photos.stored += "logo"
    }

    /** A card of the store for each label, with a front photo `front-1`, `front-2`… whose file is there. */
    private fun givenCardPhotos(vararg labels: String) {
        val store = stores.stores.value.single()
        cards.cards.value = labels.mapIndexed { i, label ->
            val photo = "front-${i + 1}"
            File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve(photo).writeOnePixelPng()
            photos.stored += photo
            aLoyaltyCard(id = "card-${i + 1}", store = store, label = label, frontPhotoPath = photo)
        }
    }

    private fun waitForAnImageToLoad() {
        val loader = EntryPointAccessors.fromApplication(context, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
    }

    private fun directions() = StoreLogoDirections(toBack = { backs++ })

    private fun testPicker(): @androidx.compose.runtime.Composable ((Uri?) -> Unit) -> PicturePicker =
        { onPicked -> PicturePicker { onPicked(picked) } }

    private fun testLogoSearch() = LogoSearch { searches += it }

    private fun setScreen(fontScale: Float = 1f) {
        val viewModel = StoreLogoViewModel(SavedStateHandle(route = StoreLogo("shop")), stores, cards, photos)
        composeRule.setContent {
            WithFontScale(fontScale) {
                StoreLogoScreen(directions(), viewModel, testPicker(), testLogoSearch())
            }
        }
    }
}
