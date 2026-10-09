package com.bdaniel.loyscan.domain.cardnumber

import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm.GTIN_MOD10
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm.LUHN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckDigitAlgorithmTest {

    @Test
    fun gtinMod10_computesKnownCodes() {
        assertEquals('1', GTIN_MOD10.compute("400638133393"))  // EAN-13 4006381333931
        assertEquals('7', GTIN_MOD10.compute("7351353"))       // EAN-8 73513537
        assertEquals('2', GTIN_MOD10.compute("03600029145"))   // UPC-A 036000291452
        assertEquals('0', GTIN_MOD10.compute("000000000000"))
    }

    @Test
    fun gtinMod10_verify() {
        assertTrue(GTIN_MOD10.verify("4006381333931"))
        assertTrue(GTIN_MOD10.verify("73513537"))
        assertFalse(GTIN_MOD10.verify("4006381333932"))
        assertFalse(GTIN_MOD10.verify("4006381333913"))  // transposed digits
    }

    @Test
    fun luhn_computesKnownCodes() {
        assertEquals('3', LUHN.compute("7992739871"))
        assertEquals('1', LUHN.compute("411111111111111"))  // test card number 4111111111111111
        assertEquals('0', LUHN.compute("0"))
    }

    @Test
    fun luhn_verify() {
        assertTrue(LUHN.verify("79927398713"))
        assertTrue(LUHN.verify("4111111111111111"))
        assertFalse(LUHN.verify("79927398710"))
    }

    @Test
    fun verify_rejectsNonDigitsAndTooShort() {
        assertFalse(GTIN_MOD10.verify("40063813339A"))
        assertFalse(LUHN.verify("7"))
        assertFalse(LUHN.verify(""))
    }

    @Test
    fun compute_rejectsInvalidPayload() {
        assertThrows(IllegalArgumentException::class.java) { GTIN_MOD10.compute("") }
        assertThrows(IllegalArgumentException::class.java) { LUHN.compute("12a") }
    }
}
