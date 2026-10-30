package com.bdaniel.loyscan

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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

    private fun setScreen(
        color: String = "#336699",
        card: LoyaltyCard = this.card,
        repository: FakeLoyaltyCardRepository = FakeLoyaltyCardRepository(listOf(card)),
        onBack: () -> Unit = {},
        onEdit: (cardId: String, storeId: String) -> Unit = { _, _ -> },
        added: Boolean = false,
        onAddAnother: () -> Unit = {},
        restorationTester: StateRestorationTester? = null,
    ) {
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = card.id, color = color, added = added)), repository)
        val content = @Composable {
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
        if (restorationTester != null) restorationTester.setContent(content) else composeRule.setContent(content)
    }

    @Test
    fun showsTheCardLabelAndNumber() {
        setScreen()

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertIsDisplayed()
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

        composeRule.waitUntil { wentBack }
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
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = card.id, color = "#336699")), notLoadedYet)
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

    private companion object {
        val TILE_SIZE = 80.dp
        /** `SnackbarDuration.Long` without accessibility services. */
        const val SNACKBAR_LONG_MS = 10_000L
    }
}
