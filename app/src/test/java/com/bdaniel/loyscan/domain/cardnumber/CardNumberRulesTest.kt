package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display
import com.bdaniel.loyscan.testing.rootCause
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardNumberRulesTest {

    @Test
    fun parse_fullRule() {
        val rules = CardNumberRules.parse(
            """
            [{
              "id": "prefixed-ean",
              "format": "EAN_13",
              "charset": "DIGITS",
              "minLength": 9,
              "maxLength": 9,
              "case": "NONE",
              "separators": "-./",
              "prefix": "200",
              "suffix": "",
              "padLength": 0,
              "padChar": "0",
              "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" },
              "coreStartsWith": ["12", "34"],
              "display": { "source": "CORE", "pattern": "### ### ###" }
            }]
            """
        )
        val expected = CardNumberRule(
            id = "prefixed-ean",
            format = BarcodeFormat.EAN_13,
            minLength = 9,
            maxLength = 9,
            case = CardNumberRule.LetterCase.NONE,
            separators = "-./",
            prefix = "200",
            checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, CheckDigit.Mode.APPEND),
            coreStartsWith = listOf("12", "34"),
            display = Display(Display.Source.CORE, "### ### ###"),
        )
        assertEquals(listOf(expected), rules)
    }

    @Test
    fun parse_minimalRuleUsesDefaults() {
        val rule = CardNumberRules.parse("""[{ "id": "plain", "format": "CODE_128" }]""").single()
        assertEquals(CardNumberRule(id = "plain", format = BarcodeFormat.CODE_128), rule)
        assertEquals("0012", CardNumberEngine.barcodeContent("00 12", rule))
        assertEquals(CardNumberRule.LetterCase.NONE, rule.case)
        assertEquals("-", rule.separators)
        val any = CardNumberRules.parse("""[{ "id": "any", "format": "QR_CODE", "charset": "ANY" }]""").single()
        assertEquals("", any.separators)
        assertEquals(emptyList<String>(), rule.coreStartsWith)
        assertEquals(0, rule.dropLeading)
    }

    @Test
    fun toJson_roundTrips() {
        val rules = listOf(
            CardNumberRule(id = "a", format = BarcodeFormat.QR_CODE, charset = CardNumberRule.Charset.ANY),
            CardNumberRule(id = "a2", format = BarcodeFormat.QR_CODE, charset = CardNumberRule.Charset.ANY, separators = "-"),
            CardNumberRule(id = "a3", format = BarcodeFormat.CODE_128, separators = ""),
            CardNumberRule(
                id = "b", format = BarcodeFormat.EAN_8, minLength = 7, maxLength = 7,
                checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, CheckDigit.Mode.APPEND),
            ),
            CardNumberRule(id = "c", format = BarcodeFormat.CODE_128, coreStartsWith = listOf("99", "123")),
            CardNumberRule(
                id = "d", format = BarcodeFormat.GS1_128, minLength = 18, maxLength = 18, prefix = "9799",
                coreStartsWith = listOf("634009"), dropLeading = 6,
            ),
        )
        assertEquals(rules, CardNumberRules.parse(CardNumberRules.toJson(rules)))
    }

    @Test
    fun parse_rejectsUnknownKeysAndValues() {
        assertThrows(SerializationException::class.java) {
            CardNumberRules.parse("""[{ "id": "x", "format": "EAN_13", "minLenght": 9 }]""")
        }
        assertThrows(SerializationException::class.java) {
            CardNumberRules.parse("""[{ "id": "x", "format": "EAN_14" }]""")
        }
        assertThrows(SerializationException::class.java) {
            CardNumberRules.parse("""[{ "id": "x" }]""")
        }
    }

    @Test
    fun parse_rejectsInvalidRuleDefinitions() {
        val invalid = listOf(
            """{ "id": "", "format": "CODE_128" }""",
            """{ "id": "x", "format": "CODE_128", "minLength": 9, "maxLength": 8 }""",
            """{ "id": "x", "format": "CODE_128", "minLength": 0 }""",
            """{ "id": "x", "format": "CODE_128", "prefix": "A1" }""",
            """{ "id": "x", "format": "CODE_128", "padLength": 10, "padChar": "X" }""",
            """{ "id": "x", "format": "CODE_128", "separators": "-1" }""",
            """{ "id": "x", "format": "CODE_128", "charset": "ALPHANUMERIC",
                 "checkDigit": { "algorithm": "LUHN", "mode": "APPEND" } }""",
            """{ "id": "x", "format": "CODE_128", "maxLength": 1,
                 "checkDigit": { "algorithm": "LUHN", "mode": "VERIFY" } }""",
            """{ "id": "x", "format": "CODE_128", "display": { "pattern": "    " } }""",
            """{ "id": "x", "format": "CODE_128", "coreStartsWith": [""] }""",
            """{ "id": "x", "format": "CODE_128", "coreStartsWith": ["9A"] }""",
            """{ "id": "x", "format": "CODE_128", "maxLength": 3, "coreStartsWith": ["1234"] }""",
            // input is upper-cased, so a lower-case start could never match
            """{ "id": "x", "format": "CODE_128", "charset": "ALPHANUMERIC", "case": "UPPER", "coreStartsWith": ["ab"] }""",
            """{ "id": "x", "format": "CODE_128", "dropLeading": -1 }""",
            // the dropped digits must be known to read a scan back: a required start has to supply them
            """{ "id": "x", "format": "CODE_128", "minLength": 9, "dropLeading": 2 }""",
            """{ "id": "x", "format": "CODE_128", "minLength": 9, "coreStartsWith": ["12", "3"], "dropLeading": 2 }""",
            // two starts that drop different digits: a scan couldn't tell which were dropped
            """{ "id": "x", "format": "CODE_128", "minLength": 9, "coreStartsWith": ["12", "34"], "dropLeading": 2 }""",
            // nothing would be left of the core
            """{ "id": "x", "format": "CODE_128", "minLength": 2, "maxLength": 9, "coreStartsWith": ["12"], "dropLeading": 2 }""",
        )
        for (rule in invalid) {
            val e = assertThrows(rule, IllegalArgumentException::class.java) { CardNumberRules.parse("[$rule]") }
            // Make sure a rule check (require) fired, not a JSON error: a JSON error is a SerializationException, a
            // subclass. It checks the root cause, as a kotlinx.serialization version may wrap the check (1.12 did).
            assertEquals(rule, IllegalArgumentException::class.java, e.rootCause.javaClass)
        }
    }

    @Test
    fun parse_acceptsRulesThatFitTheirFormat() {
        val valid = listOf(
            // printed 12 digits, check digit appended
            """{ "id": "ean13-append", "format": "EAN_13", "minLength": 12, "maxLength": 12,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" } }""",
            // full EAN printed on the card
            """{ "id": "ean13-verify", "format": "EAN_13", "minLength": 13, "maxLength": 13,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "VERIFY" } }""",
            // variable-length number padded to a fixed width
            """{ "id": "ean8-padded", "format": "EAN_8", "minLength": 3, "maxLength": 6, "prefix": "2", "padLength": 6,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" } }""",
            """{ "id": "upca", "format": "UPC_A", "minLength": 11, "maxLength": 11,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" } }""",
            """{ "id": "upce", "format": "UPC_E", "minLength": 8, "maxLength": 8 }""",
            """{ "id": "itf", "format": "ITF", "minLength": 14, "maxLength": 14 }""",
            """{ "id": "code128-any-length", "format": "CODE_128" }""",
            // 15 printed digits, the first three left out, check digit appended: 13 digits
            """{ "id": "ean13-dropped", "format": "EAN_13", "minLength": 15, "maxLength": 15,
                 "coreStartsWith": ["123"], "dropLeading": 3,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" } }""",
        )
        assertEquals(valid.size, CardNumberRules.parse(valid.joinToString(",", "[", "]")).size)
    }

    @Test
    fun parse_rejectsRulesThatDontFitTheirFormat() {
        val invalid = listOf(
            // no fixed length
            """{ "id": "x", "format": "EAN_13" }""",
            // 12 digits without a check digit appended
            """{ "id": "x", "format": "EAN_13", "minLength": 12, "maxLength": 12 }""",
            // right length, wrong algorithm
            """{ "id": "x", "format": "EAN_13", "minLength": 13, "maxLength": 13,
                 "checkDigit": { "algorithm": "LUHN", "mode": "VERIFY" } }""",
            // VERIFY only covers the core, not the prefix
            """{ "id": "x", "format": "EAN_13", "minLength": 12, "maxLength": 12, "prefix": "2",
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "VERIFY" } }""",
            // variable length without padding
            """{ "id": "x", "format": "EAN_8", "minLength": 6, "maxLength": 7,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" } }""",
            """{ "id": "x", "format": "EAN_8", "charset": "ALPHANUMERIC", "minLength": 8, "maxLength": 8 }""",
            """{ "id": "x", "format": "UPC_E", "minLength": 7, "maxLength": 7 }""",
            """{ "id": "x", "format": "ITF", "minLength": 13, "maxLength": 13 }""",
            """{ "id": "x", "format": "ITF", "minLength": 12, "maxLength": 14 }""",
            // 13 printed digits, two left out: 11
            """{ "id": "x", "format": "EAN_13", "minLength": 13, "maxLength": 13, "coreStartsWith": ["12"], "dropLeading": 2,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "VERIFY" } }""",
            // VERIFY covers the printed number, not what is left of it in the barcode
            """{ "id": "x", "format": "EAN_13", "minLength": 15, "maxLength": 15, "coreStartsWith": ["12"], "dropLeading": 2,
                 "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "VERIFY" } }""",
        )
        for (rule in invalid) {
            val e = assertThrows(rule, IllegalArgumentException::class.java) { CardNumberRules.parse("[$rule]") }
            assertEquals(rule, IllegalArgumentException::class.java, e.rootCause.javaClass)
        }
    }

    @Test
    fun parse_rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            CardNumberRules.parse("""[{ "id": "x", "format": "CODE_128" }, { "id": "x", "format": "QR_CODE" }]""")
        }
    }
}
