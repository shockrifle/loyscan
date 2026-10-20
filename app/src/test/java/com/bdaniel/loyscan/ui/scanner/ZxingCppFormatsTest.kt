package com.bdaniel.loyscan.ui.scanner

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Format

class ZxingCppFormatsTest {

    @Test
    fun everyAppFormat_isLookedForAndMappedBack() { // GS1-128 is a Code 128 marked GS1, not a zxing-cpp format
        val readable = SCANNED_FORMATS.flatMap { it.members() }.mapNotNull { it.toBarcodeFormat() }.toSet()

        assertThat(readable + scannedBarcode(Format.CODE_128, "90", gs1 = true)!!.format)
            .containsExactlyElementsIn(BarcodeFormat.entries)
    }

    @Test
    fun theVariantsTheReaderReports_mapToTheirSymbology() { // 3.0.2 couldn't hand these back at all (bug page)
        val expected = mapOf(
            Format.DATA_BAR_OMNI to BarcodeFormat.DATABAR,
            Format.DATA_BAR_STK to BarcodeFormat.DATABAR,
            Format.DATA_BAR_STK_OMNI to BarcodeFormat.DATABAR,
            Format.DATA_BAR_LTD to BarcodeFormat.DATABAR_LIMITED,
            Format.DATA_BAR_EXP to BarcodeFormat.DATABAR_EXPANDED,
            Format.DATA_BAR_EXP_STK to BarcodeFormat.DATABAR_EXPANDED,
            Format.CODE_39_STD to BarcodeFormat.CODE_39,
            Format.CODE_39_EXT to BarcodeFormat.CODE_39,
            Format.ITF_14 to BarcodeFormat.ITF,
            Format.ISBN to BarcodeFormat.EAN_13,
            Format.COMPACT_PDF_417 to BarcodeFormat.PDF_417,
            Format.AZTEC_CODE to BarcodeFormat.AZTEC,
            Format.QR_CODE_MODEL_1 to BarcodeFormat.QR_CODE,
            Format.QR_CODE_MODEL_2 to BarcodeFormat.QR_CODE,
        )

        assertThat(expected.keys.associateWith { it.toBarcodeFormat() }).isEqualTo(expected)
    }

    @Test
    fun everyFormatLookedFor_standsOnlyForFormatsThatMap() { // a family value also asks for all its variants
        val unmappableMembers = SCANNED_FORMATS.associateWith { requested -> requested.members().filter { it.toBarcodeFormat() == null } }
            .filterValues { it.isNotEmpty() }

        // Plain PDF417 is reported as the family value itself, so the family must be asked for, Micro PDF417 included.
        assertThat(unmappableMembers).containsExactly(Format.PDF_417, listOf(Format.MICRO_PDF_417))
    }

    @Test
    fun code39_isAskedForWithoutItsCode32AndPznReadings() { // same bars, different text: such a card would never scan
        assertThat(SCANNED_FORMATS).containsAtLeast(Format.CODE_39_STD, Format.CODE_39_EXT)
        assertThat(SCANNED_FORMATS).containsNoneOf(Format.CODE_39, Format.CODE_32, Format.PZN)
    }

    @Test
    fun members_ofAFamilyShareItsSymbologyByte() {
        assertThat(Format.QR_CODE.members()).containsExactly(
            Format.QR_CODE, Format.QR_CODE_MODEL_1, Format.QR_CODE_MODEL_2, Format.MICRO_QR_CODE, Format.RMQR_CODE,
        )
        assertThat(Format.QR_CODE_MODEL_2.members()).containsExactly(Format.QR_CODE_MODEL_2)
    }

    @Test
    fun formatsTheAppCannotDrawBack_areNotMapped() {
        val undrawable = listOf(
            Format.MICRO_QR_CODE, Format.RMQR_CODE, Format.MICRO_PDF_417, Format.AZTEC_RUNE, Format.MAXI_CODE,
            Format.CODE_32, Format.PZN, Format.EAN_2, Format.EAN_5, Format.TELEPEN, Format.DX_FILM_EDGE,
            Format.OTHER_BARCODE, Format.NONE, Format.ALL,
        )

        assertThat(undrawable.mapNotNull { it.toBarcodeFormat() }).isEmpty()
    }

    @Test
    fun theReader_looksForTheAppsFormatsAndReturnsPlainText() { // HRI would turn GS1 content into "(01)…"
        assertThat(ZXING_CPP_OPTIONS.formats).isEqualTo(SCANNED_FORMATS)
        assertThat(ZXING_CPP_OPTIONS.textMode).isEqualTo(BarcodeReader.TextMode.PLAIN)
    }

    @Test
    fun scannedBarcode_keepsTheContentExactly() {
        assertThat(scannedBarcode(Format.CODE_128, " 90 12\u001D34 ")).isEqualTo(ScannedBarcode(" 90 12\u001D34 ", BarcodeFormat.CODE_128))
    }

    @Test
    fun scannedBarcode_code128MarkedGs1_isGs1_128() { // #33: so the card keeps its FNC1 when drawn
        assertThat(scannedBarcode(Format.CODE_128, "9799123456789012", gs1 = true))
            .isEqualTo(ScannedBarcode("9799123456789012", BarcodeFormat.GS1_128, gs1 = true))
    }

    @Test
    fun scannedBarcode_otherFormatsMarkedGs1_keepTheirFormat() { // DataBar is always GS1; QR and Data Matrix may be
        assertThat(scannedBarcode(Format.DATA_BAR_OMNI, "0109012345678901", gs1 = true))
            .isEqualTo(ScannedBarcode("0109012345678901", BarcodeFormat.DATABAR, gs1 = true))
        assertThat(scannedBarcode(Format.QR_CODE_MODEL_2, "0109012345678901", gs1 = true))
            .isEqualTo(ScannedBarcode("0109012345678901", BarcodeFormat.QR_CODE, gs1 = true))
    }

    @Test
    fun scannedBarcode_withoutTextOrInAnUnmappedFormat_isNull() {
        assertThat(scannedBarcode(Format.EAN_13, null)).isNull()
        assertThat(scannedBarcode(Format.EAN_13, "")).isNull()
        assertThat(scannedBarcode(Format.MICRO_QR_CODE, "123")).isNull()
    }
}
