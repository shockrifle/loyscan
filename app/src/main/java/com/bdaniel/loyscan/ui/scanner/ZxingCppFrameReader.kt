package com.bdaniel.loyscan.ui.scanner

import androidx.camera.core.ImageProxy
import com.bdaniel.loyscan.domain.ScannedBarcode
import zxingcpp.BarcodeReader

/**
 * Reads barcodes with zxing-cpp. Creating one loads its native library, which throws `UnsatisfiedLinkError` on an
 * ABI it doesn't ship. Not unit-tested: the native library doesn't run on the JVM; the format and content mapping
 * it uses is tested in `ZxingCppFormatsTest`.
 */
class ZxingCppFrameReader : FrameReader {
    private val reader = BarcodeReader(ZXING_CPP_OPTIONS)

    override fun read(image: ImageProxy): ScannedBarcode? =
        reader.read(image).firstNotNullOfOrNull { scannedBarcode(it.format, it.text) }
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
