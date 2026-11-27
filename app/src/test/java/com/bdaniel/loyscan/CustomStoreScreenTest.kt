package com.bdaniel.loyscan

import androidx.navigation.testing.invoke
import com.bdaniel.loyscan.ui.STORE_LOGO_PREVIEW_TAG
import org.robolectric.annotation.GraphicsMode
import com.bdaniel.loyscan.testing.WithFontScale
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import androidx.compose.ui.test.assert
import com.bdaniel.loyscan.ui.CARD_TILE_LOGO_TAG
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performTextClearance
import com.bdaniel.loyscan.testing.SystemBack
import com.bdaniel.loyscan.ui.LEAVE_FORM_DIALOG_TAG
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.writeOnePixelPng
import dagger.hilt.android.EntryPointAccessors
import org.robolectric.shadows.ShadowLooper
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CustomStoreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stores = FakeStoreRepository()
    private val cards = FakeLoyaltyCardRepository()
    private val photos = FakeCardPhotos()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logoScreens = mutableListOf<String>()

    /** A text drawn in the logo slot's preview, not in the name field. Its node merges them: read the unmerged tree. */
    private fun inThePreview(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(STORE_LOGO_PREVIEW_TAG))

    /** The store's logo in the logo slot's preview, drawn as a tile draws it (#192). */
    private val previewLogo = hasTestTag(CARD_TILE_LOGO_TAG) and hasAnyAncestor(hasTestTag(STORE_LOGO_PREVIEW_TAG))
    private val scanned = mutableListOf<String>()
    private var backs = 0

    @Test
    fun create_isDisabledUntilTheStoreHasAName() {
        setScreen()

        composeRule.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Create").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theFirstColour_isPickedAtFirst() {
        setScreen()

        composeRule.onNodeWithContentDescription("Blue") // COLORS[0]
            .assertIsSelected()
        composeRule.onNodeWithContentDescription("Red") // COLORS[1]
            .assertIsNotSelected()
    }

    @Test
    fun theNewStoreForm_asksForNoBarcodeType() { // #187: the card form asks for it
        setScreen()

        composeRule.onNodeWithText("Create").performScrollTo()
        composeRule.onNodeWithText("Barcode for typed numbers").assertDoesNotExist()
        composeRule.onNodeWithText("Code 128").assertDoesNotExist()
        composeRule.onNodeWithText("A scanned card keeps the barcode it was scanned in.").assertDoesNotExist()
    }

    @Test
    fun theEditStoreForm_asksForNoBarcodeType() { // #187
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true, defaultBarcodeFormat = BarcodeFormat.EAN_13))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithText("Change logo").performScrollTo()
        composeRule.onNodeWithText("Barcode for typed numbers").assertDoesNotExist()
        composeRule.onNodeWithText("EAN-13").assertDoesNotExist()
    }

    @Test
    fun creatingAStore_savesItWithTheChosenColour_inCode128_andOpensTheScannerForIt() { // Code 128: #187
        setScreen()
        val color = CustomStoreViewModel.COLORS[3]

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithContentDescription("Orange").performClick() // COLORS[3]
        composeRule.onNodeWithText("Create").performScrollTo().performClick()

        composeRule.runOnIdle {
            val saved = stores.stores.value.single()
            assertThat(saved.name).isEqualTo("My Shop")
            assertThat(saved.color).isEqualTo(color.hex)
            assertThat(saved.defaultBarcodeFormat).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(scanned).containsExactly(saved.id)
        }
    }

    @Test
    fun aFailedCreate_saysSo() {
        stores.addFailure = IllegalStateException("insert failed")
        setScreen()

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Create").performScrollTo().performClick()

        composeRule.onNodeWithText("The store couldn't be saved. Try again.").performScrollTo().assertIsDisplayed()
        assertThat(scanned).isEmpty()
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun editingAStore_showsItsValues_andSavingItReturns() { // #53
        stores.stores.value = listOf(
            aStore(id = "custom", name = "My Shop", color = CustomStoreViewModel.COLORS[2].hex, isCustom = true, defaultBarcodeFormat = BarcodeFormat.QR_CODE),
        )
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithText("Edit store").assertIsDisplayed()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).assert(hasText("My Shop"))
        composeRule.onNodeWithContentDescription("Green") // COLORS[2]
            .assertIsSelected()
        composeRule.onNodeWithText("Create").assertDoesNotExist()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("2")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.runOnIdle {
            val saved = stores.stores.value.single()
            assertThat(saved.name).isEqualTo("2My Shop") // typed where the cursor starts
            assertThat(saved.defaultBarcodeFormat).isEqualTo(BarcodeFormat.QR_CODE) // its own type, not asked (#187)
            assertThat(backs).isEqualTo(1)
            assertThat(scanned).isEmpty()
        }
    }

    @Test
    fun editingAStoreThatIsGone_saysSo_withoutAForm() { // #53
        setScreen(CustomStore("gone"))

        composeRule.onNodeWithText("This store is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun deletingAStore_saysHowManyCardsGoWithIt_andReturnsOnceDeleted() { // #53
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store), aLoyaltyCard(id = cardId(2), store = store))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertIsDisplayed()
        composeRule.onNodeWithText("Its 2 cards are deleted with it.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle {
            assertThat(stores.stores.value).isEmpty()
            assertThat(backs).isEqualTo(1)
        }
    }

    @Test
    fun theDeleteDialog_namesTheStoreAsSaved_notAnUnsavedRename() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput(" Renamed")
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertIsDisplayed()
    }

    @Test
    fun deletingAStoreWithOneCard_saysSo() { // #53
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(store = store))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Its card is deleted with it.").assertIsDisplayed()
    }

    @Test
    fun deletingAStoreWithoutCards_saysSo_andCancelKeepsIt() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("It has no cards.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertDoesNotExist()
        composeRule.runOnIdle {
            assertThat(stores.stores.value).hasSize(1)
            assertThat(backs).isEqualTo(0)
        }
    }

    @Test
    fun aDeleteThatFails_saysSo_inTheDialog() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        stores.deleteFailure = IllegalStateException("delete failed")
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("The store couldn't be deleted. Try again.").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(backs).isEqualTo(0) }
    }

    @Test
    fun editingAStoreWithALogo_showsItInTheLogoSlot() { // #151
        givenACustomStoreWithALogo()
        setScreen(CustomStore("custom"))
        waitForAnImageToLoad()

        composeRule.onNodeWithText("Logo").performScrollTo().assertIsDisplayed()
        composeRule.onNode(previewLogo, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_TAG).assert(hasContentDescriptionExactly("My Shop")) // #192: as a tile is read
        composeRule.onNode(inThePreview("MS"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun editingAStoreWithoutALogo_showsTheInitialsOfItsNameInTheLogoSlot_asTyped() { // #151: the form's name; #192: initials
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNode(inThePreview("MS"), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNode(inThePreview("My Shop"), useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextReplacement("Big Deal")
        composeRule.onNode(inThePreview("BD"), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_TAG).assert(hasContentDescriptionExactly("Big Deal"))
        composeRule.onNode(previewLogo, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun changeLogo_opensTheStoresLogoScreen() { // #151
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithText("Change logo").performScrollTo().performClick()

        assertThat(logoScreens).containsExactly("custom")
    }

    @Test
    fun aNewStore_hasNoLogoSlot() { // #151: it has no id before Create; its logo is set through Edit
        setScreen()

        composeRule.onNodeWithText("Logo").assertDoesNotExist()
        composeRule.onNodeWithText("Change logo").assertDoesNotExist()
        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun aStoreThatIsGone_hasNoLogoSlot() { // #151
        setScreen(CustomStore("gone"))

        composeRule.onNodeWithText("This store is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Change logo").assertDoesNotExist()
        composeRule.onNodeWithTag(STORE_LOGO_PREVIEW_TAG).assertDoesNotExist()
    }

    @Test
    fun aNewStore_hasNoDeleteAction() { // #53
        setScreen()

        composeRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextOnTheNewStoreForm_isTranslated() { // #72
        stores.addFailure = IllegalStateException("insert failed")
        setScreen()
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Létrehozás").performScrollTo().performClick()
        shown.collect()

        shown.assertSeen(
            "Új bolt", "Vissza", "Bolt neve", "Szín", "Kék", "Szürke", "Nem sikerült menteni a boltot. Próbáld újra.",
            "Létrehozás",
        )
        shown.assertEveryTextIsTranslated("My Shop")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextOnTheEditForm_andItsDeleteDialog_isTranslated() { // #72
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store), aLoyaltyCard(id = cardId(2), store = store))
        setScreen(CustomStore("custom"))
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithText("Logó módosítása").performScrollTo()
        shown.collect()
        composeRule.onNodeWithContentDescription("Törlés").performClick()
        shown.collect()
        stores.deleteFailure = IllegalStateException("delete failed")
        composeRule.onNode(hasText("Törlés") and hasAnyAncestor(isDialog())).performClick()
        shown.collect()

        shown.assertSeen(
            "Bolt szerkesztése", "Mentés", "Törlés", "Törlöd ezt a boltot: My Shop?", "Logó", "Logó módosítása",
            "A hozzá tartozó 2 kártya is törlődik vele.", "Mégse", "Nem sikerült törölni a boltot. Próbáld újra.",
        )
        shown.assertEveryTextIsTranslated("My Shop", "MS") // its initials in the logo slot (#192)
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_deletingAStoreWithOneCard_saysSo() { // #72
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(store = store))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Törlés").performClick()

        composeRule.onNodeWithText("A kártyája is törlődik vele.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_deletingAStoreWithoutCards_saysSo() { // #72
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Törlés").performClick()

        composeRule.onNodeWithText("Nincs hozzá kártya.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_editingAStoreThatIsGone_saysSo() { // #72
        setScreen(CustomStore("gone"))

        composeRule.onNodeWithText("Ez a bolt már nem létezik.").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // #108; Robolectric's default screen is 320dp wide
        setScreen(fontScale = 2f)

        composeRule.assertTextFitsAndDoesNotOverlap()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_theEditFormsTextIncludingItsLogoSlot_fits() { // #151
        stores.stores.value = listOf(aStore(id = "custom", name = "Corner Shop And Bakery", isCustom = true))
        setScreen(CustomStore("custom"), fontScale = 2f)

        composeRule.onNodeWithText("Change logo").assertExists()
        composeRule.assertTextFitsAndDoesNotOverlap(fitsByDesign = setOf("CS")) // the slot's initials shrink to fit, as on a tile (#192)
    }

    @Test
    fun editingAStore_systemBackWithAChange_asksToSaveChanges_andBackInTheDialogKeepsEditing() { // #209
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextReplacement("My Gym")

        back.onTheScreen()

        composeRule.onNode(hasText("Save changes?") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        composeRule.onNode(inTheDialog("Discard")).assertIsDisplayed()
        composeRule.onNode(inTheDialog("Save")).assertIsDisplayed()
        back.inTheDialog()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).assert(hasText("My Gym"))
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun creatingAStore_theBackArrowWithAName_asksToDiscardTheStore_andDiscardLeavesWithoutSaving() { // #209
        setScreen()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNode(hasText("Discard this store?") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        composeRule.onNode(inTheDialog("Discard")).performClick()

        assertThat(backs).isEqualTo(1)
        assertThat(stores.stores.value).isEmpty()
    }

    @Test
    fun theDialogsSave_createsTheStore_andOpensTheScannerForIt_asCreateDoes() { // #209
        setScreen()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNode(inTheDialog("Save")).performClick()

        composeRule.runOnIdle {
            val saved = stores.stores.value.single()
            assertThat(saved.name).isEqualTo("My Shop")
            assertThat(scanned).containsExactly(saved.id)
            assertThat(backs).isEqualTo(0)
        }
    }

    @Test
    fun theDialogsSave_onAnEdit_savesIt_andGoesBack_asSaveDoes() { // #209
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextReplacement("My Gym")
        back.onTheScreen()

        composeRule.onNode(inTheDialog("Save")).performClick()

        composeRule.runOnIdle {
            assertThat(stores.stores.value.single().name).isEqualTo("My Gym")
            assertThat(backs).isEqualTo(1)
        }
    }

    @Test
    fun aStoreThatCantBeSaved_offersDiscardAndKeepEditing() { // #209: a colour chosen, but no name
        setScreen()
        composeRule.onNodeWithContentDescription("Orange").performClick()

        back.onTheScreen()

        composeRule.onNode(inTheDialog("Save")).assertDoesNotExist()
        composeRule.onNode(inTheDialog("Discard")).assertIsDisplayed()
        composeRule.onNode(inTheDialog("Keep editing")).performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Orange").assertIsSelected()
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun aNameTypedAndDeleted_leavesWithoutAsking() { // #209
        setScreen()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextClearance()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_theDialogOnANewStore_isTranslated() { // #209
        setScreen()
        composeRule.onNodeWithContentDescription("Narancs").performClick()
        val shown = ShownTexts(composeRule)

        back.onTheScreen()
        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Elveted ezt a boltot?", "Elvetés", "Szerkesztés folytatása")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_theDialogsTextsFit() { // #209
        setScreen(fontScale = 2f)
        composeRule.onNodeWithContentDescription("Orange").performClick()

        back.onTheScreen()

        composeRule.onNode(inTheDialog("Keep editing")).assertIsDisplayed()
        composeRule.assertTextFitsAndDoesNotOverlap(under = hasTestTag(LEAVE_FORM_DIALOG_TAG))
    }

    private fun inTheDialog(text: String) = hasText(text) and hasClickAction() and hasAnyAncestor(isDialog())

    private val back = SystemBack(composeRule)

    private fun givenACustomStoreWithALogo() {
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeOnePixelPng()
        photos.stored += "logo"
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true, logo = "logo"))
    }

    private fun waitForAnImageToLoad() {
        val loader = EntryPointAccessors.fromApplication(context, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
    }

    private fun setScreen(route: CustomStore = CustomStore(), fontScale: Float = 1f) {
        val viewModel = CustomStoreViewModel(SavedStateHandle(route = route), stores, cards, photos, Clock.fixed(SOME_INSTANT, ZoneOffset.UTC))
        val directions = CustomStoreDirections(onCreated = { scanned += it }, toBack = { backs++ }, toStoreLogo = { logoScreens += it })
        composeRule.setContent {
            back.capture()
            WithFontScale(fontScale) { CustomStoreScreen(directions = directions, viewModel = viewModel) }
        }
    }
}
