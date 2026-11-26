package com.bdaniel.loyscan.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Single colour (#240): the logo in one colour, each pixel's opacity from its brightness, so the detail inside it stays
 * visible. Which end is solid follows the logo: on a mostly dark logo the dark parts, on a mostly light one the light
 * parts; "mostly" is by area, the mean CIE lightness L* against mid-grey (50). Expected alphas are worked out by hand from the WCAG relative luminance: `#808080` is 0.215861, so it keeps
 * 1 − 0.215861 of a solid pixel's opacity (200 of 255) on a dark logo and 0.215861 of it (55) on a light one.
 */
class LogoTreatmentsTest {

    @Test
    fun aDarkDiscWithALightLetterCutOut_isTheDiscInTheColour_andTheLetterClear() {
        val logo = intArrayOf(
            CLEAR, BLACK, BLACK, CLEAR,
            BLACK, WHITE, GREY, BLACK,
            BLACK, BLACK, BLACK, BLACK,
            CLEAR, BLACK, BLACK, CLEAR,
        )

        val drawn = inSingleColour(logo, tint = 0xFF1E5AA8.toInt())

        assertThat(drawn.toList()).containsExactlyElementsIn(
            listOf(
                0x001E5AA8, 0xFF1E5AA8, 0xFF1E5AA8, 0x001E5AA8,
                0xFF1E5AA8, 0x001E5AA8, 0xC81E5AA8, 0xFF1E5AA8,
                0xFF1E5AA8, 0xFF1E5AA8, 0xFF1E5AA8, 0xFF1E5AA8,
                0x001E5AA8, 0xFF1E5AA8, 0xFF1E5AA8, 0x001E5AA8,
            ).map { it.toInt() },
        ).inOrder()
    }

    @Test
    fun aLightDiscWithADarkLetterCutOut_isTheDiscInTheColour_andTheLetterClear() {
        val logo = intArrayOf(
            CLEAR, WHITE, WHITE, CLEAR,
            WHITE, BLACK, GREY, WHITE,
            WHITE, WHITE, WHITE, WHITE,
            CLEAR, WHITE, WHITE, CLEAR,
        )

        val drawn = inSingleColour(logo, tint = 0xFF000000.toInt())

        assertThat(drawn.toList()).containsExactlyElementsIn(
            listOf(
                0x00000000, 0xFF000000, 0xFF000000, 0x00000000,
                0xFF000000, 0x00000000, 0x37000000, 0xFF000000,
                0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000,
                0x00000000, 0xFF000000, 0xFF000000, 0x00000000,
            ).map { it.toInt() },
        ).inOrder()
    }

    @Test
    fun aLogoTwoThirdsBlack_isMostlyDark_soItsBlackIsSolid() { // by area: its mean linear luminance (1/3) is light
        val drawn = inSingleColour(intArrayOf(BLACK, BLACK, WHITE), tint = 0xFFFFFFFF.toInt())

        assertThat(drawn.toList()).containsExactly(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF).inOrder()
    }

    @Test
    fun aLogoTwoThirdsWhite_isMostlyLight_soItsWhiteIsSolid() {
        val drawn = inSingleColour(intArrayOf(WHITE, WHITE, BLACK), tint = 0xFF000000.toInt())

        assertThat(drawn.toList()).containsExactly(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000).inOrder()
    }

    @Test
    fun aLogoInOneColour_isSolidInTheTint_keepingItsEdgesSoftness() { // the logo that blends into its brand colour
        val red = 0xFFC62828.toInt()
        val halfRed = 0x80C62828.toInt()
        val logo = intArrayOf(CLEAR, halfRed, red, red)

        val drawn = inSingleColour(logo, tint = 0xFFFFFFFF.toInt())

        assertThat(drawn.toList()).containsExactly(0x00FFFFFF, 0x80FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()).inOrder()
    }

    @Test
    fun aLogoWithNothingDrawn_staysClear() {
        val drawn = inSingleColour(intArrayOf(CLEAR, 0x00FFFFFF), tint = 0xFFFFFFFF.toInt())

        assertThat(drawn.map { it ushr 24 }).containsExactly(0, 0)
    }

    private companion object {
        const val CLEAR = 0x00000000
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val GREY = 0xFF808080.toInt()
    }
}
