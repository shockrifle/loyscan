package com.bdaniel.loyscan.ui.barcode

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.Gs1
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import org.junit.Test

/**
 * Every [BarcodeFormat] either encodes in its own format or falls back (CODE_128, then QR; for GS1 data GS1-128, then
 * GS1 QR), and what is encoded reads back as the content: each barcode is decoded with ZXing's own reader. Example
 * numbers only.
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
            // ZXing has no DataBar writer, so these fall back to GS1-128: DataBar always holds GS1 data.
            BarcodeFormat.DATABAR to ("0109012345678901" to BarcodeFormat.GS1_128),
            BarcodeFormat.DATABAR_EXPANDED to ("0109012345678901" to BarcodeFormat.GS1_128),
            BarcodeFormat.DATABAR_LIMITED to ("0109012345678901" to BarcodeFormat.GS1_128),
            BarcodeFormat.EAN_8 to ("96385074" to BarcodeFormat.EAN_8),
            BarcodeFormat.GS1_128 to ("9799123456789012" to BarcodeFormat.GS1_128),
            BarcodeFormat.GS1_DATA_MATRIX to ("0109012345678901" to BarcodeFormat.GS1_DATA_MATRIX),
            BarcodeFormat.GS1_QR_CODE to ("0109012345678901" to BarcodeFormat.GS1_QR_CODE),
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
    fun everyFormatACustomStoreCanPick_isDrawnAsItself() { // #47: each can draw a number that fits it as itself
        val examples = mapOf(
            BarcodeFormat.CODABAR to "123456",
            BarcodeFormat.CODE_39 to "LOY-42",
            BarcodeFormat.CODE_93 to "LOY-42",
            BarcodeFormat.EAN_8 to "96385074",
            BarcodeFormat.EAN_13 to "5901234123457",
            BarcodeFormat.ITF to "12345678",
            BarcodeFormat.UPC_A to "036000291452",
            BarcodeFormat.UPC_E to "01234565",
        )

        BarcodeFormat.forCustomStores.forEach { format ->
            val content = examples[format] ?: "LoyScan 0042".takeIf { format != BarcodeFormat.CODE_128 } ?: "Loy42-x"

            val barcode = BarcodeEncoder.encode(content, format)

            assertWithMessage(format.name).that(barcode!!.format).isEqualTo(format)
            assertWithMessage(format.name).that(decode(barcode)).isEqualTo(content)
        }
    }

    @Test
    fun gs1_128_isCode128WithALeadingFnc1() { // #33: the FNC1 tells a till the content is GS1 data
        val gs1 = read(BarcodeEncoder.encode("9799123456789012", BarcodeFormat.GS1_128)!!)
        val plain = read(BarcodeEncoder.encode("9799123456789012", BarcodeFormat.CODE_128)!!)

        // Symbology identifier "]C1": Code 128 with FNC1 in the first position; "]C0": no FNC1.
        assertThat(gs1.resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER]).isEqualTo("]C1")
        assertThat(gs1.text).isEqualTo("9799123456789012")
        assertThat(plain.resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER]).isEqualTo("]C0")
    }

    @Test
    fun gs1_128_separatorIsDrawnAsFnc1() { // #42: zxing-cpp returns the FNC1 between two GS1 fields as GS (U+001D)
        val content = "0109501101020917" + "1012AB\u001D" + "21XYZ"
        // ZXing's Code 128 writer writes FNC1 for U+00F1; a GS as itself would be a literal character in code set A.
        val fnc1 = EncodedBarcode(
            BarcodeFormat.GS1_128,
            MultiFormatWriter().encode(
                "ñ0109501101020917" + "1012ABñ" + "21XYZ", ZxingFormat.CODE_128, 0, 0, mapOf(EncodeHintType.MARGIN to 0),
            ),
        )

        val barcode = BarcodeEncoder.encode(content, BarcodeFormat.GS1_128)!!

        assertThat(barcode.bars()).isEqualTo(fnc1.bars())
        // ZXing's reader returns a later FNC1 as GS only when told the data is GS1 (zxing-cpp always does), and then
        // puts the symbology identifier in front: "]C1" is Code 128 with FNC1 in the first position.
        assertThat(read(barcode, DecodeHintType.ASSUME_GS1).text).isEqualTo("]C1$content")
    }

    @Test
    fun gs1DataMatrixAndQr_areDrawnInGs1Mode() { // #45: a scanned GS1 Data Matrix or QR keeps its FNC1
        // Symbology identifiers: "]d2" Data Matrix and "]Q3" QR with FNC1 in the first position; "]d1" / "]Q1" without.
        val expected = mapOf(
            BarcodeFormat.GS1_DATA_MATRIX to "]d2",
            BarcodeFormat.DATA_MATRIX to "]d1",
            BarcodeFormat.GS1_QR_CODE to "]Q3",
            BarcodeFormat.QR_CODE to "]Q1",
        )

        expected.forEach { (format, identifier) ->
            val barcode = BarcodeEncoder.encode("0109012345678901", format)!!

            assertWithMessage(format.name).that(read(barcode).resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER])
                .isEqualTo(identifier)
            assertWithMessage(format.name).that(decode(barcode)).isEqualTo("0109012345678901")
        }
    }

    @Test
    fun gs1DataMatrixAndQr_separatorReadsBackAsASeparator() { // #45: the FNC1 between two fields, held as GS (#42)
        // Reading back can't tell an FNC1 from a literal GS: ZXing's Data Matrix reader returns both as GS. That the
        // compact encoder writes FNC1 for it is from its source (MinimalEncoder's fnc1 parameter), not this test.
        val content = "0109501101020917" + "1012AB" + "21XYZ"
        listOf(BarcodeFormat.GS1_DATA_MATRIX, BarcodeFormat.GS1_QR_CODE).forEach { format ->

            val barcode = BarcodeEncoder.encode(content, format)!!

            assertWithMessage(format.name).that(barcode.format).isEqualTo(format)
            assertWithMessage(format.name).that(decode(barcode)).isEqualTo(content)
        }
    }

    @Test
    fun gs1Qr_percentSignIsNotReadAsASeparator() { // #45
        // In a GS1 QR code's alphanumeric mode a single % stands for FNC1, and %% for the % sign itself.
        val barcode = BarcodeEncoder.encode("91AB%12", BarcodeFormat.GS1_QR_CODE)!!

        assertThat(decode(barcode)).isEqualTo("91AB%12")
    }

    @Test
    fun nonGs1ContentOnAGs1Card_fallsBackWithoutTheGs1Mark() { // #45 review: an FNC1 would make a GS1 till reject it
        // Not a GS1 element string (AI 00 needs 18 digits), e.g. a number typed for a store whose format is DataBar.
        val barcode = BarcodeEncoder.encode("0042", BarcodeFormat.DATABAR)!!

        assertThat(barcode.format).isEqualTo(BarcodeFormat.CODE_128)
        assertThat(read(barcode).resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER]).isEqualTo("]C0")
    }

    @Test
    fun dataBar_isDrawnAsGs1_128() { // #45: ZXing can't write DataBar; its fallback keeps the GS1 mark
        listOf(BarcodeFormat.DATABAR, BarcodeFormat.DATABAR_EXPANDED, BarcodeFormat.DATABAR_LIMITED).forEach { format ->

            val barcode = read(BarcodeEncoder.encode("0109012345678901", format)!!)

            assertWithMessage(format.name).that(barcode.resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER])
                .isEqualTo("]C1")
        }
    }

    @Test
    fun gs1ContentCode128CannotHold_fallsBackToGs1Qr() { // #45: GS1 data stays GS1 in every fallback
        listOf(BarcodeFormat.GS1_128, BarcodeFormat.DATABAR).forEach { format ->

            val barcode = BarcodeEncoder.encode("91Kártya", format)!!

            assertWithMessage(format.name).that(barcode.format).isEqualTo(BarcodeFormat.GS1_QR_CODE)
            assertWithMessage(format.name).that(read(barcode).resultMetadata[ResultMetadataType.SYMBOLOGY_IDENTIFIER])
                .isEqualTo("]Q3")
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
    fun code128ContentWithAnEscapeCharacter_fallsBackToQr() { // #46
        // ZXing's Code 128 writer writes FNC1-FNC4 for these four characters instead of the letter, and raises no error.
        listOf('ñ', 'ò', 'ó', 'ô').forEach { letter ->
            mapOf(
                (BarcodeFormat.CODE_128 to "Loy${letter}42") to BarcodeFormat.QR_CODE,
                // GS1 data falls back to GS1 QR (#45).
                (BarcodeFormat.GS1_128 to "91Loy${letter}42") to BarcodeFormat.GS1_QR_CODE,
                (BarcodeFormat.DATABAR to "91Loy${letter}42") to BarcodeFormat.GS1_QR_CODE,
            ).forEach { (formatAndContent, drawnAs) ->
                val (format, content) = formatAndContent

                val barcode = BarcodeEncoder.encode(content, format)!!

                assertWithMessage("$format $content").that(barcode.format).isEqualTo(drawnAs)
                assertWithMessage("$format $content").that(decode(barcode)).isEqualTo(content)
            }
        }
    }

    @Test
    fun latin1Content_isEncodedWithoutACharsetHeader() {
        // A UTF-8 hint makes ZXing prepend an ECI header, which the card's own barcode doesn't have: a till may pass
        // it on as a prefix or reject the code. ZXing's reader strips it, so compare with a hint-free encoding.
        listOf(
            BarcodeFormat.AZTEC to ZxingFormat.AZTEC,
            BarcodeFormat.PDF_417 to ZxingFormat.PDF_417,
            BarcodeFormat.QR_CODE to ZxingFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX to ZxingFormat.DATA_MATRIX,
        ).forEach { (format, zxing) ->
            val content = "https://example.com/c/abc123"
            val plain = MultiFormatWriter().encode(content, zxing, 0, 0, mapOf(EncodeHintType.MARGIN to 0))

            val barcode = BarcodeEncoder.encode(content, format)!!

            assertWithMessage(format.name).that(barcode.modules(like = plain)).isEqualTo(plain.modules())
        }
    }

    @Test
    fun everyBarcode_carriesItsSymbologysQuietZone() {
        // Minimum light modules on each side, per symbology spec (EAN/UPC: the larger, left one). ZXing gives 1D codes
        // only 4-5 a side at size 0, and Aztec and Data Matrix none.
        val minimum = mapOf(
            BarcodeFormat.CODE_128 to ("Loy42-x" to 10),
            BarcodeFormat.EAN_13 to ("5901234123457" to 11),
            BarcodeFormat.UPC_E to ("01234565" to 9),
            BarcodeFormat.ITF to ("12345678" to 10),
            BarcodeFormat.QR_CODE to ("LoyScan 0042" to 4),
            BarcodeFormat.DATA_MATRIX to ("LoyScan 0042" to 1),
            BarcodeFormat.GS1_QR_CODE to ("0109012345678901" to 4),
            BarcodeFormat.GS1_DATA_MATRIX to ("0109012345678901" to 1),
            BarcodeFormat.PDF_417 to ("LoyScan 0042" to 2),
        )
        minimum.forEach { (format, contentAndModules) ->
            val (content, modules) = contentAndModules

            val barcode = BarcodeEncoder.encode(content, format)!!

            val rows = if (barcode.isLinear) 0 until 1 else 0 until barcode.height
            fun lightColumn(x: Int) = rows.none { barcode[x, it] }
            fun lightRow(y: Int) = (0 until barcode.width).none { barcode[it, y] }
            assertWithMessage("$format left").that((0 until modules).all(::lightColumn)).isTrue()
            assertWithMessage("$format right").that((barcode.width - modules until barcode.width).all(::lightColumn)).isTrue()
            if (!barcode.isLinear) {
                assertWithMessage("$format top").that((0 until modules).all(::lightRow)).isTrue()
                assertWithMessage("$format bottom").that((barcode.height - modules until barcode.height).all(::lightRow)).isTrue()
            }
        }
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

    /** A 1D barcode's row as text, `1` for a dark module: short enough to read in a failure message. */
    private fun EncodedBarcode.bars() = (0 until width).joinToString("") { if (this[it, 0]) "1" else "0" }

    private fun BitMatrix.modules() = (0 until height).map { y -> (0 until width).map { x -> this[x, y] } }

    /** The modules of the symbol [like] is, without the quiet zone [EncodedBarcode] adds around it. */
    private fun EncodedBarcode.modules(like: BitMatrix): List<List<Boolean>> {
        val margin = (width - like.width) / 2
        return (0 until like.height).map { y -> (0 until like.width).map { x -> this[x + margin, y + margin] } }
    }

    /**
     * Draws [barcode] at 4 px per module (linear ones 40 rows high), with no margin beyond its own: every barcode must
     * carry the quiet zone a reader needs, whatever the screen puts around it. Then reads it with ZXing.
     *
     * ZXing's Data Matrix reader returns every FNC1 as GS, the leading one too (its identifier, "]d2", already says the
     * data is GS1); a GS1 reader drops that one, so it is dropped here.
     */
    private fun decode(barcode: EncodedBarcode): String = read(barcode).text.let {
        if (barcode.format == BarcodeFormat.GS1_DATA_MATRIX) it.removePrefix(Gs1.SEPARATOR.toString()) else it
    }

    private fun read(barcode: EncodedBarcode, vararg hints: DecodeHintType): Result {
        val scale = 4
        val width = barcode.width * scale
        val rows = if (barcode.isLinear) 40 else barcode.height * scale
        val pixels = IntArray(width * rows) { i ->
            val dark = barcode[(i % width) / scale, if (barcode.isLinear) 0 else (i / width) / scale]
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, rows, pixels)))
        return try {
            MultiFormatReader().decode(bitmap, (hints.toList() + DecodeHintType.TRY_HARDER).associateWith { true })
        } catch (e: NotFoundException) {
            throw AssertionError("${barcode.format} didn't read back", e)
        }
    }
}
