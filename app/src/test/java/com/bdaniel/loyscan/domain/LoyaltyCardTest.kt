package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Barcode content and display number are derived from the number, the store and the card's type (#1); the type is
 * stored (#187), and the store's rule applies only while the card is in the rule's type.
 */
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
    fun scannedGs1Card_displaysTheContentAsPrintedUnderGs1Barcodes() { // #42: no GS (U+001D) in the display number
        val content = "0109501101020917" + "1012AB\u001D" + "21XYZ"
        listOf(aStore(cardNumberRule = null), ruledStore).forEach { store ->
            val card = aLoyaltyCard(store = store, number = content, scannedFormat = BarcodeFormat.GS1_128)

            assertThat(card.displayNumber).isEqualTo("(01)09501101020917(10)12AB(21)XYZ")
            assertThat(card.barcodeContent).isEqualTo(content)
        }
    }

    @Test
    fun scannedDataBarCard_isGs1Data_too() {
        val card = aLoyaltyCard(store = aStore(cardNumberRule = null), number = "0109012345678901", scannedFormat = BarcodeFormat.DATABAR)

        assertThat(card.displayNumber).isEqualTo("(01)09012345678901")
    }

    @Test
    fun scannedGs1DataMatrixOrQrCard_isGs1Data_too() { // #45
        listOf(BarcodeFormat.GS1_DATA_MATRIX, BarcodeFormat.GS1_QR_CODE).forEach { format ->
            val card = aLoyaltyCard(store = aStore(cardNumberRule = null), number = "0109012345678901" + "10AB", scannedFormat = format)

            assertThat(card.displayNumber).isEqualTo("(01)09012345678901(10)AB")
        }
    }

    @Test
    fun scannedContentWithControlCharacters_displaysEachRunAsASpace() { // #42
        // Not GS1 (a Data Matrix not marked GS1), or marked GS1 but not a valid element string.
        listOf(
            Triple("AB\u001D\u001ECD\u001D", BarcodeFormat.DATA_MATRIX, "AB CD"),
            Triple("14AB\u001D21XY", BarcodeFormat.GS1_128, "14AB 21XY"),
        ).forEach { (content, format, shown) ->
            val card = aLoyaltyCard(store = aStore(cardNumberRule = null), number = content, scannedFormat = format)

            assertThat(card.displayNumber).isEqualTo(shown)
        }
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
    fun aTypedCard_isDrawnInTheTypeItWasSavedWith_notItsStoresDefault() { // #187
        val card = aLoyaltyCard(store = aStore(defaultBarcodeFormat = BarcodeFormat.CODE_39), barcodeFormat = BarcodeFormat.ITF)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.ITF)
    }

    @Test
    fun aTypedCardAtItsRulesType_isBuiltAndShownByTheRule() { // #187
        val card = aLoyaltyCard(store = ruledStore, number = "123456789", barcodeFormat = BarcodeFormat.EAN_13)

        assertThat(card.barcodeContent).isEqualTo("2001234567893")
        assertThat(card.displayNumber).isEqualTo("123 456 789")
    }

    @Test
    fun aTypedCardAtAnotherType_isDrawnAndShownAsTyped_withoutTheRule() { // #187
        val card = aLoyaltyCard(store = ruledStore, number = " 123-456789 ", barcodeFormat = BarcodeFormat.CODE_128)

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.CODE_128)
        assertThat(card.barcodeContent).isEqualTo("123-456789")
        assertThat(card.displayNumber).isEqualTo("123-456789")
    }
}
