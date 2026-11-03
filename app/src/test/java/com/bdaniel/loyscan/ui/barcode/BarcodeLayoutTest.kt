package com.bdaniel.loyscan.ui.barcode

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.google.common.truth.Truth.assertThat
import com.google.zxing.common.BitMatrix
import org.junit.Test

class BarcodeLayoutTest {

    /** A barcode from a picture: `#` is a dark module, one string per row; no quiet zone added. */
    private fun barcode(format: BarcodeFormat, vararg rows: String): EncodedBarcode {
        val matrix = BitMatrix(rows[0].length, rows.size)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == '#') matrix.set(x, y) } }
        return EncodedBarcode(format, matrix, margin = 0)
    }

    @Test
    fun linear_barsSpanTheFullHeight_withWholePixelModules_centred() {
        // 6 modules in 100 px: 16 px each (not 16.67, so every bar is equally wide), 2 px left over on each side.
        val rects = barcodeRects(barcode(BarcodeFormat.CODE_128, ".##..#"), Size(100f, 40f))

        assertThat(rects).containsExactly(
            Rect(left = 18f, top = 0f, right = 50f, bottom = 40f),
            Rect(left = 82f, top = 0f, right = 98f, bottom = 40f),
        ).inOrder()
    }

    @Test
    fun linear_inATallArea_runsDownTheLongSide_withWholePixelModules_centred() { // #34
        // Turned a quarter: 6 modules down 100 px, 16 px each, 2 px left over at the top and bottom; bars full width.
        val rects = barcodeRects(barcode(BarcodeFormat.CODE_128, ".##..#"), Size(40f, 100f))

        assertThat(rects).containsExactly(
            Rect(left = 0f, top = 18f, right = 40f, bottom = 50f),
            Rect(left = 0f, top = 82f, right = 40f, bottom = 98f),
        ).inOrder()
    }

    @Test
    fun linear_inATallArea_keepsItsQuietZone() { // #34
        val barcode = EncodedBarcode(BarcodeFormat.CODE_128, BitMatrix(2, 1).apply { set(0, 0); set(1, 0) })

        // 2 dark modules plus 11 light ones each side: 24 modules down 240 px, 10 px each.
        val rects = barcodeRects(barcode, Size(40f, 240f))

        assertThat(rects).containsExactly(Rect(left = 0f, top = 110f, right = 40f, bottom = 130f))
    }

    @Test
    fun twoDimensional_inATallArea_isNotTurned() { // #34
        val rects = barcodeRects(barcode(BarcodeFormat.QR_CODE, "#.", ".."), Size(20f, 100f))

        assertThat(rects).containsExactly(Rect(left = 0f, top = 40f, right = 10f, bottom = 50f))
    }

    @Test
    fun twoDimensional_modulesAreSquare_sizedByTheTighterSide_centred() {
        // 3x2 modules in 100x40 px: 20 px squares (height-bound), centred horizontally.
        val rects = barcodeRects(barcode(BarcodeFormat.QR_CODE, "#.#", ".##"), Size(100f, 40f))

        assertThat(rects).containsExactly(
            Rect(left = 20f, top = 0f, right = 40f, bottom = 20f),
            Rect(left = 60f, top = 0f, right = 80f, bottom = 20f),
            Rect(left = 40f, top = 20f, right = 80f, bottom = 40f),
        ).inOrder()
    }

    @Test
    fun lessThanAPixelPerModule_stillFitsTheBarcode() {
        val rects = barcodeRects(barcode(BarcodeFormat.CODE_128, "#...#"), Size(2.5f, 1f))

        assertThat(rects).containsExactly(
            Rect(left = 0f, top = 0f, right = 0.5f, bottom = 1f),
            Rect(left = 2f, top = 0f, right = 2.5f, bottom = 1f),
        ).inOrder()
    }
}
