package com.bdaniel.loyscan.ui

import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
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
    fun aLogoInOneColour_withAFaintStrayPixel_isStillSolid() { // review of PR #244: an edge pixel nearly clear
        val red = 0xFFC62828.toInt()
        val faintBlack = 0x04000000 // alpha 4: its colour is noise after decoding
        val logo = intArrayOf(red, red, red, faintBlack)

        val drawn = inSingleColour(logo, tint = 0xFFFFFFFF.toInt())

        assertThat(drawn.take(3)).containsExactly(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        assertThat(drawn[3] ushr 24).isAtMost(4)
    }

    @Test
    fun aLogoWithNothingDrawn_staysClear() {
        val drawn = inSingleColour(intArrayOf(CLEAR, 0x00FFFFFF), tint = 0xFFFFFFFF.toInt())

        assertThat(drawn.map { it ushr 24 }).containsExactly(0, 0)
    }

    @Test
    fun aTwoColourLogo_recoloursItsBrandColouredPart_andLeavesTheOtherAlone() {
        val logo = intArrayOf(RED, WHITE, 0x80FF0000.toInt(), CLEAR)

        val drawn = recoloured(logo, brand = RED, tint = BLACK)

        // The red, half of it a soft edge, in black at its own opacity; the white and the clear pixel as they were.
        assertThat(drawn.toList()).containsExactly(BLACK, WHITE, 0x80000000.toInt(), CLEAR).inOrder()
    }

    /**
     * Within a CIE76 distance of 25 of the brand colour, in CIELAB (D65), a pixel is the brand colour. The distances come
     * from the published sRGB → CIELAB formulas (worked out outside the app): `#FF0000` is L* 53.24, a* 80.09, b* 67.20;
     * `#C00000` is (39.93, 64.71, 54.30), ΔE 24.1; `#B40000` is (37.30, 61.68, 51.72), ΔE 28.9.
     */
    @Test
    fun aShadeOfTheBrandColour_withinTheDistance_isRecoloured_andOneBeyondItIsNot() {
        val inside = 0xFFC00000.toInt()
        val beyond = 0xFFB40000.toInt()

        val drawn = recoloured(intArrayOf(inside, beyond), brand = RED, tint = WHITE)

        assertThat(drawn.toList()).containsExactly(WHITE, beyond).inOrder()
    }

    // The contrast check (#240). On red, #FF0000 (relative luminance 0.2126): white is (1 + 0.05) / (0.2126 + 0.05) =
    // 4.0:1 and black (0.2126 + 0.05) / 0.05 = 5.25:1, so black is the default colour; green #00A000 (0.2514) is
    // 1.15:1, too little, and far from red (ΔE 141), so Recolour leaves it.

    @Test
    fun aLogoLegibleOnItsBrandColour_isDrawnAsIs() {
        assertThat(preselectedTreatment(intArrayOf(WHITE, WHITE, CLEAR), brand = RED)).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun aLogoMostlyInItsBrandColour_isRecoloured_inTheContrastingColour() {
        val treatment = preselectedTreatment(intArrayOf(RED, RED, RED, WHITE), brand = RED)

        assertThat(treatment).isEqualTo(LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#000000"))
    }

    @Test
    fun aLogoMostlyInAnotherColourJustAsDark_isInSingleColour() { // Recolour would leave the green as it is
        val green = 0xFF00A000.toInt()

        val treatment = preselectedTreatment(intArrayOf(green, green, green, WHITE), brand = RED)

        assertThat(treatment).isEqualTo(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000"))
    }

    @Test
    fun aLogoExactlyHalfInItsBrandColour_isDrawnAsIs() { // "too much" is more than half
        assertThat(preselectedTreatment(intArrayOf(RED, WHITE), brand = RED)).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun theCheck_weighsEachPixelByItsOpacity() { // a faint red haze over most of the square doesn't outweigh the white
        val faintRed = 0x20FF0000

        val treatment = preselectedTreatment(intArrayOf(faintRed, faintRed, faintRed, faintRed, WHITE), brand = RED)

        assertThat(treatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun aHalfClearWhitePixel_isJudgedAsItShowsOnTheBrandColour() { // pink on red: (255, 128, 128) is 1.65:1, too little
        val halfWhite = 0x80FFFFFF.toInt()

        val treatment = preselectedTreatment(intArrayOf(halfWhite), brand = RED)

        assertThat(treatment.style).isNotNull()
    }

    @Test
    fun aLogoWithNothingDrawn_isDrawnAsIs() {
        assertThat(preselectedTreatment(intArrayOf(CLEAR, CLEAR), brand = RED)).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun theDefaultColour_isWhiteOrBlack_whicheverContrastsMore() {
        assertThat(defaultLogoTint(RED)).isEqualTo("#000000")
        assertThat(defaultLogoTint(0xFF1E5AA8.toInt())).isEqualTo("#FFFFFF") // luminance 0.104: white 6.8:1, black 3.1:1
    }

    /**
     * Outline (#240): a ring in the outline colour around the logo's shape, the logo on top, on a square grown by the
     * radius on every side. The ring's opacity is r + 0.5 − d (clamped to 0..1), d the distance to the nearest pixel of
     * the logo: with r = 2, opaque at d = 1 and √2 (1.5, 1.09), 128 at d = 2 (0.5), 67 at d = √5 (0.264), clear at
     * d = 2√2 (−0.33).
     */
    @Test
    fun anOutline_isARingAroundTheLogo_fadingAtItsEdge() {
        val drawn = outlined(intArrayOf(RED), width = 1, height = 1, radius = 2, colour = BLACK)

        val clear = 0x00000000
        val edge = 0x80000000.toInt()
        val faint = 0x43000000
        assertThat(drawn.toList()).containsExactly(
            clear, faint, edge, faint, clear,
            faint, BLACK, BLACK, BLACK, faint,
            edge, BLACK, RED, BLACK, edge,
            faint, BLACK, BLACK, BLACK, faint,
            clear, faint, edge, faint, clear,
        ).inOrder()
    }

    @Test
    fun aSoftLogoPixel_showsTheOutlineThroughIt() { // half red over the opaque black ring: (128, 0, 0)
        val drawn = outlined(intArrayOf(0x80FF0000.toInt()), width = 1, height = 1, radius = 1, colour = BLACK)

        assertThat(drawn[4]).isEqualTo(0xFF800000.toInt())
    }

    @Test
    fun anOutline_goesInsideAHoleToo() { // a 3×3 frame, r = 1: its clear middle is 1 from the frame, so 128 of 255
        val logo = intArrayOf(RED, RED, RED, RED, CLEAR, RED, RED, RED, RED)

        val drawn = outlined(logo, width = 3, height = 3, radius = 1, colour = WHITE)

        assertThat(drawn[2 * 5 + 2]).isEqualTo(0x80FFFFFF.toInt())
    }

    @Test
    fun aLogoWithNothingDrawn_hasNoOutline() {
        val drawn = outlined(intArrayOf(CLEAR, 0x40FF0000), width = 2, height = 1, radius = 1, colour = BLACK)

        assertThat(drawn.size).isEqualTo(4 * 3)
        assertThat(drawn.filter { it != CLEAR && it != 0x40FF0000 }).isEmpty()
    }

    @Test
    fun anOutlinedLogo_growsByTheRadiusOnEverySide() {
        val drawn = outlined(IntArray(4 * 2) { RED }, width = 4, height = 2, radius = 3, colour = BLACK)

        assertThat(drawn.size).isEqualTo(10 * 8)
        assertThat(drawn[3 * 10 + 3]).isEqualTo(RED) // the logo's first pixel
        assertThat(drawn[3 * 10 + 1]).isEqualTo(BLACK) // 2 left of it, on the ring
        assertThat(drawn[3 * 10]).isEqualTo(0x80000000.toInt()) // 3 left of it, the ring's soft edge
    }

    @Test
    fun theOutlinesRadius_is4PercentOfTheLogosLongerSide_atLeastAPixel() {
        assertThat(outlineRadius(100, 50)).isEqualTo(4)
        assertThat(outlineRadius(20, 40)).isEqualTo(2) // 1.6
        assertThat(outlineRadius(8, 8)).isEqualTo(1) // 0.32
    }

    private companion object {
        const val CLEAR = 0x00000000
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val GREY = 0xFF808080.toInt()
        val RED = 0xFFFF0000.toInt()
    }
}
