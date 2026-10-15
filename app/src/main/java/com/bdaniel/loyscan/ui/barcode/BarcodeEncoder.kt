package com.bdaniel.loyscan.ui.barcode

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat as ZxingFormat

/** A barcode ready to draw: a grid of dark (`true`) and light modules, quiet zone included. */
class EncodedBarcode(
    /** The format actually encoded, which differs from the card's when it had to fall back. */
    val format: BarcodeFormat,
    private val matrix: BitMatrix,
) {
    val width: Int get() = matrix.width
    val height: Int get() = matrix.height

    /** A one-row (1D) barcode, to be stretched to any height; otherwise modules are square. */
    val isLinear: Boolean get() = format !in TWO_DIMENSIONAL

    operator fun get(x: Int, y: Int): Boolean = matrix[x, y]

    private companion object {
        val TWO_DIMENSIONAL =
            setOf(BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417, BarcodeFormat.QR_CODE)
    }
}

/** Turns barcode content into modules with ZXing core. Pure JVM code, no Android. */
object BarcodeEncoder {

    /**
     * Encodes [content] in [format]; if that format can't hold it (no ZXing writer, as for DataBar, or content the
     * format rejects), in CODE_128, then in QR. `null` for empty content, or content not even QR can hold.
     */
    fun encode(content: String, format: BarcodeFormat): EncodedBarcode? {
        if (content.isEmpty()) return null
        return listOf(format, BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE).distinct()
            .firstNotNullOfOrNull { tryEncode(content, it) }
    }

    private fun tryEncode(content: String, format: BarcodeFormat): EncodedBarcode? {
        val zxingFormat = format.toZxing() ?: return null
        return try {
            // Size 0: the smallest matrix, one pixel per module (and one row for 1D); the screen scales it.
            val matrix = MultiFormatWriter().encode(content, zxingFormat, 0, 0, hintsFor(content))
            EncodedBarcode(format, matrix)
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            // ZXing's 1D writers reject content they can't hold (wrong length, bad check digit, bad character).
            null
        }
    }

    /**
     * UTF-8 only for content ISO-8859-1 (ZXing's default) can't hold: with a charset hint ZXing prepends an ECI
     * header, which the card's own barcode doesn't have and a till may pass on as a prefix or reject.
     */
    private fun hintsFor(content: String): Map<EncodeHintType, Any> =
        if (Charsets.ISO_8859_1.newEncoder().canEncode(content)) emptyMap()
        else mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name())

    private fun BarcodeFormat.toZxing(): ZxingFormat? = when (this) {
        BarcodeFormat.AZTEC -> ZxingFormat.AZTEC
        BarcodeFormat.CODABAR -> ZxingFormat.CODABAR
        BarcodeFormat.CODE_39 -> ZxingFormat.CODE_39
        BarcodeFormat.CODE_93 -> ZxingFormat.CODE_93
        BarcodeFormat.CODE_128 -> ZxingFormat.CODE_128
        BarcodeFormat.DATA_MATRIX -> ZxingFormat.DATA_MATRIX
        BarcodeFormat.EAN_8 -> ZxingFormat.EAN_8
        BarcodeFormat.EAN_13 -> ZxingFormat.EAN_13
        BarcodeFormat.ITF -> ZxingFormat.ITF
        BarcodeFormat.PDF_417 -> ZxingFormat.PDF_417
        BarcodeFormat.QR_CODE -> ZxingFormat.QR_CODE
        BarcodeFormat.UPC_A -> ZxingFormat.UPC_A
        BarcodeFormat.UPC_E -> ZxingFormat.UPC_E
        // ZXing core has readers but no writers for DataBar.
        BarcodeFormat.DATABAR, BarcodeFormat.DATABAR_EXPANDED, BarcodeFormat.DATABAR_LIMITED -> null
    }
}
