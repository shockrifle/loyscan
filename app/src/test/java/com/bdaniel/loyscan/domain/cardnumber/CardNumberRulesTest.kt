package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.Display
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )
        for (rule in invalid) {
            val e = assertThrows(rule, IllegalArgumentException::class.java) { CardNumberRules.parse("[$rule]") }
            // SerializationException extends IllegalArgumentException; make sure a rule check fired, not a JSON error.
            assertFalse(rule, e is SerializationException)
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
        )
        for (rule in invalid) {
            val e = assertThrows(rule, IllegalArgumentException::class.java) { CardNumberRules.parse("[$rule]") }
            assertFalse(rule, e is SerializationException)
        }
    }

    @Test
    fun parse_rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            CardNumberRules.parse("""[{ "id": "x", "format": "CODE_128" }, { "id": "x", "format": "QR_CODE" }]""")
        }
    }
}
