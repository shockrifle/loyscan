package com.bdaniel.loyscan.ui.scanner

import androidx.camera.core.ImageProxy
import com.bdaniel.loyscan.domain.ScannedBarcode
import timber.log.Timber
import zxingcpp.BarcodeReader

/**
 * Reads barcodes with zxing-cpp. Creating one loads its native library, which throws `UnsatisfiedLinkError` on an
 * ABI it doesn't ship. Not unit-tested: the native library doesn't run on the JVM; what it does with the results is
 * (`ZxingCppFormatsTest`, `ZxingCppResultsTest`).
 */
class ZxingCppFrameReader : FrameReader {
    private val reader = BarcodeReader(ZXING_CPP_OPTIONS)

    override fun read(image: ImageProxy): ScannedBarcode? = firstScannedBarcode(reader.read(image))
}

/**
 * The first result the app can store. Each one it drops (a format it can't draw back, or no text) is logged with its
 * format, never its content: otherwise a card that never scans leaves no trace (wiki: bugs/2026-10-19-zxing-cpp-3-0-databar).
 *
 * GS1 data is taken from the content type, which zxing-cpp sets from the same AI flag as the symbology identifier's
 * GS1 modifier (`]C1` for GS1-128, `]e0` for DataBar, `]d2` for Data Matrix, `]Q3` for QR).
 */
internal fun firstScannedBarcode(results: kotlin.collections.List<BarcodeReader.Result>): ScannedBarcode? {
    for (result in results) {
        scannedBarcode(result.format, result.text, gs1 = result.contentType == BarcodeReader.ContentType.GS1)?.let { return it }
        Timber.d("Dropped a %s result (%s)", result.format, if (result.text.isNullOrEmpty()) "no text" else "format not stored")
    }
    return null
}

/**
 * Plain text, so the content is the barcode's own characters: the default (HRI) writes GS1 data as `(01)…`, which
 * would change the card's number. Rotation and inversion cover cards held sideways and light-on-dark barcodes.
 */
internal val ZXING_CPP_OPTIONS = BarcodeReader.Options(
    formats = SCANNED_FORMATS,
    tryHarder = true,
    tryRotate = true,
    tryInvert = true,
    maxNumberOfSymbols = 1,
    textMode = BarcodeReader.TextMode.PLAIN,
)
