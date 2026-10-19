package com.bdaniel.loyscan.ui.scanner

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import zxingcpp.BarcodeReader.Format

/**
 * The zxing-cpp formats the scanner looks for: every family and variant that maps to a symbology a card can be
 * stored in. Asking for both is harmless (zxing-cpp merges them) and doesn't rely on how a family expands.
 */
val SCANNED_FORMATS: Set<Format> = Format.entries.filter { it.toBarcodeFormat() != null }.toSet()

/**
 * The app's symbology for a zxing-cpp format. zxing-cpp 3.1 reports variants (`DATA_BAR_OMNI`, `QR_CODE_MODEL_2`,
 * `CODE_39_STD`, …), which map to their symbology; the app draws them back in its standard form. `null` for what the
 * app can't draw back, whose scan is ignored so the user enters the number: Micro QR, rMQR, Micro PDF417, Aztec
 * runes, Code 32 / PZN (decoded text differs from the bars), EAN add-ons, and the umbrella values.
 *
 * No `else` branch, on purpose: a format a later zxing-cpp adds must be placed here, or the build fails. A reported
 * format nothing could map is how DataBar scans were lost before (wiki: bugs/2026-10-19-zxing-cpp-3-0-databar).
 */
fun Format.toBarcodeFormat(): BarcodeFormat? = when (this) {
    Format.AZTEC, Format.AZTEC_CODE -> BarcodeFormat.AZTEC
    Format.CODABAR -> BarcodeFormat.CODABAR
    Format.CODE_39, Format.CODE_39_STD, Format.CODE_39_EXT -> BarcodeFormat.CODE_39
    Format.CODE_93 -> BarcodeFormat.CODE_93
    Format.CODE_128 -> BarcodeFormat.CODE_128
    Format.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Format.DATA_BAR, Format.DATA_BAR_OMNI, Format.DATA_BAR_STK, Format.DATA_BAR_STK_OMNI -> BarcodeFormat.DATABAR
    Format.DATA_BAR_EXP, Format.DATA_BAR_EXP_STK -> BarcodeFormat.DATABAR_EXPANDED
    Format.DATA_BAR_LTD -> BarcodeFormat.DATABAR_LIMITED
    Format.EAN_8 -> BarcodeFormat.EAN_8
    Format.EAN_13, Format.ISBN -> BarcodeFormat.EAN_13
    Format.ITF, Format.ITF_14 -> BarcodeFormat.ITF
    Format.PDF_417, Format.COMPACT_PDF_417 -> BarcodeFormat.PDF_417
    Format.QR_CODE, Format.QR_CODE_MODEL_1, Format.QR_CODE_MODEL_2 -> BarcodeFormat.QR_CODE
    Format.UPC_A -> BarcodeFormat.UPC_A
    Format.UPC_E -> BarcodeFormat.UPC_E
    Format.NONE, Format.ALL, Format.ALL_READABLE, Format.ALL_CREATABLE, Format.ALL_LINEAR, Format.ALL_MATRIX,
    Format.ALL_GS1, Format.ALL_RETAIL, Format.ALL_INDUSTRIAL, Format.EAN_UPC,
    Format.AZTEC_RUNE, Format.CODE_32, Format.PZN, Format.MICRO_PDF_417, Format.MICRO_QR_CODE, Format.RMQR_CODE,
    Format.EAN_2, Format.EAN_5, Format.TELEPEN, Format.TELEPEN_ALPHA, Format.TELEPEN_NUMERIC, Format.OTHER_BARCODE,
    Format.DX_FILM_EDGE, Format.MAXI_CODE -> null
}

/**
 * A decoded result as a [ScannedBarcode], or `null` when it has no text or a format the app can't store. [text] is
 * kept exactly: it becomes the card's number as scanned (`LoyaltyCard.number`).
 */
fun scannedBarcode(format: Format, text: String?): ScannedBarcode? {
    if (text.isNullOrEmpty()) return null
    return ScannedBarcode(text, format.toBarcodeFormat() ?: return null)
}
