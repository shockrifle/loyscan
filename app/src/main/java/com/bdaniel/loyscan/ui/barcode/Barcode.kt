package com.bdaniel.loyscan.ui.barcode

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws [barcode] in black, as large as fits: linear barcodes fill the width and the height, 2D ones keep square
 * modules. Modules are a whole number of pixels when there is room, so every bar has the same width. The caller
 * puts it on white, with a margin, whatever the theme: scanners need dark on light and a quiet zone.
 */
@Composable
fun Barcode(barcode: EncodedBarcode, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .semantics { contentDescription = "Barcode" }
            .drawWithCache {
                val rects = barcodeRects(barcode, size)
                onDrawBehind { rects.forEach { drawRect(Color.Black, it.topLeft, it.size) } }
            }
    )
}

/** The dark runs of [barcode] laid out in [size], see [Barcode]. Adjacent dark modules in a row are one rect. */
internal fun barcodeRects(barcode: EncodedBarcode, size: Size): List<Rect> {
    val rows = if (barcode.isLinear) 1 else barcode.height
    val moduleWidth = snap(size.width / barcode.width)
        .let { if (barcode.isLinear) it else min(it, snap(size.height / rows)) }
    val moduleHeight = if (barcode.isLinear) size.height else moduleWidth
    // Whole-pixel offsets too, so bar edges stay sharp.
    val left = floor((size.width - moduleWidth * barcode.width) / 2)
    val top = floor((size.height - moduleHeight * rows) / 2)
    return buildList {
        for (y in 0 until rows) {
            var x = 0
            while (x < barcode.width) {
                if (!barcode[x, y]) { x++; continue }
                val start = x
                while (x < barcode.width && barcode[x, y]) x++
                add(
                    Rect(
                        offset = Offset(left + start * moduleWidth, top + y * moduleHeight),
                        size = Size((x - start) * moduleWidth, moduleHeight),
                    )
                )
            }
        }
    }
}

/** The size [Barcode] should be laid out at: the available width, and [linearHeight] high if it is 1D. */
fun Modifier.barcodeSize(barcode: EncodedBarcode, linearHeight: Dp = 120.dp): Modifier =
    if (barcode.isLinear) fillMaxWidth().height(linearHeight)
    else fillMaxWidth().aspectRatio(barcode.width.toFloat() / barcode.height)

/** Whole pixels when a module gets at least one; otherwise the exact fraction, so the barcode still fits. */
private fun snap(px: Float): Float = if (px >= 1f) floor(px) else px
