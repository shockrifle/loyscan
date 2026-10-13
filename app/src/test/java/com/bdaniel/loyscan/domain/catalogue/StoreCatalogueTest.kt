package com.bdaniel.loyscan.domain.catalogue

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule.CheckDigit
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class StoreCatalogueTest {

    @Test
    fun parse_fullEntry() {
        val entries = StoreCatalogue.parse(
            """
            [{
              "id": "hu-example",
              "name": "Example Store",
              "aliases": ["Example Club", "exmpl"],
              "countryCodes": ["HU", "AT"],
              "color": "#1A2B3C",
              "defaultBarcodeFormat": "EAN_13",
              "cardNumberRule": {
                "id": "hu-example", "format": "EAN_13", "minLength": 9, "maxLength": 9, "prefix": "200",
                "checkDigit": { "algorithm": "GTIN_MOD10", "mode": "APPEND" }
              }
            }]
            """
        )

        assertThat(entries).containsExactly(
            CatalogueEntry(
                id = "hu-example",
                name = "Example Store",
                aliases = listOf("Example Club", "exmpl"),
                countryCodes = listOf("HU", "AT"),
                color = "#1A2B3C",
                defaultBarcodeFormat = BarcodeFormat.EAN_13,
                cardNumberRule = CardNumberRule(
                    id = "hu-example",
                    format = BarcodeFormat.EAN_13,
                    minLength = 9,
                    maxLength = 9,
                    prefix = "200",
                    checkDigit = CheckDigit(CheckDigitAlgorithm.GTIN_MOD10, CheckDigit.Mode.APPEND),
                ),
            )
        )
    }

    @Test
    fun parse_minimalEntry_hasNoAliasesAndNoRule() {
        val entry = StoreCatalogue.parse(
            """[{ "id": "hu-plain", "name": "Plain", "countryCodes": ["HU"], "color": "#000000",
                  "defaultBarcodeFormat": "CODE_128" }]"""
        ).single()

        assertThat(entry.aliases).isEmpty()
        assertThat(entry.cardNumberRule).isNull()
    }

    @Test
    fun parse_emptyCatalogue() {
        assertThat(StoreCatalogue.parse("[]")).isEmpty()
    }

    @Test
    fun parse_rejectsUnknownKeysAndValues() {
        val invalid = listOf(
            // a typo in a key
            """{ "id": "hu-x", "name": "X", "countryCodes": ["HU"], "colour": "#000000", "defaultBarcodeFormat": "CODE_128" }""",
            // logos are not part of the catalogue
            """{ "id": "hu-x", "name": "X", "countryCodes": ["HU"], "color": "#000000", "defaultBarcodeFormat": "CODE_128",
                 "logo": "https://example.com/x.png" }""",
            """{ "id": "hu-x", "name": "X", "countryCodes": ["HU"], "color": "#000000", "defaultBarcodeFormat": "GS1_128" }""",
            // a missing required field
            """{ "id": "hu-x", "name": "X", "countryCodes": ["HU"], "color": "#000000" }""",
        )
        for (entry in invalid) {
            assertThrows(entry, SerializationException::class.java) { StoreCatalogue.parse("[$entry]") }
        }
    }

    @Test
    fun parse_rejectsInvalidEntries() {
        val invalid = listOf(
            // id: country-prefixed slug of lowercase letters, digits and single hyphens
            entry(id = "tesco"),
            entry(id = "hu-Tesco"),
            entry(id = "hu-ikea--family"),
            entry(id = "hu-ikea-"),
            entry(id = "hu-ikea family"),
            // the id's country must be one of the entry's countries
            entry(id = "at-x", countryCodes = """["HU"]"""),
            entry(countryCodes = "[]"),
            entry(countryCodes = """["hu"]"""),
            entry(countryCodes = """["HUN"]"""),
            entry(name = " "),
            entry(color = "#12345"),
            entry(color = "#12345G"),
            entry(color = "red"),
            entry(aliases = """[" "]"""),
            // the rule belongs to this store
            entry(rule = """{ "id": "hu-other", "format": "CODE_128" }"""),
        )
        for (entry in invalid) {
            val e = assertThrows(entry, IllegalArgumentException::class.java) { StoreCatalogue.parse("[$entry]") }
            // SerializationException extends IllegalArgumentException; make sure a check fired, not a JSON error.
            assertFalse(entry, e is SerializationException)
        }
    }

    @Test
    fun parse_rejectsDuplicateIds() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            StoreCatalogue.parse("[${entry()}, ${entry(name = "Again")}]")
        }

        assertThat(e).hasMessageThat().contains("hu-x")
    }

    private fun entry(
        id: String = "hu-x",
        name: String = "X",
        countryCodes: String = """["HU"]""",
        color: String = "#000000",
        aliases: String = "[]",
        rule: String = "null",
    ) = """{ "id": "$id", "name": "$name", "aliases": $aliases, "countryCodes": $countryCodes, "color": "$color",
             "defaultBarcodeFormat": "CODE_128", "cardNumberRule": $rule }"""
}
