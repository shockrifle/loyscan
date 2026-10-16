package com.bdaniel.loyscan.ui.scanner

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import zxingcpp.BarcodeReader
import zxingcpp.BarcodeReader.Format

class ZxingCppFormatsTest {

    @Test
    fun everyAppFormat_isLookedForAndMappedBack() {
        val readable = SCANNED_FORMATS.mapNotNull { it.toBarcodeFormat() }

        assertThat(readable).containsExactlyElementsIn(BarcodeFormat.entries)
    }

    @Test
    fun formatsKeepTheirName() {
        val mapped = Format.entries.mapNotNull { format -> format.toBarcodeFormat()?.let { format to it } }

        val renamed = mapped.filter { (zxing, app) -> zxing.name.replace("DATA_BAR", "DATABAR") != app.name }

        assertThat(renamed).isEmpty()
    }

    @Test
    fun symbologiesTheAppCannotDraw_areNotLookedFor() {
        val undrawable = listOf(Format.MICRO_QR_CODE, Format.RMQR_CODE, Format.MAXICODE, Format.DX_FILM_EDGE, Format.NONE)

        assertThat(undrawable.mapNotNull { it.toBarcodeFormat() }).isEmpty()
        assertThat(SCANNED_FORMATS).containsNoneIn(undrawable)
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
    fun scannedBarcode_withoutTextOrInAnUnmappedFormat_isNull() {
        assertThat(scannedBarcode(Format.EAN_13, null)).isNull()
        assertThat(scannedBarcode(Format.EAN_13, "")).isNull()
        assertThat(scannedBarcode(Format.MICRO_QR_CODE, "123")).isNull()
    }
}
