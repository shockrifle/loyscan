package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Barcode content, display number and barcode format are derived from the number and the store, not stored (#1). */
class LoyaltyCardTest {

    /** Example rule: 9-digit printed number, barcode = EAN-13 "200" + number + check digit. */
    private val prefixedEan = CardNumberRule(
        id = "prefixed-ean",
        format = BarcodeFormat.EAN_13,
        minLength = 9,
        maxLength = 9,
        prefix = "200",
        checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, CheckDigit.Mode.APPEND),
        display = Display(pattern = "### ### ###"),
    )

    private val ruledStore = aStore(defaultBarcodeFormat = BarcodeFormat.EAN_13, cardNumberRule = prefixedEan)

    @Test
    fun barcodeContent_isBuiltByTheStoresRule() {
        val card = aLoyaltyCard(store = ruledStore, number = "123 456 789")

        assertThat(card.barcodeContent).isEqualTo("2001234567893")
    }

    @Test
    fun displayNumber_usesTheStoresDisplayPattern() {
        val card = aLoyaltyCard(store = ruledStore, number = "123456789")

        assertThat(card.displayNumber).isEqualTo("123 456 789")
    }

    @Test
    fun withoutARule_barcodeContentAndDisplayNumberAreTheTrimmedNumber() {
        val card = aLoyaltyCard(store = aStore(cardNumberRule = null), number = " AB-12 ")

        assertThat(card.barcodeContent).isEqualTo("AB-12")
        assertThat(card.displayNumber).isEqualTo("AB-12")
    }

    @Test
    fun scannedCard_barcodeContentIsTheScannedContentVerbatim() {
        // The rule would rebuild this 9-digit number as "200…"; a scan is what is really on the card.
        val card = aLoyaltyCard(store = ruledStore, number = "912345678", scannedFormat = BarcodeFormat.CODE_128)

        assertThat(card.barcodeContent).isEqualTo("912345678")
    }

    @Test
    fun scannedCard_displayNumberIsTheCoreTheRuleRecoversFromTheScan() {
        val card = aLoyaltyCard(store = ruledStore, number = "2001234567893", scannedFormat = BarcodeFormat.EAN_13)

        assertThat(card.displayNumber).isEqualTo("123 456 789")
    }

    @Test
    fun scannedCard_thatTheRuleDoesNotMatch_displaysTheScannedContent() {
        val card = aLoyaltyCard(store = ruledStore, number = "12345678", scannedFormat = BarcodeFormat.CODE_128)

        assertThat(card.displayNumber).isEqualTo("12345678")
    }

    @Test
    fun barcodeFormat_isTheScannedFormat_whenTheCardWasScanned() {
        val card = aLoyaltyCard(store = ruledStore, scannedFormat = BarcodeFormat.QR_CODE)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.QR_CODE)
    }

    @Test
    fun scannedGs1_128Card_isDrawnAsGs1_128WithTheScannedContent() { // #33: not as plain Code 128
        val card = aLoyaltyCard(store = ruledStore, number = "9799123456789012", scannedFormat = BarcodeFormat.GS1_128)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.GS1_128)
        assertThat(card.barcodeContent).isEqualTo("9799123456789012")
    }

    @Test
    fun barcodeFormat_isTheRulesFormat_whenNotScanned() {
        val store = aStore(defaultBarcodeFormat = BarcodeFormat.CODE_128, cardNumberRule = prefixedEan)
        val card = aLoyaltyCard(store = store, scannedFormat = null)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.EAN_13)
    }

    @Test
    fun barcodeFormat_isTheStoreDefault_whenNotScannedAndNoRule() {
        val card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.CODE_39), scannedFormat = null)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.CODE_39)
    }
}
