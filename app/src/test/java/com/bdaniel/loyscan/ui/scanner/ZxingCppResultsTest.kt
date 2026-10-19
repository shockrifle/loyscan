package com.bdaniel.loyscan.ui.scanner

import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import zxingcpp.BarcodeReader.ContentType
import zxingcpp.BarcodeReader.Format
import zxingcpp.BarcodeReader.Position
import zxingcpp.BarcodeReader.Result

/** Picking the scan from zxing-cpp's results. Robolectric only for `Result`'s `android.graphics.Point`s. */
@RunWith(AndroidJUnit4::class)
class ZxingCppResultsTest {

    private val logged = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logged += message
        }
    }

    @Before
    fun plant() = Timber.plant(tree)

    @After
    fun uproot() = Timber.uproot(tree)

    private fun result(
        format: Format, text: String?, contentType: ContentType = ContentType.TEXT, symbologyIdentifier: String? = null,
    ) = Result(
        format = format, bytes = null, text = text, contentType = contentType,
        position = Position(Point(), Point(), Point(), Point(), 0.0), orientation = 0, ecLevel = null,
        symbologyIdentifier = symbologyIdentifier, sequenceSize = -1, sequenceIndex = -1, sequenceId = null, readerInit = false,
        lineCount = 1, error = null,
    )

    @Test
    fun theFirstResultTheAppCanStore_isTheScan() {
        val scan = firstScannedBarcode(listOf(result(Format.MICRO_PDF_417, "123"), result(Format.QR_CODE_MODEL_2, "456")))

        assertThat(scan).isEqualTo(ScannedBarcode("456", BarcodeFormat.QR_CODE))
    }

    @Test
    fun droppedResults_areLoggedWithTheirFormatButNotTheirContent() { // a card that never scans must leave a trace
        firstScannedBarcode(listOf(result(Format.MICRO_PDF_417, "secret-123"), result(Format.EAN_13, null)))

        assertThat(logged).hasSize(2)
        assertThat(logged[0]).contains("MICRO_PDF_417")
        assertThat(logged[1]).contains("EAN_13")
        assertThat(logged.joinToString()).doesNotContain("secret-123")
    }

    @Test
    fun aGs1Code128Result_isRecordedAsGs1() { // #41: zxing-cpp marks it with "]C1" and content type GS1
        val scan = firstScannedBarcode(listOf(result(Format.CODE_128, "9799123456789012", ContentType.GS1, "]C1")))

        assertThat(scan).isEqualTo(ScannedBarcode("9799123456789012", BarcodeFormat.CODE_128, gs1 = true))
    }

    @Test
    fun aPlainCode128Result_isNotGs1() {
        val scan = firstScannedBarcode(listOf(result(Format.CODE_128, "9799123456789012", ContentType.TEXT, "]C0")))

        assertThat(scan).isEqualTo(ScannedBarcode("9799123456789012", BarcodeFormat.CODE_128, gs1 = false))
    }
}
