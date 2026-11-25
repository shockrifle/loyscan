package com.bdaniel.loyscan

import java.io.File
import org.robolectric.shadows.ShadowLooper
import dagger.hilt.android.EntryPointAccessors
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.testing.writeOnePixelPng
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.util.Locale
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import com.bdaniel.loyscan.testing.ShownTexts
import org.robolectric.shadows.ShadowToast
import androidx.compose.ui.test.isDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import com.bdaniel.loyscan.testing.WithFontScale
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalSharedTransitionApi::class)
// A typical phone: Robolectric's default 320x470dp screen is shorter than the header, barcode and number together.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val card = aLoyaltyCard(label = "Gold card", store = aStore(name = "Test Store"), number = "0042")

    /** The store's name as text in the band, which shows its logo or initials instead (#210). */
    private val nameInTheBand = hasText("Test Store") and hasAnyAncestor(hasTestTag(STORE_HEADER_TAG))

    /** The store's initials in the band (#210): kept out of TalkBack's reading, so found by their tag. */
    private val initialsInTheBand = hasTestTag(STORE_BAND_INITIALS_TAG) and hasAnyAncestor(hasTestTag(STORE_HEADER_TAG))

    private val keepsScreenOn get() = composeRule.activity.window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0

    private fun setScreen(
        color: String = "#336699",
        card: LoyaltyCard = this.card,
        repository: FakeLoyaltyCardRepository = FakeLoyaltyCardRepository(listOf(card)),
        photos: FakeCardPhotos = FakeCardPhotos("front", "back"),
        onBack: () -> Unit = {},
        onEdit: (cardId: String, storeId: String) -> Unit = { _, _ -> },
        added: Boolean = false,
        onAddAnother: () -> Unit = {},
        restorationTester: StateRestorationTester? = null,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ) {
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = card.id, color = color, added = added)), repository, photos)
        val content = @Composable {
            wrap {
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        DetailScreen(
                            cardId = card.id,
                            color = color,
                            added = added,
                            animatedVisibilityScope = this,
                            directions = DetailDirections(toBack = onBack, toEdit = onEdit, toAddCard = onAddAnother),
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
        if (restorationTester != null) restorationTester.setContent(content) else composeRule.setContent(content)
    }

    @Test
    fun showsTheCardLabelAndNumber() {
        setScreen()

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertIsDisplayed()
    }

    @Test
    fun theBarcode_isAGroupOfItsOwn_insetFromTheSheet() { // #108
        setScreen()

        val surface = composeRule.onNodeWithTag(BARCODE_SURFACE_TAG).fetchSemanticsNode().boundsInRoot
        // The sheet keeps the label and the store's name above the barcode (#210).
        val label = composeRule.onNodeWithText("Gold card").fetchSemanticsNode().boundsInRoot
        val store = composeRule.onNodeWithText("Test Store").fetchSemanticsNode().boundsInRoot
        assertThat(label.bottom).isAtMost(store.top)
        assertThat(store.bottom).isAtMost(surface.top)
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dp = composeRule.density.density

        assertThat((surface.top - store.bottom) / dp).isWithin(1f).of(24f) // a group gap above it
        assertThat(surface.left / dp).isAtLeast(16f)
        assertThat((screen.right - surface.right) / dp).isAtLeast(16f)
    }

    /** The card sheet's barcode and its white surface, in dp. */
    private fun barcodeAndSurface(): Pair<DpRect, DpRect> {
        val barcode = composeRule.onNode(
            hasContentDescription("Barcode") and hasAnyAncestor(hasTestTag(BARCODE_SURFACE_TAG)),
            useUnmergedTree = true,
        ).getBoundsInRoot()
        return barcode to composeRule.onNodeWithTag(BARCODE_SURFACE_TAG).getBoundsInRoot()
    }

    private fun cardIn(format: BarcodeFormat, number: String = "LoyScan 0042") =
        aLoyaltyCard(store = aStore(defaultBarcodeFormat = format), number = number)

    @Test
    fun aQrCode_isAtMost160dpWide_centredOnItsWhiteSurface() { // #212: a till's scanner reads it from closer
        setScreen(card = cardIn(BarcodeFormat.QR_CODE))

        val (barcode, surface) = barcodeAndSurface()

        assertThat(barcode.width.value).isWithin(0.5f).of(160f)
        assertThat(barcode.height.value).isWithin(0.5f).of(160f)
        assertThat((barcode.left + barcode.right).value / 2).isWithin(0.5f).of((surface.left + surface.right).value / 2)
        // The surface stays the sheet's width: far wider than the code.
        assertThat(surface.width.value).isGreaterThan(300f)
    }

    @Test
    fun aDataMatrix_isAtMost160dpWide() { // #212
        setScreen(card = cardIn(BarcodeFormat.DATA_MATRIX))

        val (barcode, _) = barcodeAndSurface()

        // ZXing picks the smallest symbol, here a rectangular 8x32 one (12x36 modules with the quiet zone): capped by
        // its width, at its own proportions.
        assertThat(barcode.width.value).isWithin(0.5f).of(160f)
        assertThat(barcode.height.value).isWithin(0.5f).of(160f * 12 / 36)
    }

    @Test
    @Config(qualifiers = "w180dp-h640dp")
    fun onAScreenNarrowerThanTheCap_aQrCodeFitsTheWidth() { // #212
        setScreen(card = cardIn(BarcodeFormat.QR_CODE))

        val (barcode, surface) = barcodeAndSurface()

        // The surface's width inside its 16dp padding, less than 160dp here.
        assertThat(barcode.width.value).isWithin(0.5f).of(surface.width.value - 32f)
        assertThat(barcode.width.value).isLessThan(160f)
        assertThat(barcode.height.value).isWithin(0.5f).of(barcode.width.value)
    }

    @Test
    fun aLinearBarcode_keepsTheSurfacesFullWidth() { // #212
        setScreen(card = cardIn(BarcodeFormat.EAN_13, number = "5901234123457"))

        val (barcode, surface) = barcodeAndSurface()

        assertThat(barcode.width.value).isWithin(0.5f).of(surface.width.value - 32f)
    }

    @Test
    fun aPdf417_keepsTheSurfacesFullWidth() { // #212: a stacked code, not a square one
        setScreen(card = cardIn(BarcodeFormat.PDF_417))

        val (barcode, surface) = barcodeAndSurface()

        assertThat(barcode.width.value).isWithin(0.5f).of(surface.width.value - 32f)
    }

    @Test
    @Config(qualifiers = "w320dp-h470dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // #108
        setScreen(
            card = card.copy(note = "PIN hint: birthday"),
            wrap = { content -> WithFontScale(2f, content) },
        )

        composeRule.assertTextFitsAndDoesNotOverlap()
    }

    @Test
    fun showsTheCardsNote_underTheNumber() { // #9
        setScreen(card = card.copy(note = "PIN hint: birthday\nGold tier"))

        val note = composeRule.onNodeWithTag(CARD_NOTE_TAG).performScrollTo()
        note.assertTextEquals("PIN hint: birthday\nGold tier")
        assertThat(note.getUnclippedBoundsInRoot().top)
            .isAtLeast(composeRule.onNodeWithText("0042").getUnclippedBoundsInRoot().bottom)
    }

    @Test
    fun aCardWithoutANote_showsNone() { // #9
        setScreen()

        composeRule.onNodeWithText("0042").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun aCardWithPhotos_showsThemUnderTheNumber() { // #10
        setScreen(card = card.copy(frontPhotoPath = "front", backPhotoPath = "back"))

        val front = composeRule.onNodeWithContentDescription("Front photo").performScrollTo()
        composeRule.onNodeWithContentDescription("Back photo").assertExists()
        assertThat(front.getUnclippedBoundsInRoot().top)
            .isAtLeast(composeRule.onNodeWithText("0042").getUnclippedBoundsInRoot().bottom)
    }

    /** Shows the card with a store logo that loads, a one-pixel picture. */
    private fun setScreenWithALogo(wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
        File(composeRule.activity.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeOnePixelPng()
        setScreen(card = card.copy(store = card.store.copy(logo = "logo")), photos = FakeCardPhotos("logo"), wrap = wrap)
        val loader = EntryPointAccessors.fromApplication(composeRule.activity, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
    }

    @Test
    fun aStoresLogo_isShownInTheBand_inPlaceOfItsInitials() { // #151, #210
        setScreenWithALogo()

        composeRule.onNodeWithTag(STORE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(initialsInTheBand, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(nameInTheBand, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aStoreWithoutALogo_showsItsInitialsInTheBand_notItsName() { // #210
        setScreen()

        composeRule.onNode(initialsInTheBand, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(nameInTheBand, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(STORE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLogoThatCantBeLoaded_showsTheStoresInitials() { // #151, #210
        setScreen(card = card.copy(store = card.store.copy(logo = "broken")), photos = FakeCardPhotos("broken"))

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            composeRule.onAllNodes(initialsInTheBand, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(STORE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(nameInTheBand, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLogoWhoseFileIsMissing_showsTheStoresInitials() { // #151, #210
        setScreen(card = card.copy(store = card.store.copy(logo = "lost")), photos = FakeCardPhotos())

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(initialsInTheBand, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(STORE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(nameInTheBand, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun thereIsNoTopBarTitle_theStoresNameIsShownOnlyOnTheSheet() { // #210
        setScreen()

        composeRule.onAllNodesWithText("Test Store", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(nameInTheBand, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBand_is140dpTall_underAShortStatusBar_withItsButtonsInTheTopCorners_andTheLogoCentredBelowThem() { // #210
        assertTheBandsLayout(statusBar = 24.dp)
    }

    @Test
    fun theBand_is140dpTall_underATallStatusBar_withItsButtonsInTheTopCorners_andTheLogoCentredBelowThem() { // #210: a camera cut-out
        assertTheBandsLayout(statusBar = 48.dp)
    }

    private fun assertTheBandsLayout(statusBar: Dp) {
        var view: android.view.View? = null
        setScreenWithALogo(wrap = { content -> view = LocalView.current; content() })
        composeRule.runOnUiThread {
            val top = with(composeRule.density) { statusBar.roundToPx() }
            val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0)).build()
            ViewCompat.dispatchApplyWindowInsets(view!!, insets)
        }
        composeRule.waitForIdle()
        val band = composeRule.onNodeWithTag(STORE_HEADER_TAG).getBoundsInRoot()
        val back = composeRule.onNodeWithContentDescription("Back").getBoundsInRoot()
        val edit = composeRule.onNodeWithContentDescription("Edit").getBoundsInRoot()
        val delete = composeRule.onNodeWithContentDescription("Delete").getBoundsInRoot()
        val logo = composeRule.onNodeWithTag(STORE_LOGO_TAG, useUnmergedTree = true).getBoundsInRoot()

        // One band from the top of the screen, the status bar's area included.
        assertThat(band.top.value).isWithin(0.5f).of(0f)
        assertThat(band.height.value).isWithin(0.5f).of(140f)
        // The buttons below the status bar, Back in the start corner, Edit and Delete in the end one.
        listOf(back, edit, delete).forEach { assertThat(it.top.value).isAtLeast(statusBar.value - 0.5f) }
        assertThat((back.left - band.left).value).isAtMost(8f)
        assertThat((band.right - delete.right).value).isAtMost(8f)
        assertThat(edit.right.value).isAtMost(delete.left.value + 0.5f)
        // The logo below them, centred across the band. It is fitted (ContentScale.Fit) and centred in its bounds,
        // so bounds centred in the band centre the logo.
        assertThat(logo.top.value).isAtLeast(maxOf(back.bottom, edit.bottom, delete.bottom).value - 0.5f)
        assertThat(logo.bottom.value).isAtMost(band.bottom.value + 0.5f)
        assertThat(((logo.left + logo.right) / 2).value).isWithin(1f).of(((band.left + band.right) / 2).value)
    }

    @Test
    fun talkBack_readsTheBand_asAHeadingWithTheStoresName_overItsInitials() { // #210
        setScreen()

        assertTheBandIsAHeadingNamed("Test Store")
    }

    @Test
    fun talkBack_readsTheBand_asAHeadingWithTheStoresName_overItsLogo() { // #210
        setScreenWithALogo()

        assertTheBandIsAHeadingNamed("Test Store")
    }

    @Test
    fun beforeTheCardHasLoaded_theBandIsNoEmptyHeading() { // #210: the name is empty until Room emits
        setScreen(repository = FakeLoyaltyCardRepository(emptyList()))

        composeRule.onAllNodes(isHeading()).assertCountEquals(0)
    }

    /** What TalkBack hears: the band's heading says the store's [name], and nothing anywhere spells out "TS". */
    private fun assertTheBandIsAHeadingNamed(name: String) {
        val heading = composeRule.onNode(isHeading() and hasAnyAncestor(hasTestTag(STORE_HEADER_TAG)))
        heading.assertContentDescriptionEquals(name)
        assertThat(heading.fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }).isEmpty()
        composeRule.onAllNodesWithText("TS", substring = true).assertCountEquals(0)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun onADarkBrandColour_theBandsButtonsAreWhite() { // #210: the top bar drew them in onSurfaceVariant
        setScreen(color = "#1A1A1A", card = card.copy(store = card.store.copy(color = "#1A1A1A")))
        composeRule.waitForIdle()

        assertThat(backIconPixels()).contains(0xFFFFFFFF.toInt())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun onALightBrandColour_theBandsButtonsAreBlack() { // #210
        setScreen(color = "#FFEB3B", card = card.copy(store = card.store.copy(color = "#FFEB3B")))
        composeRule.waitForIdle()

        assertThat(backIconPixels()).contains(0xFF000000.toInt())
    }

    private fun backIconPixels(): Set<Int> {
        val pixels = composeRule.onNodeWithContentDescription("Back").captureToImage().toPixelMap()
        return buildSet { for (x in 0 until pixels.width) for (y in 0 until pixels.height) add(pixels[x, y].toArgb()) }
    }

    @Test
    fun aCardWithOnlyABackPhoto_showsOnlyThat() { // #10
        setScreen(card = card.copy(backPhotoPath = "back"))

        composeRule.onNodeWithContentDescription("Back photo").assertExists()
        composeRule.onNodeWithContentDescription("Front photo").assertDoesNotExist()
    }

    @Test
    fun aCardWithoutPhotos_showsNone() { // #10
        setScreen()

        composeRule.onNodeWithText("0042").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Front photo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back photo").assertDoesNotExist()
    }

    @Test
    fun aPhotoWhoseFileIsMissing_showsNoThumbnail() { // #87: a cloud restore brings the row, not the file
        setScreen(card = card.copy(frontPhotoPath = "front", backPhotoPath = "lost"), photos = FakeCardPhotos("front"))

        // The thumbnail is the viewer's only way in, so no viewer opens for the missing photo either.
        composeRule.onNodeWithContentDescription("Front photo").assertExists()
        composeRule.onNodeWithContentDescription("Back photo").assertDoesNotExist()
    }

    @Test
    fun aCardWhosePhotoFilesAreAllMissing_showsNoPhotos() { // #87
        setScreen(card = card.copy(frontPhotoPath = "lost", backPhotoPath = "gone"), photos = FakeCardPhotos())

        composeRule.onNodeWithText("0042").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Front photo").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back photo").assertDoesNotExist()
    }

    @Test
    fun tappingAPhoto_showsItFullScreen_andATapOrBackClosesIt() { // #10
        setScreen(card = card.copy(frontPhotoPath = "front", backPhotoPath = "back"))
        val inViewer = hasAnyAncestor(hasTestTag(PHOTO_VIEWER_TAG))

        composeRule.onNodeWithContentDescription("Back photo").performScrollTo().performClick()
        composeRule.onNode(hasContentDescription("Back photo") and inViewer, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).performClick()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Front photo").performScrollTo().performClick()
        composeRule.onNode(hasContentDescription("Front photo") and inViewer, useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("0042").assertExists()
    }

    @Test
    fun theOpenPhoto_staysOpenWhenThePhoneTurns() { // #10: the card screen rotates
        val restorationTester = StateRestorationTester(composeRule)
        setScreen(card = card.copy(frontPhotoPath = "front"), restorationTester = restorationTester)
        composeRule.onNodeWithContentDescription("Front photo").performScrollTo().performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
    }

    @Test
    fun scanMode_andThePhotoViewer_stayAboveTheNavigationBar() { // #108: found on a device, the number sat under it
        var view: android.view.View? = null
        setScreen(card = card.copy(frontPhotoPath = "front"), wrap = { content -> view = LocalView.current; content() })
        val bar = 96 // px
        val sendTheBar = {
            composeRule.runOnUiThread {
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bar))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(view!!, insets)
            }
            composeRule.waitForIdle()
        }
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }
        sendTheBar()
        val number = composeRule.onNode(hasText("0042") and hasAnyAncestor(hasTestTag("scanMode")), useUnmergedTree = true)
        assertThat(number.fetchSemanticsNode().boundsInRoot.bottom).isAtMost(screen.bottom - bar)

        composeRule.onNodeWithTag("scanMode").performClick()
        composeRule.onNodeWithContentDescription("Front photo").performScrollTo().performClick()
        sendTheBar()
        val photo = composeRule.onNode(hasContentDescription("Front photo") and hasAnyAncestor(hasTestTag(PHOTO_VIEWER_TAG)), useUnmergedTree = true)
        assertThat(photo.fetchSemanticsNode().boundsInRoot.bottom).isAtMost(screen.bottom - bar)
    }

    @Test
    fun scanMode_showsNoNote() { // #9: barcode and number only
        setScreen(card = card.copy(note = "PIN hint: birthday"))
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        val inScanMode = hasAnyAncestor(hasTestTag("scanMode"))
        composeRule.onNode(hasText("0042") and inScanMode, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("PIN hint: birthday") and inScanMode, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun showsTheNumberFormattedByTheStoresRule() { // #1: the display number is derived, not stored
        val rule = CardNumberRule(
            id = "example",
            format = BarcodeFormat.CODE_128,
            minLength = 9,
            maxLength = 9,
            display = CardNumberRule.Display(pattern = "### ### ###"),
        )
        setScreen(card = aLoyaltyCard(store = aStore(cardNumberRule = rule), number = "123456789"))

        composeRule.onNodeWithText("123 456 789").assertIsDisplayed()
    }

    @Test
    fun showsTheCardsBarcode() {
        setScreen()

        composeRule.onNodeWithContentDescription("Barcode").assertIsDisplayed()
    }

    @Test
    fun cardWithNothingToEncode_showsNoBarcode() {
        setScreen(card = aLoyaltyCard(number = ""))

        composeRule.onNodeWithContentDescription("Barcode").assertDoesNotExist()
    }

    @Test
    fun cardDrawnInAFallbackFormat_namesTheFormatDrawn() { // #35: ZXing can't write DataBar; GS1 data stays GS1 (#45)
        val dataBarStore = aStore(defaultBarcodeFormat = BarcodeFormat.DATABAR)
        setScreen(card = aLoyaltyCard(store = dataBarStore, number = "0109012345678901"))

        composeRule.onNodeWithText("Shown as GS1-128").assertIsDisplayed()
    }

    @Test
    fun contentNotEvenCode128CanHold_namesQrAsTheFormatDrawn() { // #35
        setScreen(card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.EAN_13), number = "Café"))

        composeRule.onNodeWithText("Shown as QR code").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_contentNotEvenCode128CanHold_namesQrInHungarian() { // #72
        setScreen(card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.EAN_13), number = "Café"))

        composeRule.onNodeWithText("Megjelenítve: QR-kód").assertIsDisplayed()
    }

    @Test
    fun scannedGs1_128Card_isDrawnAsGs1_128_withNoFormatNote() { // #33: drawn with its FNC1, not as plain Code 128
        setScreen(card = aLoyaltyCard(number = "9799123456789012", scannedFormat = BarcodeFormat.GS1_128))

        composeRule.onNodeWithContentDescription("Barcode").assertExists()
        composeRule.onNodeWithText("Shown as", substring = true).assertDoesNotExist()
    }

    @Test
    fun cardDrawnInItsOwnFormat_showsNoFormatNote() { // #35
        setScreen()

        composeRule.onNodeWithText("Shown as", substring = true).assertDoesNotExist()
    }

    @Test
    fun tappingTheNumber_copiesItAsShown() {
        val rule = CardNumberRule(
            id = "example",
            format = BarcodeFormat.CODE_128,
            minLength = 9,
            maxLength = 9,
            display = CardNumberRule.Display(pattern = "### ### ###"),
        )
        setScreen(card = aLoyaltyCard(store = aStore(cardNumberRule = rule), number = "123456789"))

        composeRule.onNodeWithText("123 456 789").performClick()

        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { clipboard.primaryClip != null }
        assertThat(clipboard.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("123 456 789")
    }

    @Test
    @Config(sdk = [32])
    fun onAndroid12OrOlder_copyingTheNumber_saysSo() { // Android 13+ confirms a copy itself
        setScreen()

        composeRule.onNodeWithText("0042").performClick()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Number copied")
    }

    @Test
    @Config(sdk = [32])
    fun theCopyToast_readsComposeResources() { // #126
        // Compose updates LocalConfiguration and LocalResources on a configuration change it handles; LocalContext's
        // resources don't follow it. The app recreates its activity on a locale change today, so this pins where the
        // text is read (lint's LocalContextGetResourceValueCall) rather than a bug a user can hit.
        val english = composeRule.activity.resources
        val hungarian = composeRule.activity.createConfigurationContext(
            Configuration(english.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag("hu"))) },
        ).resources
        var resources by mutableStateOf(english)
        setScreen(wrap = { content ->
            CompositionLocalProvider(LocalConfiguration provides resources.configuration, LocalResources provides resources) {
                content()
            }
        })

        resources = hungarian
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0042").performClick()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Szám másolva")
    }

    @Test
    @Config(sdk = [32], qualifiers = "+hu")
    fun inHungarian_copyingTheNumber_saysSoInHungarian() { // #72
        setScreen()

        composeRule.onNodeWithText("0042").performClick()

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Szám másolva")
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { clipboard.primaryClip != null }
        assertThat(clipboard.primaryClipDescription!!.label.toString()).isEqualTo("Kártyaszám")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextOnTheCard_isTranslated() { // #72
        // A DataBar card is drawn as GS1-128, so the screen names the format drawn.
        val card = aLoyaltyCard(
            label = "Gold card",
            store = aStore(name = "Test Store", defaultBarcodeFormat = BarcodeFormat.DATABAR),
            number = "0109012345678901",
            note = "PIN hint",
            frontPhotoPath = "front",
            backPhotoPath = "back",
        )
        val repository = FakeLoyaltyCardRepository(listOf(card))
        setScreen(card = card, repository = repository, added = true)
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithContentDescription("Hátoldali fotó").performScrollTo().performClick()
        shown.collect()
        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).performClick()
        composeRule.onNodeWithContentDescription("Vonalkód").performScrollTo().performTouchInput { doubleClick() }
        shown.collect()
        composeRule.onNodeWithTag("scanMode").performClick()
        composeRule.onNodeWithContentDescription("Törlés").performClick()
        shown.collect()
        repository.deleteFailure = IllegalStateException("disk I/O error")
        composeRule.onNode(hasText("Törlés") and hasAnyAncestor(isDialog())).performClick()
        shown.collect()

        shown.assertSeen(
            "Kártya hozzáadva", "Újabb hozzáadása", "Megjelenítve: GS1-128", "Előoldali fotó", "Hátoldali fotó",
            "Teljes képernyő bezárása", "Törlöd ezt a kártyát?", "Nem sikerült törölni a kártyát. Próbáld újra.",
            "Szám másolása", "Vissza",
        )
        // "TS": the store's initials in the band, from its name (the test's unmerged tree keeps them; TalkBack doesn't).
        shown.assertEveryTextIsTranslated("Test Store", "TS", "Gold card", card.displayNumber, "PIN hint")
    }

    @Test
    fun turnsTheScreenToFullBrightness() {
        setScreen()

        assertThat(composeRule.activity.window.attributes.screenBrightness).isEqualTo(BRIGHTNESS_OVERRIDE_FULL)
    }

    @Test
    fun doubleTappingTheBarcode_opensScanMode_whichKeepsTheScreenOn() {
        setScreen()

        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        composeRule.onNodeWithTag("scanMode").assertIsDisplayed()
        assertThat(keepsScreenOn).isTrue()
    }

    @Test
    fun scanMode_backClosesIt_andLetsTheScreenSleep() {
        setScreen()
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }
        composeRule.onNodeWithTag("scanMode").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag("scanMode").assertDoesNotExist()
        assertThat(keepsScreenOn).isFalse()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun scanMode_inLandscape_keepsTheNumberUnderA2dBarcode() {
        setScreen(card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.QR_CODE), number = "0042"))
        composeRule.onNodeWithContentDescription("Barcode").performScrollTo().performTouchInput { doubleClick() }

        // Unmerged: the clickable scan-mode surface merges its children into one node.
        composeRule.onNode(hasText("0042") and hasAnyAncestor(hasTestTag("scanMode")), useUnmergedTree = true)
            .assertIsDisplayed()
        // As large as the height the number leaves, not the card screen's 160dp (#212).
        val bounds = scanModeBarcode.getBoundsInRoot()
        assertThat(bounds.height.value).isGreaterThan(250f)
        assertThat(bounds.width.value).isWithin(1f).of(bounds.height.value)
    }

    private val scanModeBarcode get() = composeRule.onNode(
        hasContentDescription("Barcode") and hasAnyAncestor(hasTestTag("scanMode")),
        useUnmergedTree = true,
    )

    @Test
    fun scanMode_inPortrait_runsA1dBarcodeAlongTheLongSide() { // #34
        setScreen() // a Code 128 card
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        val bounds = scanModeBarcode.getBoundsInRoot()

        // The full width inside the padding, and the height the number leaves: far more than the 200dp it had.
        assertThat(bounds.width.value).isWithin(1f).of(411f - 48f)
        assertThat(bounds.height.value).isGreaterThan(600f)
        composeRule.onNode(hasText("0042") and hasAnyAncestor(hasTestTag("scanMode")), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun scanMode_inLandscape_drawsA1dBarcodeAcrossTheWidth() { // #34: the long side already is the width
        setScreen()
        composeRule.onNodeWithContentDescription("Barcode").performScrollTo().performTouchInput { doubleClick() }

        val bounds = scanModeBarcode.getBoundsInRoot()

        assertThat(bounds.width.value).isWithin(1f).of(891f - 48f)
        assertThat(bounds.height.value).isAtMost(200f)
    }

    @Test
    fun scanMode_inPortrait_keepsA2dBarcodeSquare() { // #34
        setScreen(card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.QR_CODE), number = "0042"))
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        val bounds = scanModeBarcode.getBoundsInRoot()

        assertThat(bounds.width.value).isWithin(1f).of(411f - 48f)
        assertThat(bounds.height.value).isWithin(1f).of(bounds.width.value)
    }

    @Test
    fun scanMode_forAFallbackCard_showsNoFormatNote() { // #37: the note stays on the detail card
        val dataBarStore = aStore(defaultBarcodeFormat = BarcodeFormat.DATABAR)
        setScreen(card = aLoyaltyCard(store = dataBarStore, number = "0109012345678901"))
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        val inScanMode = hasAnyAncestor(hasTestTag("scanMode"))
        // The card does fall back: the detail card under the overlay has the note.
        composeRule.onNode(hasText("Shown as GS1-128") and !inScanMode, useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("0109012345678901") and inScanMode, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("Shown as", substring = true) and inScanMode, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun scanMode_showsTheBarcode_overTheWholeScreen() { // #178: the Play listing says a double-tap shows it full screen
        setScreen()
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(composeRule.onNodeWithTag("scanMode").fetchSemanticsNode().boundsInRoot).isEqualTo(screen)
        composeRule.onNode(hasContentDescription("Barcode") and hasAnyAncestor(hasTestTag("scanMode")), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun scanMode_tapClosesIt() {
        setScreen()
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }

        composeRule.onNodeWithTag("scanMode").performClick()

        composeRule.onNodeWithTag("scanMode").assertDoesNotExist()
    }

    @Test
    fun backArrow_navigatesBack() {
        var wentBack = false
        setScreen(onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(wentBack).isTrue()
    }

    @Test
    fun editAction_opensTheCardsForm() { // #8
        val edited = mutableListOf<Pair<String, String>>()
        setScreen(onEdit = { cardId, storeId -> edited += cardId to storeId })

        composeRule.onNodeWithContentDescription("Edit").performClick()

        assertThat(edited).containsExactly(card.id to card.store.id)
    }

    @Test
    fun aCardJustAdded_saysSo_andOffersToAddAnother() { // #6
        var addedAnother = false
        setScreen(added = true, onAddAnother = { addedAnother = true })

        composeRule.onNodeWithText("Card added").assertIsDisplayed()
        composeRule.onNodeWithText("Add another").performClick()

        assertThat(addedAnother).isTrue()
    }

    @Test
    fun aCardOpenedOtherwise_offersNoOtherCard() { // #6: from the list, or after an edit
        setScreen()

        composeRule.onNodeWithText("Card added").assertDoesNotExist()
        composeRule.onNodeWithText("Add another").assertDoesNotExist()
    }

    @Test
    fun theOffer_goesAway_andDoesNotComeBack_onceItHasTimedOut() { // #6
        val restorationTester = StateRestorationTester(composeRule)
        setScreen(added = true, restorationTester = restorationTester)
        composeRule.onNodeWithText("Card added").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MS + 1_000)
        composeRule.onNodeWithText("Card added").assertDoesNotExist()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Card added").assertDoesNotExist()
    }

    @Test
    fun theOffer_doesNotComeBack_afterAddAnother() { // #6: Back from the picker returns to this card
        val restorationTester = StateRestorationTester(composeRule)
        setScreen(added = true, restorationTester = restorationTester)
        composeRule.onNodeWithText("Add another").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Card added").assertDoesNotExist()
    }

    @Test
    fun theOffer_doesNotComeBack_afterEdit() { // review of #6: Back from the form returned to the card, offering again
        val restorationTester = StateRestorationTester(composeRule)
        setScreen(added = true, restorationTester = restorationTester)
        composeRule.onNodeWithText("Card added").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Card added").assertDoesNotExist()
    }

    @Test
    fun theOffer_waitsWhileScanModeIsOpen() { // review of #6: scan mode hid it while it timed out
        setScreen(added = true)
        composeRule.onNodeWithText("Card added").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Barcode").performTouchInput { doubleClick() }
        composeRule.onNodeWithTag("scanMode").assertExists()

        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MS + 1_000)
        composeRule.onNodeWithText("Add another").assertDoesNotExist()
        composeRule.onNodeWithTag("scanMode").performClick()

        composeRule.onNodeWithText("Card added").assertIsDisplayed()
    }

    @Test
    fun theOffer_isMadeAgain_whenTheScreenIsRecreatedWhileItShows() { // #6: the card screen turns with the phone
        val restorationTester = StateRestorationTester(composeRule)
        setScreen(added = true, restorationTester = restorationTester)
        composeRule.onNodeWithText("Card added").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Card added").assertIsDisplayed()
    }

    @Test
    fun editAction_isDisabledUntilTheCardHasLoaded() { // review of #8: an empty id would open a blank form
        setScreen(repository = FakeLoyaltyCardRepository(emptyList()))

        composeRule.onNodeWithContentDescription("Edit").assertIsNotEnabled()
    }

    @Test
    fun aDeleteThatFailed_isNotSaidAgain_whenTheDialogOpensNext() { // review of #8
        val repository = FakeLoyaltyCardRepository(listOf(card)).apply { deleteFailure = IllegalStateException("disk I/O error") }
        setScreen(repository = repository)
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("The card couldn't be deleted. Try again.").assertDoesNotExist()
        composeRule.onNodeWithText("It is removed from this phone for good.").assertIsDisplayed()
    }

    @Test
    fun deleteAction_asksFirst_andCancelKeepsTheCard() { // #8
        val repository = FakeLoyaltyCardRepository(listOf(card))
        var wentBack = false
        setScreen(repository = repository, onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete this card?").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete this card?").assertDoesNotExist()
        assertThat(repository.cards.value.map { it.id }).containsExactly(card.id)
        assertThat(wentBack).isFalse()
    }

    @Test
    fun confirmingDelete_deletesTheCard_andGoesBack() { // #8
        val repository = FakeLoyaltyCardRepository(listOf(card))
        var wentBack = false
        setScreen(repository = repository, onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { wentBack }
        assertThat(repository.cards.value).isEmpty()
    }

    @Test
    fun aDeleteThatFails_saysSo_andStaysOnTheCard() { // #8
        val repository = FakeLoyaltyCardRepository(listOf(card)).apply { deleteFailure = IllegalStateException("disk I/O error") }
        var wentBack = false
        setScreen(repository = repository, onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("The card couldn't be deleted. Try again.").assertIsDisplayed()
        assertThat(repository.cards.value.map { it.id }).containsExactly(card.id)
        assertThat(wentBack).isFalse()
    }

    @Test
    fun aTileOpeningTheCard_growsIntoTheScreen_beforeTheCardHasLoaded() { // #16: the key came from the loaded card
        val notLoadedYet = FakeLoyaltyCardRepository(emptyList())
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = card.id, color = "#336699")), notLoadedYet, FakeCardPhotos())
        var showDetail by mutableStateOf(false)
        val widths = mutableListOf<Int>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedContent(targetState = showDetail, label = "tile to detail") { detail ->
                    if (detail) {
                        DetailScreen(
                            cardId = card.id,
                            color = "#336699",
                            animatedVisibilityScope = this,
                            modifier = Modifier.onSizeChanged { widths += it.width },
                            directions = DetailDirections(toBack = {}, toEdit = { _, _ -> }, toAddCard = {}),
                            viewModel = viewModel,
                        )
                    } else {
                        CardItem(animatedVisibilityScope = this, card = card, onClick = {}, modifier = Modifier.width(TILE_SIZE))
                    }
                }
            }
        }

        composeRule.runOnIdle { showDetail = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(100)

        // A matched shared element starts at the list tile's bounds; an unmatched one would fill the screen at once.
        assertThat(viewModel.card.value).isEqualTo(LoyaltyCard.EMPTY)
        assertThat(widths.first()).isEqualTo(with(composeRule.density) { TILE_SIZE.roundToPx() })
    }

    @Test
    fun emptyStoreColor_doesNotCrash() { // #14
        setScreen(color = "")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }

    @Test
    fun malformedStoreColor_doesNotCrash() { // #14
        setScreen(color = "not a colour")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aStoresNewColour_isShownOnTheOpenCard_inTheBand() { // #226: the route's colour stayed
        val repository = FakeLoyaltyCardRepository(listOf(card))
        setScreen(color = "#336699", repository = repository)
        composeRule.waitForIdle()

        repository.cards.value = listOf(card.copy(store = card.store.copy(color = "#C62828")))
        composeRule.waitForIdle()

        assertThat(headerPixel()).isEqualTo(0xFFC62828.toInt())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun beforeTheCardHasLoaded_theBand_isInTheRoutesColour() { // the transition's first frames
        setScreen(color = "#336699", repository = FakeLoyaltyCardRepository(emptyList()))
        composeRule.waitForIdle()

        assertThat(headerPixel()).isEqualTo(0xFF336699.toInt())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun anEmptyStoredColour_fallsBackToSurfaceVariant() { // #14, #226
        setScreen(color = "#336699", card = card.copy(store = card.store.copy(color = "")))
        composeRule.waitForIdle()

        assertThat(headerPixel()).isEqualTo(BASELINE_LIGHT_SURFACE_VARIANT)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aMalformedStoredColour_fallsBackToSurfaceVariant() { // #14, #226
        setScreen(color = "#336699", card = card.copy(store = card.store.copy(color = "not a colour")))
        composeRule.waitForIdle()

        assertThat(headerPixel()).isEqualTo(BASELINE_LIGHT_SURFACE_VARIANT)
    }

    /** The band's top-left corner, clear of the Back button and of the logo or initials. */
    private fun headerPixel(): Int {
        val header = composeRule.onNodeWithTag(STORE_HEADER_TAG).captureToImage().toPixelMap()
        return header[4, 4].toArgb()
    }

    private companion object {
        val TILE_SIZE = 80.dp
        /** Material 3's baseline light `surfaceVariant` (the test isn't in the app's theme), written out by hand. */
        val BASELINE_LIGHT_SURFACE_VARIANT = 0xFFE7E0EC.toInt()
        /** `SnackbarDuration.Long` without accessibility services. */
        const val SNACKBAR_LONG_MS = 10_000L
    }
}
