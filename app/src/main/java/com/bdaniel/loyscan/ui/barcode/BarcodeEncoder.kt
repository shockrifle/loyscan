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
    /** Light modules added on each side. ZXing is asked for none, since its own are too narrow or missing. */
    private val margin: Int = quietZoneModules(format),
) {
    val width: Int get() = matrix.width + 2 * margin
    val height: Int get() = if (isLinear) matrix.height else matrix.height + 2 * margin

    /** A one-row (1D) barcode, to be stretched to any height; otherwise modules are square. */
    val isLinear: Boolean get() = format !in TWO_DIMENSIONAL

    operator fun get(x: Int, y: Int): Boolean {
        val mx = x - margin
        val my = if (isLinear) y else y - margin
        return mx in 0 until matrix.width && my in 0 until matrix.height && matrix[mx, my]
    }

    private companion object {
        val TWO_DIMENSIONAL =
            setOf(BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417, BarcodeFormat.QR_CODE)

        /**
         * Each symbology's minimum quiet zone, a little more where the spec allows one module. 1D: 11 covers
         * EAN-13's left side (the largest), Code 128 and ITF need 10, UPC 9. ZXing at size 0 gives 1D codes 4-5 a
         * side and Aztec and Data Matrix none, and the screen's padding alone is less than a module of a 2D code
         * drawn at full width.
         */
        fun quietZoneModules(format: BarcodeFormat): Int = when (format) {
            BarcodeFormat.QR_CODE -> 4
            BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417 -> 2
            else -> 11
        }
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
        if (zxingFormat == ZxingFormat.CODE_128 && content.any { it in CODE_128_ESCAPES }) return null
        return try {
            // Size 0: the smallest matrix, one pixel per module (and one row for 1D); the screen scales it.
            val matrix = MultiFormatWriter().encode(content.withFnc1For(format), zxingFormat, 0, 0, hintsFor(content))
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
        if (Charsets.ISO_8859_1.newEncoder().canEncode(content)) NO_MARGIN
        else mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name())
            .plus(NO_MARGIN)

    /**
     * GS1-128 is Code 128 with a leading FNC1. ZXing's Code 128 writer ignores `EncodeHintType.GS1_FORMAT` (only its
     * 2D writers read it); it writes FNC1 for the escape character U+00F1 instead. A scan holds the FNC1 between two
     * GS1 fields as GS (U+001D), as scanners send it (#42); it is drawn as FNC1 too, not as a literal GS.
     */
    private fun String.withFnc1For(format: BarcodeFormat): String =
        if (format == BarcodeFormat.GS1_128) CODE_128_FNC1 + replace(GS1_SEPARATOR, CODE_128_FNC1) else this

    private const val CODE_128_FNC1 = 'ñ'
    private const val GS1_SEPARATOR = '\u001D'

    /**
     * ZXing's Code 128 writer writes FNC1–FNC4 for U+00F1–U+00F4 (`ñ` `ò` `ó` `ô`) without an error, so content holding
     * one of these letters would be drawn with a function character in its place (#46). Such content skips Code 128.
     */
    private val CODE_128_ESCAPES = CODE_128_FNC1..'ô'

    /** [EncodedBarcode] adds the quiet zone. */
    private val NO_MARGIN = mapOf(EncodeHintType.MARGIN to 0)

    private fun BarcodeFormat.toZxing(): ZxingFormat? = when (this) {
        BarcodeFormat.AZTEC -> ZxingFormat.AZTEC
        BarcodeFormat.CODABAR -> ZxingFormat.CODABAR
        BarcodeFormat.CODE_39 -> ZxingFormat.CODE_39
        BarcodeFormat.CODE_93 -> ZxingFormat.CODE_93
        // GS1-128 is Code 128 with a leading FNC1 (withFnc1For).
        BarcodeFormat.CODE_128, BarcodeFormat.GS1_128 -> ZxingFormat.CODE_128
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
