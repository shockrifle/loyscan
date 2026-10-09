package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine.barcodeContent
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine.displayNumber
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine.match
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine.validate
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit.Mode
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display
import com.bdaniel.loyscan.domain.cardnumber.Validation.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Example rules only — not real stores. Real store rules are tested where the catalogue (#3) is. */
class CardNumberEngineTest {

    /** 9-digit printed number, barcode = EAN-13 "200" + number + check digit. */
    private val prefixedEan = CardNumberRule(
        id = "prefixed-ean",
        format = BarcodeFormat.EAN_13,
        minLength = 9,
        maxLength = 9,
        prefix = "200",
        checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, Mode.APPEND),
        display = Display(pattern = "### ### ###"),
    )

    /** Alphanumeric, zero-padded to 10 behind an "LC" prefix. */
    private val paddedCode128 = CardNumberRule(
        id = "padded-code128",
        format = BarcodeFormat.CODE_128,
        charset = CardNumberRule.Charset.ALPHANUMERIC,
        minLength = 4,
        maxLength = 10,
        prefix = "LC",
        padLength = 10,
    )

    /** Printed number already carries a Luhn check digit; shown in groups of four. */
    private val luhnVerified = CardNumberRule(
        id = "luhn-verified",
        format = BarcodeFormat.QR_CODE,
        minLength = 10,
        maxLength = 16,
        checkDigit = CheckDigit(CheckDigitAlgorithm.LUHN, Mode.VERIFY),
        display = Display(Display.Source.BARCODE, "#### #### #### ####"),
    )

    // --- no rule ---

    @Test
    fun noRule_barcodeAndDisplayAreTheRawNumber() {
        assertEquals("12 34-ab", barcodeContent("  12 34-ab ", null))
        assertEquals("12 34-ab", displayNumber("  12 34-ab ", null))
        assertEquals(Validation.Valid("12 34-ab"), validate(" 12 34-ab", null))
    }

    @Test
    fun noRule_emptyIsInvalid() {
        assertEquals(Validation.Invalid("", setOf(Error.EMPTY)), validate("   ", null))
    }

    // --- validation ---

    @Test
    fun validate_normalisesSeparatorsAndCase() {
        assertEquals(Validation.Valid("123456789"), validate("123 456-789", prefixedEan))
        assertEquals(Validation.Valid("AB12"), validate("ab-12", paddedCode128))
    }

    @Test
    fun validate_reportsLengthAndCharsetErrors() {
        assertEquals(setOf(Error.TOO_SHORT), (validate("12345678", prefixedEan) as Validation.Invalid).errors)
        assertEquals(setOf(Error.TOO_LONG), (validate("1234567890", prefixedEan) as Validation.Invalid).errors)
        assertEquals(setOf(Error.INVALID_CHARACTERS), (validate("12345678X", prefixedEan) as Validation.Invalid).errors)
        assertEquals(setOf(Error.INVALID_CHARACTERS), (validate("AB_12", paddedCode128) as Validation.Invalid).errors)
        assertEquals(setOf(Error.EMPTY), (validate(" - ", prefixedEan) as Validation.Invalid).errors)
    }

    @Test
    fun validate_verifiesCheckDigitOnlyWhenOtherwiseValid() {
        assertEquals(Validation.Valid("4111111111111111"), validate("4111 1111 1111 1111", luhnVerified))
        assertEquals(setOf(Error.BAD_CHECK_DIGIT), (validate("4111111111111112", luhnVerified) as Validation.Invalid).errors)
        assertEquals(setOf(Error.TOO_SHORT), (validate("411", luhnVerified) as Validation.Invalid).errors)
    }

    // --- barcode content ---

    @Test
    fun barcodeContent_prefixAndAppendedCheckDigit() {
        assertEquals("2001234567893", barcodeContent("123 456 789", prefixedEan))
    }

    @Test
    fun barcodeContent_padding() {
        assertEquals("LC000000AB12", barcodeContent("ab12", paddedCode128))
        assertEquals("LCABCDEF1234", barcodeContent("abcdef1234", paddedCode128))
    }

    @Test
    fun barcodeContent_verifyModeAddsNothing() {
        assertEquals("4111111111111111", barcodeContent("4111-1111-1111-1111", luhnVerified))
    }

    @Test
    fun barcodeContent_invalidInputFallsBackToNormalisedNumber() {
        assertEquals("12345", barcodeContent("123 45", prefixedEan))
    }

    // --- display ---

    @Test
    fun displayNumber_appliesPatternToCore() {
        assertEquals("123 456 789", displayNumber("123456789", prefixedEan))
    }

    @Test
    fun displayNumber_appliesPatternToBarcodeContent() {
        assertEquals("4111 1111 1111 1111", displayNumber("4111111111111111", luhnVerified))
    }

    @Test
    fun displayNumber_shortInputDropsTrailingLiterals_longInputKeepsRemainder() {
        assertEquals("123 45", displayNumber("12345", prefixedEan))
        assertEquals("123 456 789012", displayNumber("123456789012", prefixedEan))
    }

    @Test
    fun displayNumber_withoutPatternIsNormalisedCore() {
        assertEquals("AB12", displayNumber("ab 12", paddedCode128))
    }

    // --- reverse match ---

    @Test
    fun match_roundTripsEveryRule() {
        val cases = mapOf(
            prefixedEan to listOf("123456789", "000000001"),
            paddedCode128 to listOf("AB12", "ABCDEF1234", "A0B1C2"),
            luhnVerified to listOf("4111111111111111", "79927398713"),
        )
        for ((rule, cores) in cases) for (core in cores) {
            assertEquals("${rule.id} / $core", RuleMatch(core, formatMatches = true), match(barcodeContent(core, rule), rule.format, rule))
        }
    }

    @Test
    fun match_rejectsContentTheRuleCannotProduce() {
        assertNull(match("2001234567890", null, prefixedEan))   // wrong check digit
        assertNull(match("4001234567891", null, prefixedEan))   // wrong prefix (valid EAN, though)
        assertNull(match("20012345678", null, prefixedEan))     // too short
        assertNull(match("XX000000AB12", null, paddedCode128))  // wrong prefix
        assertNull(match("LC00000AB12", null, paddedCode128))   // padding width wrong
        assertNull(match("4111111111111112", null, luhnVerified))
    }

    @Test
    fun match_reportsFormatAgreement() {
        assertEquals(RuleMatch("123456789", formatMatches = true), match("2001234567893", null, prefixedEan))
        assertEquals(RuleMatch("123456789", formatMatches = false), match("2001234567893", BarcodeFormat.CODE_128, prefixedEan))
    }

    @Test
    fun match_ambiguousPaddingYieldsShortestCore() {
        // "00AB" padded is indistinguishable from "AB" padded; minLength 4 stops stripping at "00AB".
        assertEquals("00AB", match("LC00000000AB", null, paddedCode128)?.core)
    }

    @Test
    fun match_isCaseSensitive() {
        // The card would otherwise be saved as "AB12" and draw "LC000000AB12", not what was scanned.
        assertNull(match("lc000000ab12", BarcodeFormat.CODE_128, paddedCode128))
    }

    @Test
    fun match_acceptsOwnBarcodeWhenPrefixCaseDiffersFromRuleCase() {
        val rule = paddedCode128.copy(id = "lowercase-prefix", prefix = "lc")
        assertEquals(RuleMatch("AB12", formatMatches = true), match(barcodeContent("ab12", rule), BarcodeFormat.CODE_128, rule))
    }

    @Test
    fun match_triesLongerCoresWhenCoreStartsWithPadChar() {
        // Core "9123458" ends in a GTIN check digit; stripping every leading '9' would leave "123458", which fails it.
        val rule = CardNumberRule(
            id = "nine-padded",
            format = BarcodeFormat.CODE_128,
            minLength = 6,
            maxLength = 10,
            padLength = 10,
            padChar = '9',
            checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, Mode.VERIFY),
        )
        val core = "912345" + CheckDigitAlgorithm.GTIN_MOD10.compute("912345")
        assertEquals("9999$core".takeLast(10), barcodeContent(core, rule))
        assertEquals(core, match(barcodeContent(core, rule), null, rule)?.core)
    }
}
