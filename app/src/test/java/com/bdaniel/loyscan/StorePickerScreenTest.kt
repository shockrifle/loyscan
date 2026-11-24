package com.bdaniel.loyscan

import org.robolectric.annotation.GraphicsMode
import com.bdaniel.loyscan.testing.WithFontScale
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.writeOnePixelPng
import dagger.hilt.android.EntryPointAccessors
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorePickerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<String>()
    private var backs = 0
    private var customStores = 0
    private val edited = mutableListOf<String>()
    private val logos = mutableListOf<String>()

    @Test
    fun showsEveryStore() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store"))

        composeRule.onNodeWithText("First Store").assertIsDisplayed()
        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
    }

    @Test
    fun typingInTheSearchField_showsOnlyTheMatchingStores() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store", aliases = listOf("Other")))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("other")

        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
        composeRule.onNodeWithText("First Store").assertDoesNotExist()
    }

    @Test
    fun aSearchThatMatchesNothing_saysSo() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("No store matches “xyz”").assertIsDisplayed()
    }

    @Test
    fun tappingAStore_picksIt() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store"))

        composeRule.onNodeWithText("Second Store").performClick()

        assertThat(picked).containsExactly("second")
    }

    @Test
    fun otherCard_opensTheCustomStoreForm() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithText("Other card").performClick()

        assertThat(customStores).isEqualTo(1)
    }

    @Test
    fun otherCard_isOfferedWhenTheSearchMatchesNothing() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("Other card").assertIsDisplayed()
    }

    @Test
    fun aCustomStoresEditButton_opensItsForm_andACatalogueStoreHasNone() { // #53
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "custom", name = "My Shop", isCustom = true))

        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()

        assertThat(edited).containsExactly("custom")
        assertThat(picked).isEmpty()
        composeRule.onNodeWithContentDescription("Edit First Store").assertDoesNotExist()
    }

    @Test
    fun inLogoMode_theTitleAsksWhichStore_withoutOtherCard_orEditAndLogoButtons() { // #151: a picture shared as a logo
        setScreen(
            aStore(id = "first", name = "First Store", frequentlyAdded = true),
            aStore(id = "custom", name = "My Shop", isCustom = true),
            route = StorePicker(logoPicture = "staged-1"),
        )

        composeRule.onNodeWithText("Logo for which store?").assertIsDisplayed()
        composeRule.onNodeWithText("Choose store").assertDoesNotExist()
        composeRule.onNodeWithText("Other card").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Logo of First Store").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Edit My Shop").assertDoesNotExist()
        composeRule.onNodeWithText("My Shop").performClick()

        assertThat(picked).containsExactly("custom")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_theLogoModesTitle_isTranslated() { // #151
        setScreen(aStore(id = "first", name = "First Store"), route = StorePicker(logoPicture = "staged-1"))

        composeRule.onNodeWithText("Melyik bolt logója legyen?").assertIsDisplayed()
    }

    @Test
    fun aCatalogueStoresLogoButton_opensItsLogoScreen_andACustomStoreHasNone() { // #151: a custom store's is its form's
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "custom", name = "My Shop", isCustom = true))

        composeRule.onNodeWithContentDescription("Logo of First Store").performClick()

        assertThat(logos).containsExactly("first")
        assertThat(picked).isEmpty()
        composeRule.onNodeWithContentDescription("Logo of My Shop").assertDoesNotExist()
    }

    @Test
    fun aFrequentlyAddedStore_hasItsLogoButtonInItsSectionToo() { // #151: the stores a user sees first
        setScreen(aStore(id = "hu-zeta", name = "Zeta"), aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true))

        val buttons = composeRule.onAllNodesWithContentDescription("Logo of Tesco")
        buttons.assertCountEquals(2) // in both sections
        buttons[0].performClick()

        assertThat(logos).containsExactly("hu-tesco")
    }

    @Test
    fun frequentlyAddedStores_comeFirst_underTheirOwnHeading_andCanBePicked() { // #6
        setScreen(aStore(id = "hu-zeta", name = "Zeta"), aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true))

        composeRule.onNodeWithText("Frequently added").assertIsDisplayed()
        composeRule.onNodeWithText("All stores").assertIsDisplayed()
        val tescoRows = composeRule.onAllNodesWithText("Tesco")
        tescoRows.assertCountEquals(2) // in both sections
        assertThat(tescoRows[0].fetchSemanticsNode().boundsInRoot.top)
            .isLessThan(composeRule.onNodeWithText("All stores").fetchSemanticsNode().boundsInRoot.top)
        tescoRows[0].performClick()

        assertThat(picked).containsExactly("hu-tesco")
    }

    @Test
    fun withoutAFrequentlyAddedStore_thereAreNoHeadings() { // #6
        setScreen(aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true), country = "AT")

        composeRule.onNodeWithText("Frequently added").assertDoesNotExist()
        composeRule.onNodeWithText("All stores").assertDoesNotExist()
        composeRule.onAllNodesWithText("Tesco").assertCountEquals(1)
    }

    @Test
    fun searching_hidesTheFrequentlyAddedSection() { // #6
        setScreen(aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("tes")

        composeRule.onNodeWithText("Frequently added").assertDoesNotExist()
        composeRule.onAllNodesWithText("Tesco").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // the full list's row is below the default screen's fold
    fun storesThatReadASharedScan_comeFirst_underSuggested_andStayInTheList() { // #77
        val reads = aStore(id = "reads", name = "Reads It", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128))
        setScreen(
            aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true),
            reads,
            route = StorePicker("12345674", BarcodeFormat.CODE_128),
        )

        composeRule.onNodeWithText("Suggested").assertIsDisplayed()
        val rows = composeRule.onAllNodesWithText("Reads It")
        rows.assertCountEquals(2)
        assertThat(rows[0].fetchSemanticsNode().boundsInRoot.top)
            .isLessThan(composeRule.onNodeWithText("Frequently added").fetchSemanticsNode().boundsInRoot.top)
        assertThat(composeRule.onNodeWithText("Suggested").fetchSemanticsNode().boundsInRoot.top)
            .isLessThan(rows[0].fetchSemanticsNode().boundsInRoot.top)
        rows[0].performClick()

        assertThat(picked).containsExactly("reads")
    }

    @Test
    fun withASharedScanNoStoreReads_thereIsNoSuggestedHeading() { // #77
        setScreen(aStore(id = "first", name = "First Store"), route = StorePicker("12345674", BarcodeFormat.CODE_128))

        composeRule.onNodeWithText("Suggested").assertDoesNotExist()
        composeRule.onNodeWithText("All stores").assertDoesNotExist()
    }

    @Test
    fun suggestionsWithoutFrequentlyAddedStores_stillHeadTheRestAsAllStores() { // #77
        setScreen(
            aStore(id = "reads", name = "Reads It", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128)),
            route = StorePicker("12345674", BarcodeFormat.CODE_128),
            country = "AT",
        )

        composeRule.onNodeWithText("Suggested").assertIsDisplayed()
        composeRule.onNodeWithText("Frequently added").assertDoesNotExist()
        composeRule.onNodeWithText("All stores").assertIsDisplayed()
    }

    @Test
    fun searching_hidesTheSuggestions() { // #77
        setScreen(
            aStore(id = "reads", name = "Reads It", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128)),
            route = StorePicker("12345674", BarcodeFormat.CODE_128),
        )

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("rea")

        composeRule.onNodeWithText("Suggested").assertDoesNotExist()
        composeRule.onAllNodesWithText("Reads It").assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // the full list's row is below the default screen's fold
    fun aStoreWithALogo_showsItInItsRow_inEverySection() { // #186
        storedPhoto("logo")
        setScreen(
            aStore(id = "reads", name = "Reads It", logo = "logo", frequentlyAdded = true, cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128)),
            route = StorePicker("12345674", BarcodeFormat.CODE_128),
        )
        waitForAnImageToLoad()

        composeRule.onNodeWithText("Suggested").assertIsDisplayed()
        composeRule.onNodeWithText("Frequently added").assertIsDisplayed()
        composeRule.onNodeWithText("All stores").assertIsDisplayed()
        composeRule.onAllNodesWithTag(STORE_BADGE_LOGO_TAG, useUnmergedTree = true).assertCountEquals(3)
        composeRule.onAllNodesWithTag(STORE_BADGE_INITIALS_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aStoreWithALogo_showsItInSearchResults() { // #186
        storedPhoto("logo")
        setScreen(aStore(id = "first", name = "First Store", logo = "logo"), aStore(id = "second", name = "Second Store"))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("first")
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(STORE_BADGE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun inLogoMode_aStoreWithALogo_showsItInItsRow() { // #186
        storedPhoto("logo")
        setScreen(aStore(id = "first", name = "First Store", logo = "logo"), route = StorePicker(logoPicture = "staged-1"))
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(STORE_BADGE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aStoreWithoutALogo_showsItsInitials_andNoImage() { // #186
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithTag(STORE_BADGE_INITIALS_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(STORE_BADGE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aStoreWhoseLogoFileIsMissing_showsItsInitials_andNoImage() { // #186: the loader finds no file to decode
        setScreen(aStore(id = "first", name = "First Store", logo = "lost"))

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper() // the loader's result comes on the main looper
            composeRule.onAllNodesWithTag(STORE_BADGE_INITIALS_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(STORE_BADGE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun storeNames_lineUp_whetherOrNotTheStoreHasALogo() { // #186
        storedPhoto("logo")
        setScreen(
            aStore(id = "a", name = "A Logo", logo = "logo"),
            aStore(id = "b", name = "B Initials"),
        )
        waitForAnImageToLoad()

        val lefts = listOf("A Logo", "B Initials").map { name ->
            composeRule.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        }
        assertThat(lefts.toSet()).hasSize(1)
        // The badge comes before the name.
        assertThat(lefts.first()).isGreaterThan(
            composeRule.onAllNodesWithTag(STORE_BADGE_INITIALS_TAG, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot.left
        )
    }

    @Test
    fun aRowsTalkBackText_isTheStoreNameOnly_withOrWithoutALogo() { // #186: the badge adds nothing to it
        storedPhoto("logo")
        setScreen(aStore(id = "first", name = "First Store", logo = "logo"), aStore(id = "second", name = "Second Store"))
        waitForAnImageToLoad()

        for (name in listOf("First Store", "Second Store")) {
            val row = composeRule.onNode(hasText(name) and hasClickAction()).fetchSemanticsNode().config
            assertThat(row[SemanticsProperties.Text].map { it.text }).containsExactly(name)
            assertThat(row.contains(SemanticsProperties.ContentDescription)).isFalse()
        }
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "+hu-w411dp-h891dp") // tall, so every section's rows are composed
    fun inHungarian_everyTextOnThePicker_isTranslated() { // #72
        setScreen(
            aStore(id = "reads", name = "Reads It", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128)),
            aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true),
            aStore(id = "custom", name = "My Shop", isCustom = true),
            route = StorePicker("12345674", BarcodeFormat.CODE_128),
        )
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")
        shown.collect()

        shown.assertSeen(
            "Válassz boltot", "Vissza", "Boltok keresése", "Egyéb kártya", "Egy bolt, ami nincs a listán", "Javasolt",
            "Gyakran hozzáadott", "Minden bolt", "My Shop szerkesztése", "Tesco logója", "Nincs ilyen bolt: „xyz”",
        )
        shown.assertEveryTextIsTranslated("Reads It", "Tesco", "My Shop", "xyz", "RI", "T", "MS") // the badges' initials are data (#186)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // #108; Robolectric's default screen is 320dp wide
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store"), fontScale = 2f)

        composeRule.assertTextFitsAndDoesNotOverlap(fitsByDesign = setOf("FS", "SS")) // the badges' initials shrink to fit (#186)
    }

    private val application: Application = ApplicationProvider.getApplicationContext()

    /** A real picture where the app's image loader finds a logo [name]. */
    private fun storedPhoto(name: String) =
        File(application.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve(name).writeOnePixelPng()

    /** Waits until the app's image loader has a picture in its memory cache: the logo has loaded. */
    private fun waitForAnImageToLoad() {
        val loader = EntryPointAccessors.fromApplication(application, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
    }

    private fun setScreen(vararg stores: Store, route: StorePicker = StorePicker(), country: String? = "HU", fontScale: Float = 1f) {
        val viewModel = StorePickerViewModel(FakeStoreRepository(stores.toList()), route) { country }
        val directions = StorePickerDirections(
            toStore = { picked += it },
            toCustomStore = { customStores++ },
            toEditStore = { edited += it },
            toBack = { backs++ },
            toStoreLogo = { logos += it },
        )
        composeRule.setContent { WithFontScale(fontScale) { StorePickerScreen(directions = directions, viewModel = viewModel) } }
    }
}
