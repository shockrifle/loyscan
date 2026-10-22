package com.bdaniel.loyscan

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
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
    private val cards = FakeLoyaltyCardRepository()
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
    fun aScanTheStoreDoesNotUse_saysSo_andOffersSaveAnyway() {
        setScreen(CardForm("ruled", scannedContent = "ABC", scannedFormat = BarcodeFormat.QR_CODE))

        composeRule.onNodeWithText("This barcode doesn't look like a Ruled Store card.").assertIsDisplayed()
        composeRule.onNodeWithText("Save anyway").assertIsEnabled()
    }

    @Test
    fun aScan_showsItsNumberAndFormat() {
        setScreen(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.EAN_13))

        composeRule.onNodeWithTag(CARD_NUMBER_TAG).assertTextContains("1234 5674")
        composeRule.onNodeWithText("Scanned · EAN-13").assertIsDisplayed()
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
    fun back_goesBack() {
        setScreen(CardForm("ruled"))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    private fun setScreen(route: CardForm) {
        val viewModel = CardFormViewModel(
            SavedStateHandle(route = route),
            FakeStoreRepository(listOf(store)),
            cards,
            Clock.fixed(SOME_INSTANT, ZoneOffset.UTC),
        )
        val directions = CardFormDirections(toCard = { id, color -> opened += id to color }, toBack = { backs++ })
        composeRule.setContent { CardFormScreen(directions = directions, viewModel = viewModel) }
    }
}
