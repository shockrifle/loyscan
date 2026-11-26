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

    private companion object {
        val BLACK = 0xFF000000.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val RED = 0xFFFF0000.toInt()
        const val BRAND = "#1E5AA8"
    }
}
