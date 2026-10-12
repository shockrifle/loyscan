package com.bdaniel.loyscan.data.local.db

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun stringList_roundTrips_includingCommasAndQuotes() {
        val list = listOf("plain", "with, comma", "with \"quote\"", "")

        assertThat(converters.toStringList(converters.fromStringList(list))).isEqualTo(list)
    }

    @Test
    fun emptyStringList_roundTrips() {
        assertThat(converters.toStringList(converters.fromStringList(emptyList()))).isEmpty()
    }

    @Test
    fun cardNumberRule_roundTrips() {
        val rule = CardNumberRule(id = "example", format = BarcodeFormat.CODE_128, minLength = 4, maxLength = 10, prefix = "9")

        assertThat(converters.toCardNumberRule(converters.fromCardNumberRule(rule))).isEqualTo(rule)
    }

    @Test
    fun nullCardNumberRule_isStoredAsNull() {
        assertThat(converters.fromCardNumberRule(null)).isNull()
        assertThat(converters.toCardNumberRule(null)).isNull()
    }

    @Test
    fun invalidStoredRule_failsLoudly() {
        // Same strictness as the catalogue parser: a broken rule must not silently become "no rule".
        assertThrows(IllegalArgumentException::class.java) {
            converters.toCardNumberRule("""{"id":"x","format":"CODE_128","unknownKey":1}""")
        }
    }

    @Test
    fun instant_roundTripsAtMillisecondPrecision() {
        val instant = Instant.parse("2026-01-02T03:04:05.678Z")

        assertThat(converters.toInstant(converters.fromInstant(instant))).isEqualTo(instant)
    }

    @Test
    fun nullInstant_isStoredAsNull() {
        assertThat(converters.fromInstant(null)).isNull()
        assertThat(converters.toInstant(null)).isNull()
    }
}
