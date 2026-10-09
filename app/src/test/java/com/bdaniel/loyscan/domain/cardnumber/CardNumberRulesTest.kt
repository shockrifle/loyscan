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
            display = Display(Display.Source.CORE, "### ### ###"),
        )
        assertEquals(listOf(expected), rules)
    }

    @Test
    fun parse_minimalRuleUsesDefaults() {
        val rule = CardNumberRules.parse("""[{ "id": "plain", "format": "CODE_128" }]""").single()
        assertEquals(CardNumberRule(id = "plain", format = BarcodeFormat.CODE_128), rule)
        assertEquals("0012", CardNumberEngine.barcodeContent("00 12", rule))
    }

    @Test
    fun toJson_roundTrips() {
        val rules = listOf(
            CardNumberRule(id = "a", format = BarcodeFormat.QR_CODE, charset = CardNumberRule.Charset.ANY, separators = ""),
            CardNumberRule(
                id = "b", format = BarcodeFormat.EAN_8, minLength = 7, maxLength = 7,
                checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, CheckDigit.Mode.APPEND),
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
            """{ "id": "", "format": "EAN_13" }""",
            """{ "id": "x", "format": "EAN_13", "minLength": 9, "maxLength": 8 }""",
            """{ "id": "x", "format": "EAN_13", "minLength": 0 }""",
            """{ "id": "x", "format": "EAN_13", "prefix": "A1" }""",
            """{ "id": "x", "format": "EAN_13", "padLength": 10, "padChar": "X" }""",
            """{ "id": "x", "format": "EAN_13", "separators": "-1" }""",
            """{ "id": "x", "format": "CODE_128", "charset": "ALPHANUMERIC",
                 "checkDigit": { "algorithm": "LUHN", "mode": "APPEND" } }""",
            """{ "id": "x", "format": "EAN_13", "maxLength": 1,
                 "checkDigit": { "algorithm": "LUHN", "mode": "VERIFY" } }""",
            """{ "id": "x", "format": "EAN_13", "display": { "pattern": "    " } }""",
        )
        for (rule in invalid) {
            val e = assertThrows(rule, IllegalArgumentException::class.java) { CardNumberRules.parse("[$rule]") }
            // SerializationException extends IllegalArgumentException; make sure a rule check fired, not a JSON error.
            assertFalse(rule, e is SerializationException)
        }
    }

    @Test
    fun parse_rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            CardNumberRules.parse("""[{ "id": "x", "format": "EAN_13" }, { "id": "x", "format": "EAN_8" }]""")
        }
    }
}
