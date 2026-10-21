package com.bdaniel.loyscan

import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
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
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalSharedTransitionApi::class)
// A typical phone: Robolectric's default 320x470dp screen is shorter than the header, barcode and number together.
@Config(qualifiers = "w411dp-h891dp")
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val card = aLoyaltyCard(label = "Gold card", store = aStore(name = "Test Store"), number = "0042")

    private val keepsScreenOn get() = composeRule.activity.window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0

    private fun setScreen(color: String = "#336699", card: LoyaltyCard = this.card, onBack: () -> Unit = {}) {
        val viewModel = DetailViewModel(
            SavedStateHandle(route = Detail(cardId = card.id, color = color)),
            FakeLoyaltyCardRepository(listOf(card)),
        )
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    DetailScreen(
                        color = color,
                        animatedVisibilityScope = this,
                        directions = DetailDirections(toBack = onBack),
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    @Test
    fun showsTheCardLabelAndNumber() {
        setScreen()

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertIsDisplayed()
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
        composeRule.waitUntil { clipboard.primaryClip != null }
        assertThat(clipboard.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("123 456 789")
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
    fun emptyStoreColor_doesNotCrash() { // #14
        setScreen(color = "")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }

    @Test
    fun malformedStoreColor_doesNotCrash() { // #14
        setScreen(color = "not a colour")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }
}
