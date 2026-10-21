package com.bdaniel.loyscan.domain

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** GS1 element strings as printed under a barcode (#42). Example data only. */
class Gs1Test {

    @Test
    fun humanReadable_bracketsEachAi_andEndsAVariableFieldAtTheSeparator() {
        val content = "0109501101020917" + "1012AB\u001D" + "21XYZ"

        assertThat(Gs1.humanReadable(content)).isEqualTo("(01)09501101020917(10)12AB(21)XYZ")
    }

    @Test
    fun humanReadable_aFieldOfPredefinedLengthNeedsNoSeparator() {
        // AI 17 (a date) always holds 6 digits, so AI 10 follows it directly.
        assertThat(Gs1.humanReadable("17261231" + "10ABC")).isEqualTo("(17)261231(10)ABC")
    }

    @Test
    fun humanReadable_aVariableFieldRunsToTheEnd() {
        assertThat(Gs1.humanReadable("9799123456789012")).isEqualTo("(97)99123456789012")
    }

    @Test
    fun humanReadable_aisOfThreeAndFourDigits() {
        assertThat(Gs1.humanReadable("4101234567890128")).isEqualTo("(410)1234567890128")
        assertThat(Gs1.humanReadable("3103000125" + "8005000123")).isEqualTo("(3103)000125(8005)000123")
    }

    @Test
    fun humanReadable_contentThatIsNotAGs1ElementString_isNull() {
        listOf(
            "", // nothing
            "14123456", // AI prefix 14 isn't assigned
            "AB1234", // an AI is digits
            "01123", // AI 01 holds 14 digits
            "10\u001D21XYZ", // empty field
            "10AB\u001D", // nothing after the separator
            "1", // half an AI
        ).forEach { content ->
            assertWithMessage(content).that(Gs1.humanReadable(content)).isNull()
        }
    }
}
