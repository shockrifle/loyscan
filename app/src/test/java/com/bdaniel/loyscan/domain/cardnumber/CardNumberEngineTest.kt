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

    /** Alphanumeric, upper-cased, zero-padded to 10 behind an "LC" prefix. */
    private val paddedCode128 = CardNumberRule(
        id = "padded-code128",
        format = BarcodeFormat.CODE_128,
        charset = CardNumberRule.Charset.ALPHANUMERIC,
        case = CardNumberRule.LetterCase.UPPER,
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

    @Test
    fun validate_defaultsKeepCase_andAnyCharsetKeepsHyphens() {
        val alnum = CardNumberRule(id = "alnum", format = BarcodeFormat.CODE_128, charset = CardNumberRule.Charset.ALPHANUMERIC)
        assertEquals(Validation.Valid("Ab12"), validate("Ab-12", alnum))
        val any = CardNumberRule(id = "any", format = BarcodeFormat.QR_CODE, charset = CardNumberRule.Charset.ANY)
        assertEquals("AB-1234xy", barcodeContent(" AB-1234xy ", any))
    }

    // --- required start (#32) ---

    /** Two made-up stores whose full printed EAN-13 is the core; they differ only in their leading digits. */
    private val eanStoreA = CardNumberRule(
        id = "ean-store-a",
        format = BarcodeFormat.EAN_13,
        minLength = 13,
        maxLength = 13,
        checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, Mode.VERIFY),
        coreStartsWith = listOf("999"),
    )
    private val eanStoreB = eanStoreA.copy(id = "ean-store-b", coreStartsWith = listOf("994", "995"))

    private fun ean(first12: String) = first12 + CheckDigitAlgorithm.GTIN_MOD10.compute(first12)

    @Test
    fun validate_coreMustStartWithAnAllowedStart() {
        assertEquals(Validation.Valid(ean("999000000001")), validate(ean("999000000001"), eanStoreA))
        assertEquals(Validation.Valid(ean("995000000001")), validate(ean("995000000001"), eanStoreB))
        assertEquals(setOf(Error.WRONG_START), (validate(ean("400000000001"), eanStoreA) as Validation.Invalid).errors)
        assertEquals(setOf(Error.WRONG_START), (validate(ean("999000000001"), eanStoreB) as Validation.Invalid).errors)
    }

    @Test
    fun validate_partialInputThatCanStillReachAnAllowedStartIsOnlyTooShort() {
        assertEquals(setOf(Error.TOO_SHORT), (validate("99", eanStoreA) as Validation.Invalid).errors)
        assertEquals(setOf(Error.TOO_SHORT, Error.WRONG_START), (validate("98", eanStoreA) as Validation.Invalid).errors)
    }

    @Test
    fun validate_coreThatIsOnlyPartOfAnAllowedStartIsTooShortWhateverMinLength() {
        // minLength 1 < start length 3: "12" can't be valid, but it is still on the way to "123", not a wrong start.
        val rule = CardNumberRule(id = "short-min", format = BarcodeFormat.CODE_128, coreStartsWith = listOf("99", "123"))

        assertEquals(setOf(Error.TOO_SHORT), (validate("12", rule) as Validation.Invalid).errors)
        assertEquals(setOf(Error.TOO_SHORT), (validate("1", rule) as Validation.Invalid).errors)
        assertEquals(setOf(Error.WRONG_START), (validate("13", rule) as Validation.Invalid).errors)
        assertNull(match("12", BarcodeFormat.CODE_128, rule))
        assertEquals(Validation.Valid("1234"), validate("1234", rule))
        assertEquals(Validation.Valid("99"), validate("99", rule))
    }

    @Test
    fun validate_requiredStartAppliesToTheCoreNotTheBarcodeContent() {
        val rule = prefixedEan.copy(id = "prefixed-start", coreStartsWith = listOf("12"))
        assertEquals(Validation.Valid("123456789"), validate("123456789", rule))
        assertEquals(setOf(Error.WRONG_START), (validate("200456789", rule) as Validation.Invalid).errors)
        assertEquals(RuleMatch("123456789", formatMatches = true), match("2001234567893", BarcodeFormat.EAN_13, rule))
    }

    @Test
    fun match_scannedEanOfOneStoreDoesNotMatchAnotherStoresRule() {
        val scannedA = ean("999123456789")
        val scannedB = ean("994123456789")

        assertEquals(RuleMatch(scannedA, formatMatches = true), match(scannedA, BarcodeFormat.EAN_13, eanStoreA))
        assertNull(match(scannedA, BarcodeFormat.EAN_13, eanStoreB))
        assertEquals(RuleMatch(scannedB, formatMatches = true), match(scannedB, BarcodeFormat.EAN_13, eanStoreB))
        assertNull(match(scannedB, BarcodeFormat.EAN_13, eanStoreA))
    }

    // --- dropped leading digits (#43) ---

    /**
     * A made-up store whose card prints 12 digits starting "4321" (the last one a Luhn check digit), and whose
     * barcode holds "77" and the printed number without its first four digits.
     */
    private val droppedStart = CardNumberRule(
        id = "dropped-start",
        format = BarcodeFormat.GS1_128,
        minLength = 12,
        maxLength = 12,
        prefix = "77",
        checkDigit = CheckDigit(CheckDigitAlgorithm.LUHN, Mode.VERIFY),
        coreStartsWith = listOf("4321"),
        dropLeading = 4,
        display = Display(pattern = "#### #### ####"),
    )

    private fun luhn(payload: String) = payload + CheckDigitAlgorithm.LUHN.compute(payload)

    @Test
    fun barcodeContent_leavesOutTheDroppedLeadingDigits() {
        val printed = luhn("43210000123")

        assertEquals(Validation.Valid(printed), validate(printed, droppedStart))
        assertEquals("77" + printed.drop(4), barcodeContent(printed, droppedStart))
        assertEquals("4321 0000 123" + printed.last(), displayNumber(printed, droppedStart))
    }

    @Test
    fun match_putsTheDroppedDigitsBackFromTheRequiredStart() {
        val printed = luhn("43210000123")

        assertEquals(RuleMatch(printed, formatMatches = true), match("77" + printed.drop(4), BarcodeFormat.GS1_128, droppedStart))
    }

    @Test
    fun match_withDroppedDigits_stillChecksTheFullPrintedNumber() {
        val printed = luhn("43210000123")
        val wrongCheck = printed.dropLast(1) + ((printed.last() - '0' + 1) % 10)

        assertNull(match("77" + wrongCheck.drop(4), null, droppedStart))  // Luhn is over all 12 printed digits
        assertNull(match("78" + printed.drop(4), null, droppedStart))     // wrong prefix
        assertNull(match("77" + printed.drop(3), null, droppedStart))     // one digit too many
    }

    @Test
    fun match_withDroppedDigits_triesEachAllowedStart() {
        // The starts share the four dropped digits and differ in the fifth, which stays in the barcode.
        val rule = droppedStart.copy(id = "two-starts", coreStartsWith = listOf("43215", "43216"))
        val printed = luhn("43216000123")

        assertEquals(printed, match(barcodeContent(printed, rule), null, rule)?.core)
        assertNull(match(barcodeContent(luhn("43217000123"), droppedStart), null, rule))
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
    fun match_theGs1MarkIsTheSameFormat_eitherWay() { // #47
        val pairs = listOf(
            Triple(paddedCode128, "LC000000AB12", BarcodeFormat.GS1_128),
            Triple(luhnVerified, "4111111111111111", BarcodeFormat.GS1_QR_CODE),
            Triple(luhnVerified.copy(format = BarcodeFormat.DATA_MATRIX), "4111111111111111", BarcodeFormat.GS1_DATA_MATRIX),
        )
        for ((plainRule, content, gs1Format) in pairs) {
            val gs1Rule = plainRule.copy(format = gs1Format)
            assertEquals(gs1Format.name, true, match(content, gs1Format, plainRule)?.formatMatches)
            assertEquals(gs1Format.name, true, match(content, plainRule.format, gs1Rule)?.formatMatches)
        }
        // Another symbology still differs, GS1 or not.
        assertEquals(false, match("LC000000AB12", BarcodeFormat.GS1_QR_CODE, paddedCode128)?.formatMatches)
        assertEquals(false, match("4111111111111111", BarcodeFormat.DATABAR, luhnVerified.copy(format = BarcodeFormat.GS1_128))?.formatMatches)
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
