package com.bdaniel.loyscan.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A logo's bitmap drawn with its treatment (#240): what the image loader and the widget draw. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real pixels
class LogoTreatmentBitmapTest {

    /** Mostly dark: two black pixels and a white one. */
    private val logo = Bitmap.createBitmap(intArrayOf(BLACK, BLACK, WHITE), 3, 1, Bitmap.Config.ARGB_8888)

    @Test
    fun inSingleColour_isANewBitmapInTheTint_andTheLogoIsUntouched() {
        val drawn = logo.drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"), BRAND)

        assertThat(drawn).isNotSameInstanceAs(logo)
        assertThat(drawn.config).isEqualTo(Bitmap.Config.ARGB_8888)
        assertThat(drawn.getPixel(0, 0)).isEqualTo(WHITE) // the dark part, solid
        assertThat(drawn.getPixel(2, 0) ushr 24).isEqualTo(0) // the light part, clear
        assertThat(logo.getPixel(0, 0)).isEqualTo(BLACK)
        assertThat(logo.getPixel(2, 0)).isEqualTo(WHITE)
    }

    @Test
    fun aLogoThatCantBeChanged_isDrawnInSingleColourToo() { // a decoded bitmap may be immutable, or not ARGB
        val immutable = logo.copy(Bitmap.Config.RGB_565, false)

        val drawn = immutable.drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"), BRAND)

        assertThat(drawn.getPixel(0, 0)).isEqualTo(WHITE)
    }

    @Test
    fun asIs_isTheLogoItself() {
        assertThat(logo.drawnWith(LogoTreatment.AS_IS, BRAND)).isSameInstanceAs(logo)
    }

