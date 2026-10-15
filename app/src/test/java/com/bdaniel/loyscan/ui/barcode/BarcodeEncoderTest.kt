package com.bdaniel.loyscan.ui.barcode

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Test

/**
 * Every [BarcodeFormat] either encodes in its own format or falls back (CODE_128, then QR), and what is encoded
 * reads back as the content: each barcode is decoded with ZXing's own reader. Example numbers only.
 */
class BarcodeEncoderTest {

    @Test
    fun everyFormat_encodesAsItselfOrFallsBack_andReadsBackAsTheContent() {
        val expected = mapOf(
            BarcodeFormat.AZTEC to ("LoyScan 0042" to BarcodeFormat.AZTEC),
            BarcodeFormat.CODABAR to ("123456" to BarcodeFormat.CODABAR),
            BarcodeFormat.CODE_39 to ("LOY-42" to BarcodeFormat.CODE_39),
            BarcodeFormat.CODE_93 to ("LOY-42" to BarcodeFormat.CODE_93),
            BarcodeFormat.CODE_128 to ("Loy42-x" to BarcodeFormat.CODE_128),
            BarcodeFormat.DATA_MATRIX to ("LoyScan 0042" to BarcodeFormat.DATA_MATRIX),
            // ZXing has no DataBar writer, so these fall back to CODE_128.
            BarcodeFormat.DATABAR to ("0123456789012" to BarcodeFormat.CODE_128),
            BarcodeFormat.DATABAR_EXPANDED to ("0123456789012" to BarcodeFormat.CODE_128),
            BarcodeFormat.DATABAR_LIMITED to ("0123456789012" to BarcodeFormat.CODE_128),
            BarcodeFormat.EAN_8 to ("96385074" to BarcodeFormat.EAN_8),
            BarcodeFormat.EAN_13 to ("5901234123457" to BarcodeFormat.EAN_13),
            BarcodeFormat.ITF to ("12345678" to BarcodeFormat.ITF),
            BarcodeFormat.PDF_417 to ("LoyScan 0042" to BarcodeFormat.PDF_417),
            BarcodeFormat.QR_CODE to ("LoyScan 0042" to BarcodeFormat.QR_CODE),
            BarcodeFormat.UPC_A to ("036000291452" to BarcodeFormat.UPC_A),
            BarcodeFormat.UPC_E to ("01234565" to BarcodeFormat.UPC_E),
        )
        assertThat(expected.keys).containsExactlyElementsIn(BarcodeFormat.entries)

        expected.forEach { (format, contentAndDrawnAs) ->
            val (content, drawnAs) = contentAndDrawnAs

            val barcode = BarcodeEncoder.encode(content, format)

            assertWithMessage(format.name).that(barcode!!.format).isEqualTo(drawnAs)
            assertWithMessage(format.name).that(decode(barcode)).isEqualTo(content)
        }
    }

    @Test
    fun contentTheFormatCannotHold_fallsBackToCode128() {
        // Not 12/13 digits: a typed number saved anyway against an EAN-13 rule.
        val barcode = BarcodeEncoder.encode("12345", BarcodeFormat.EAN_13)!!

        assertThat(barcode.format).isEqualTo(BarcodeFormat.CODE_128)
        assertThat(decode(barcode)).isEqualTo("12345")
    }

    @Test
    fun eanWithAWrongCheckDigit_fallsBackToCode128() {
        assertThat(BarcodeEncoder.encode("5901234123458", BarcodeFormat.EAN_13)!!.format)
            .isEqualTo(BarcodeFormat.CODE_128)
    }

    @Test
    fun contentCode128CannotHold_fallsBackToQr() {
        val barcode = BarcodeEncoder.encode("Kártya ő 42", BarcodeFormat.EAN_13)!!

        assertThat(barcode.format).isEqualTo(BarcodeFormat.QR_CODE)
        assertThat(decode(barcode)).isEqualTo("Kártya ő 42")
    }

    @Test
    fun emptyContent_encodesNothing() {
        assertThat(BarcodeEncoder.encode("", BarcodeFormat.QR_CODE)).isNull()
    }

    @Test
    fun linearFormats_areOneRowHigh_and2dFormatsAreNot() {
        val linear = BarcodeEncoder.encode("5901234123457", BarcodeFormat.EAN_13)!!
        val qr = BarcodeEncoder.encode("LoyScan", BarcodeFormat.QR_CODE)!!

        assertThat(linear.isLinear).isTrue()
        assertThat(linear.height).isEqualTo(1)
        assertThat(qr.isLinear).isFalse()
        assertThat(qr.height).isEqualTo(qr.width)
    }

    /**
     * Draws [barcode] at 4 px per module (linear ones 40 rows high) inside a 4-module white border, as the screen's
     * white card does (Aztec and Data Matrix come without a quiet zone), and reads it with ZXing.
     */
    private fun decode(barcode: EncodedBarcode): String {
        val scale = 4
        val border = 4 * scale
        val width = barcode.width * scale + 2 * border
        val rows = (if (barcode.isLinear) 10 else barcode.height) * scale + 2 * border
        val pixels = IntArray(width * rows) { i ->
            val px = i % width - border
            val py = i / width - border
            val inside = px >= 0 && py >= 0 && px < barcode.width * scale && py < rows - 2 * border
            val dark = inside && barcode[px / scale, if (barcode.isLinear) 0 else py / scale]
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, rows, pixels)))
        return try {
            MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (e: NotFoundException) {
            throw AssertionError("${barcode.format} didn't read back", e)
        }
    }
}
