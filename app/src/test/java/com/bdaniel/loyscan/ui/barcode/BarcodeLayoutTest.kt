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
        val rects = barcodeRects(barcode(BarcodeFormat.CODE_128, "#...#"), Size(2.5f, 10f))

        assertThat(rects).containsExactly(
            Rect(left = 0f, top = 0f, right = 0.5f, bottom = 10f),
            Rect(left = 2f, top = 0f, right = 2.5f, bottom = 10f),
        ).inOrder()
    }
}
