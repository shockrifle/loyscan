package com.bdaniel.loyscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.adaptiveLayers
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.hypot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon is LoyScan's own (#75): two fanned loyalty cards, the front one's barcode shaped as a heart, as an
 * adaptive icon with its own monochrome layer for themed icons (Android 13+) ([[2026-11-18-launcher-icon]]). The
 * layers are drawn for real (`@GraphicsMode(NATIVE)`) and checked on their pixels, so a test can't pass on XML that
 * looks right but draws something else.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun bothLauncherIcons_areAdaptiveWithTheirOwnMonochromeLayer() {
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round).forEach { icon ->
            val layers = context.adaptiveLayers(icon)
            assertWithMessage("layers of ${context.resources.getResourceEntryName(icon)}")
                .that(layers.keys).containsExactly("background", "foreground", "monochrome")
            // The template reused the coloured foreground, so a themed icon showed a blank silhouette.
            assertThat(layers["monochrome"]).isNotEqualTo(layers["foreground"])
        }
    }

    @Test
    fun foregroundAndMonochrome_stayInsideTheSafeZone() {
        // A launcher may mask the 108dp layer to any shape that holds the centre 66dp circle.
        listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome).forEach { layer ->
            val bitmap = render(layer)
            val centre = SIZE / 2f
            val outside = pixels(bitmap).count { (x, y, colour) ->
                Color.alpha(colour) > 0 && hypot(x + 0.5f - centre, y + 0.5f - centre) > SAFE_RADIUS + 1
            }
            assertWithMessage("pixels outside the safe zone in ${context.resources.getResourceEntryName(layer)}")
                .that(outside).isEqualTo(0)
        }
    }

    @Test
    fun foreground_showsAHeartShapedBarcodeOnTheCard() {
        // Dark bars on the white front card: across the heart's lobes they alternate with the card, and towards its
        // point they span far less, so the barcode is a heart, not a rectangle.
        val icon = render(R.drawable.ic_launcher_foreground)
        val bar = { colour: Int -> Color.alpha(colour) > 127 && Color.luminance(colour) < 0.5f }
        assertThat(runs(icon.row(LOBES_ROW)) { !bar(it) }).isAtLeast(MIN_BAR_RUNS)
        val atPoint = extent(icon.row(POINT_ROW), bar)
        assertThat(atPoint).isGreaterThan(0) // the barcode reaches down to the heart's point
        assertThat(extent(icon.row(LOBES_ROW), bar)).isGreaterThan(2 * atPoint)
    }

    @Test
    fun monochrome_showsTheHeartShapedBarcodeAsGaps() {
        // A themed icon is tinted by alpha alone, so the bars must be holes in the card, or it is a blank card.
        val icon = render(R.drawable.ic_launcher_monochrome)
        val hole = { colour: Int -> Color.alpha(colour) < 128 }
        assertThat(runs(icon.row(LOBES_ROW)) { !hole(it) }).isAtLeast(MIN_BAR_RUNS)
        val atPoint = extent(icon.row(POINT_ROW), hole)
        assertThat(atPoint).isGreaterThan(0) // the barcode reaches down to the heart's point
        assertThat(extent(icon.row(LOBES_ROW), hole)).isGreaterThan(2 * atPoint)
    }

    private fun render(drawable: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        context.getDrawable(drawable)!!.apply { setBounds(0, 0, SIZE, SIZE) }.draw(Canvas(bitmap))
        return bitmap
    }

    private fun pixels(bitmap: Bitmap) = sequence {
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) yield(Triple(x, y, bitmap.getPixel(x, y)))
    }

    /** The pixels of the row at [dp] (in the 108dp viewport), inside the front card only. */
    private fun Bitmap.row(dp: Float): List<Int> =
        (CARD_LEFT * SCALE until CARD_RIGHT * SCALE).map { getPixel(it, (dp * SCALE).toInt()) }

    /** How wide the span from the first to the last pixel of [row] that is [on] is, in pixels; 0 if none is. */
    private fun extent(row: List<Int>, on: (Int) -> Boolean): Int {
        val first = row.indexOfFirst(on)
        return if (first < 0) 0 else row.indexOfLast(on) - first + 1
    }

    /** How many runs of `true` pixels [row] has, by [on]. */
    private fun runs(row: List<Int>, on: (Int) -> Boolean): Int =
        row.map(on).zipWithNext().count { (before, now) -> !before && now } + if (on(row.first())) 1 else 0

    private companion object {
        const val SCALE = 4
        const val SIZE = 108 * SCALE
        const val SAFE_RADIUS = 33f * SCALE
        // The front card spans 29..79 x 41..73 in the 108dp viewport; its heart's lobes are at about 52, its point at 69.
        const val CARD_LEFT = 31
        const val CARD_RIGHT = 77
        const val LOBES_ROW = 52f
        const val POINT_ROW = 67f
        const val MIN_BAR_RUNS = 6
    }
}
