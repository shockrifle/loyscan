package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoreSearchTest {

    private val apple = aStore(id = "apple", name = "Apple Market", aliases = listOf("AM"))
    private val berry = aStore(id = "berry", name = "berry shop")
    private val cafe = aStore(id = "cafe", name = "Café Ödön", aliases = listOf("Kávézó"))

    @Test
    fun blankQuery_returnsEveryStoreByName() {
        assertThat(listOf(cafe, berry, apple).search("  ").map { it.id }).containsExactly("apple", "berry", "cafe").inOrder()
    }

    @Test
    fun query_matchesAnyPartOfTheName_ignoringCase() {
        assertThat(listOf(apple, berry, cafe).search("MARK")).containsExactly(apple)
    }

    @Test
    fun query_matchesAnAlias() {
        assertThat(listOf(apple, berry, cafe).search("am")).containsExactly(apple)
    }

    @Test
    fun query_ignoresAccents_inTheQueryAndInTheStore() {
        assertThat(listOf(apple, berry, cafe).search("odon")).containsExactly(cafe)
        assertThat(listOf(apple, berry, cafe).search("kavezo")).containsExactly(cafe)
        assertThat(listOf(apple, berry, cafe).search("bérry")).containsExactly(berry)
    }

    @Test
    fun query_thatMatchesNothing_returnsNoStores() {
        assertThat(listOf(apple, berry, cafe).search("xyz")).isEmpty()
    }

    @Test
    fun results_areSortedByName_ignoringCaseAndAccents() {
        val ab = aStore(id = "ab", name = "Ábc")
        val ac = aStore(id = "ac", name = "acd")
        val aa = aStore(id = "aa", name = "Aaa")

        assertThat(listOf(ac, ab, aa).search("").map { it.id }).containsExactly("aa", "ab", "ac").inOrder()
    }

    // --- stores matching a scan (#6 step 5); example rules and made-up numbers ---

    /** 8 digits, the last a Luhn check digit (12345674 is valid). */
    private fun luhn8(format: BarcodeFormat, vararg starts: String) = CardNumberRule(
        id = "luhn8",
        format = format,
        minLength = 8,
        maxLength = 8,
        checkDigit = CardNumberRule.CheckDigit(CheckDigitAlgorithm.LUHN, CardNumberRule.CheckDigit.Mode.VERIFY),
        coreStartsWith = starts.toList(),
    )

    @Test
    fun matchingScan_returnsTheStoresWhoseRuleReadsIt_sameSymbologyFirst_thenByName() {
        val zeta = aStore(id = "zeta", name = "Zeta", cardNumberRule = luhn8(BarcodeFormat.CODE_128))
        val alpha = aStore(id = "alpha", name = "Alpha", cardNumberRule = luhn8(BarcodeFormat.CODE_128))
        val beta = aStore(id = "beta", name = "Beta", cardNumberRule = luhn8(BarcodeFormat.QR_CODE))
        val otherStart = aStore(id = "other", name = "Other", cardNumberRule = luhn8(BarcodeFormat.CODE_128, "9"))

        val matching = listOf(beta, zeta, otherStart, alpha).matchingScan("12345674", BarcodeFormat.CODE_128)

        assertThat(matching.map { it.id }).containsExactly("alpha", "zeta", "beta").inOrder()
    }

    @Test
    fun matchingScan_leavesOutStoresWithoutARule() {
        val noRule = aStore(id = "none", name = "No Rule")

        assertThat(listOf(noRule).matchingScan("12345674", BarcodeFormat.CODE_128)).isEmpty()
    }

    @Test
    fun matchingScan_aGs1ScanMatchesTheSameSymbologyWithoutTheMark_andTheReverse() { // #47
        val plain = aStore(id = "plain", name = "B Plain", cardNumberRule = luhn8(BarcodeFormat.CODE_128))
        val gs1 = aStore(id = "gs1", name = "C Gs1", cardNumberRule = luhn8(BarcodeFormat.GS1_128))
        val qr = aStore(id = "qr", name = "A Qr", cardNumberRule = luhn8(BarcodeFormat.QR_CODE))
        val stores = listOf(qr, plain, gs1)

        // Both Code 128 stores rank above the QR store, whichever has the GS1 mark.
        assertThat(stores.matchingScan("12345674", BarcodeFormat.GS1_128).map { it.id })
            .containsExactly("plain", "gs1", "qr").inOrder()
        assertThat(stores.matchingScan("12345674", BarcodeFormat.CODE_128).map { it.id })
            .containsExactly("plain", "gs1", "qr").inOrder()
    }
}