    @Test
    fun aTintThatIsNotAColour_drawsTheLogoAsIs() {
        assertThat(logo.drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "not a colour"), BRAND)).isSameInstanceAs(logo)
    }

    @Test
    fun theTransformationsCacheKey_differsByStyleAndTint() { // Coil caches a transformed logo under it
        val white = LogoTreatmentTransformation(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"), BRAND)
        val black = LogoTreatmentTransformation(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000"), BRAND)

        assertThat(white.cacheKey).isNotEqualTo(black.cacheKey)
        assertThat(white.cacheKey).isEqualTo(LogoTreatmentTransformation(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"), BRAND).cacheKey)
    }

    @Test
    fun recoloured_turnsTheBrandColouredPixelsIntoTheTint() {
        val logo = Bitmap.createBitmap(intArrayOf(RED, WHITE), 2, 1, Bitmap.Config.ARGB_8888)

        val drawn = logo.drawnWith(LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#000000"), brandColor = "#FF0000")

        assertThat(drawn).isNotSameInstanceAs(logo)
        assertThat(drawn.getPixel(0, 0)).isEqualTo(BLACK)
        assertThat(drawn.getPixel(1, 0)).isEqualTo(WHITE)
        assertThat(logo.getPixel(0, 0)).isEqualTo(RED)
    }

    @Test
    fun recoloured_onABrandColourThatIsNotAColour_isTheLogoItself() {
        val recolour = LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#000000")

        assertThat(logo.drawnWith(recolour, brandColor = "not a colour")).isSameInstanceAs(logo)
    }

    @Test
    fun aRecolouredLogosCacheKey_differsByBrandColour_andSingleColoursDoesNot() { // Recolour follows the brand colour
        val recolour = LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#000000")
        val single = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000")

        assertThat(LogoTreatmentTransformation(recolour, "#FF0000").cacheKey)
            .isNotEqualTo(LogoTreatmentTransformation(recolour, "#0000FF").cacheKey)
        assertThat(LogoTreatmentTransformation(recolour, "#FF0000").cacheKey)
            .isNotEqualTo(LogoTreatmentTransformation(single, "#FF0000").cacheKey)
        assertThat(LogoTreatmentTransformation(single, "#FF0000").cacheKey)
            .isEqualTo(LogoTreatmentTransformation(single, "#0000FF").cacheKey)
    }

    // Outline (#240): a 50 × 50 logo's ring is 2 px (4% of 50), so the drawn logo is 54 × 54, the logo at (2, 2); the
    // pixel 1 px left of its middle row's first pixel is on the ring, fully opaque (2 + 0.5 − 1 ≥ 1).

    private fun square(argb: Int) = Bitmap.createBitmap(IntArray(50 * 50) { argb }, 50, 50, Bitmap.Config.ARGB_8888)

    @Test
    fun asIsWithAnOutline_isTheLogoInARing_blackOnRed() { // black contrasts more with red (5.25:1 against 4.0:1)
        val logo = square(BLUE)

        val drawn = logo.drawnWith(LogoTreatment(outline = true), brandColor = "#FF0000")

        assertThat(drawn.width to drawn.height).isEqualTo(54 to 54)
        assertThat(drawn.getPixel(27, 27)).isEqualTo(BLUE)
        assertThat(drawn.getPixel(1, 27)).isEqualTo(BLACK)
        assertThat(drawn.getPixel(0, 0) ushr 24).isEqualTo(0) // the corner, 2.83 px from the logo
    }

    @Test
    fun anOutline_onADarkBrandColour_isWhite() {
        val drawn = square(RED).drawnWith(LogoTreatment(outline = true), brandColor = BRAND)

        assertThat(drawn.getPixel(1, 27)).isEqualTo(WHITE)
    }

    @Test
    fun anOutline_goesWithSingleColour() {
        val drawn = square(BLACK).drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF", outline = true), "#FF0000")

        assertThat(drawn.getPixel(27, 27)).isEqualTo(WHITE)
        assertThat(drawn.getPixel(1, 27)).isEqualTo(BLACK)
    }

    @Test
    fun anOutline_goesWithRecolour() {
        val drawn = square(RED).drawnWith(LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#FFFFFF", outline = true), "#FF0000")

        assertThat(drawn.getPixel(27, 27)).isEqualTo(WHITE)
        assertThat(drawn.getPixel(1, 27)).isEqualTo(BLACK)
    }

    @Test
    fun anOutline_withATintThatIsNotAColour_isTheLogoAsIsInItsRing() {
        val drawn = square(BLUE).drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "not a colour", outline = true), "#FF0000")

        assertThat(drawn.getPixel(27, 27)).isEqualTo(BLUE)
        assertThat(drawn.getPixel(1, 27)).isEqualTo(BLACK)
    }

    @Test
    fun anOutline_onABrandColourThatIsNotAColour_isLeftOut() { // its colour can't be worked out
        val logo = square(BLACK)

        assertThat(logo.drawnWith(LogoTreatment(outline = true), brandColor = "not a colour")).isSameInstanceAs(logo)
        val single = logo.drawnWith(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF", outline = true), "not a colour")
        assertThat(single.width).isEqualTo(50)
        assertThat(single.getPixel(25, 25)).isEqualTo(WHITE)
    }

    @Test
    fun anOutlinedLogosCacheKey_differsByItsOutlineColour_notByTheBrandColour() { // Outline follows the brand colour
        val outlined = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000", outline = true)
        val single = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000")

        // Red and yellow take a black outline, dark blue a white one.
        assertThat(LogoTreatmentTransformation(outlined, "#FF0000").cacheKey)
            .isEqualTo(LogoTreatmentTransformation(outlined, "#FFFF00").cacheKey)
        assertThat(LogoTreatmentTransformation(outlined, "#FF0000").cacheKey)
            .isNotEqualTo(LogoTreatmentTransformation(outlined, BRAND).cacheKey)
        assertThat(LogoTreatmentTransformation(outlined, "#FF0000").cacheKey)
            .isNotEqualTo(LogoTreatmentTransformation(single, "#FF0000").cacheKey)
    }

    private companion object {
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val RED = 0xFFFF0000.toInt()
        val BLUE = 0xFF0000AA.toInt()
        const val BRAND = "#1E5AA8"
    }
}
