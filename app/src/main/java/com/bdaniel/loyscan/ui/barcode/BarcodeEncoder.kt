package com.bdaniel.loyscan.ui.barcode

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.Gs1
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
        val TWO_DIMENSIONAL = setOf(
            BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.GS1_DATA_MATRIX, BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE, BarcodeFormat.GS1_QR_CODE,
        )

        /**
         * Each symbology's minimum quiet zone, a little more where the spec allows one module. 1D: 11 covers
         * EAN-13's left side (the largest), Code 128 and ITF need 10, UPC 9. ZXing at size 0 gives 1D codes 4-5 a
         * side and Aztec and Data Matrix none, and the screen's padding alone is less than a module of a 2D code
         * drawn at full width.
         */
        fun quietZoneModules(format: BarcodeFormat): Int = when (format) {
            BarcodeFormat.QR_CODE, BarcodeFormat.GS1_QR_CODE -> 4
            BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.GS1_DATA_MATRIX, BarcodeFormat.PDF_417 -> 2
            else -> 11
        }
    }
}

/** Turns barcode content into modules with ZXing core. Pure JVM code, no Android. */
object BarcodeEncoder {

    /**
     * Encodes [content] in [format]; if that format can't hold it (no ZXing writer, as for DataBar, or content the
     * format rejects), in CODE_128, then in QR. GS1 data on a GS1 card falls back to GS1-128, then GS1 QR, so it keeps
     * its GS1 mark (#45). Other content, e.g. a number typed for a DataBar store, gets no FNC1: a GS1 till would
     * reject it.
     * `null` for empty content, or content not even QR can hold.
     */
    fun encode(content: String, format: BarcodeFormat): EncodedBarcode? {
        if (content.isEmpty()) return null
        val fallbacks = if (format.isGs1 && Gs1.humanReadable(content) != null) GS1_FALLBACKS else FALLBACKS
        return (listOf(format) + fallbacks).distinct().firstNotNullOfOrNull { tryEncode(content, it) }
    }

    /**
     * Whether [content] is drawn in [format] exactly as it is: no fallback, and no check digit added. ZXing's EAN and UPC
     * writers accept the number without its check digit and compute one, so a typed number one digit short would be
     * drawn as a different, valid-looking number (#47 review).
     */
    fun holdsAsIs(content: String, format: BarcodeFormat): Boolean =
        FULL_LENGTH[format]?.let { content.length == it } != false && encode(content, format)?.format == format

    /** The length with the check digit, for the formats whose ZXing writer also takes one digit fewer. */
    private val FULL_LENGTH = mapOf(
        BarcodeFormat.EAN_8 to 8,
        BarcodeFormat.EAN_13 to 13,
        BarcodeFormat.UPC_A to 12,
        BarcodeFormat.UPC_E to 8,
    )

    private val FALLBACKS = listOf(BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE)
    private val GS1_FALLBACKS = listOf(BarcodeFormat.GS1_128, BarcodeFormat.GS1_QR_CODE)

    private fun tryEncode(content: String, format: BarcodeFormat): EncodedBarcode? {
        val zxingFormat = format.toZxing() ?: return null
        if (zxingFormat == ZxingFormat.CODE_128 && content.any { it in CODE_128_ESCAPES }) return null
        return try {
            // Size 0: the smallest matrix, one pixel per module (and one row for 1D); the screen scales it.
            val matrix =
                MultiFormatWriter().encode(content.withFnc1For(format), zxingFormat, 0, 0, hintsFor(content, format))
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
    private fun hintsFor(content: String, format: BarcodeFormat): Map<EncodeHintType, Any> =
        (if (Charsets.ISO_8859_1.newEncoder().canEncode(content)) NO_MARGIN
        else mapOf(EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name()).plus(NO_MARGIN))
            .plus(gs1HintsFor(format))

    /**
     * GS1 Data Matrix and GS1 QR start with FNC1, which `GS1_FORMAT` adds. The Data Matrix writer reads it only in its
     * compact mode, which also writes each GS (U+001D) as FNC1 (ZXing 3.5.3 `DataMatrixWriter`). In a QR code a GS in
     * byte mode is already how GS1 writes FNC1.
     */
    private fun gs1HintsFor(format: BarcodeFormat): Map<EncodeHintType, Any> = when (format) {
        BarcodeFormat.GS1_DATA_MATRIX ->
            mapOf(EncodeHintType.GS1_FORMAT to true, EncodeHintType.DATA_MATRIX_COMPACT to true)
        BarcodeFormat.GS1_QR_CODE -> mapOf(EncodeHintType.GS1_FORMAT to true)
        else -> emptyMap()
    }

    /**
     * GS1-128 is Code 128 with a leading FNC1. ZXing's Code 128 writer ignores `EncodeHintType.GS1_FORMAT` (only its
     * 2D writers read it); it writes FNC1 for the escape character U+00F1 instead. A scan holds the FNC1 between two
     * GS1 fields as GS (U+001D), as scanners send it (#42); it is drawn as FNC1 too, not as a literal GS.
     */
    private fun String.withFnc1For(format: BarcodeFormat): String = when {
        format == BarcodeFormat.GS1_128 -> CODE_128_FNC1 + replace(Gs1.SEPARATOR, CODE_128_FNC1)
        // In a GS1 QR code's alphanumeric mode, which ZXing picks when every character fits it, a single % is FNC1
        // and %% is the % sign. ZXing doesn't escape it.
        format == BarcodeFormat.GS1_QR_CODE && all { it in QR_ALPHANUMERIC } -> replace("%", "%%")
        else -> this
    }

    /** The characters of a QR code's alphanumeric mode. */
    private val QR_ALPHANUMERIC = ('0'..'9') + ('A'..'Z') + " $%*+-./:".toList()

    private const val CODE_128_FNC1 = 'ñ'

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
        BarcodeFormat.DATA_MATRIX, BarcodeFormat.GS1_DATA_MATRIX -> ZxingFormat.DATA_MATRIX
        BarcodeFormat.EAN_8 -> ZxingFormat.EAN_8
        BarcodeFormat.EAN_13 -> ZxingFormat.EAN_13
        BarcodeFormat.ITF -> ZxingFormat.ITF
        BarcodeFormat.PDF_417 -> ZxingFormat.PDF_417
        BarcodeFormat.QR_CODE, BarcodeFormat.GS1_QR_CODE -> ZxingFormat.QR_CODE
        BarcodeFormat.UPC_A -> ZxingFormat.UPC_A
        BarcodeFormat.UPC_E -> ZxingFormat.UPC_E
        // ZXing core has readers but no writers for DataBar.
        BarcodeFormat.DATABAR, BarcodeFormat.DATABAR_EXPANDED, BarcodeFormat.DATABAR_LIMITED -> null
    }
}
