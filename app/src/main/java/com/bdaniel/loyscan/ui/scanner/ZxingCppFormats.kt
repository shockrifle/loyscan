package com.bdaniel.loyscan.ui.scanner

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import zxingcpp.BarcodeReader.Format

/** The zxing-cpp formats the scanner looks for: every symbology a card can be stored in. */
val SCANNED_FORMATS: Set<Format> = Format.entries.filter { it.toBarcodeFormat() != null }.toSet()

/**
 * The app's symbology for a zxing-cpp format, or `null` for one the app can't draw back (Micro QR, rMQR, MaxiCode,
 * DX film edge): the scanner doesn't look for those, and such a card is entered by hand.
 */
fun Format.toBarcodeFormat(): BarcodeFormat? = when (this) {
    Format.AZTEC -> BarcodeFormat.AZTEC
    Format.CODABAR -> BarcodeFormat.CODABAR
    Format.CODE_39 -> BarcodeFormat.CODE_39
    Format.CODE_93 -> BarcodeFormat.CODE_93
    Format.CODE_128 -> BarcodeFormat.CODE_128
    Format.DATA_BAR -> BarcodeFormat.DATABAR
    Format.DATA_BAR_EXPANDED -> BarcodeFormat.DATABAR_EXPANDED
    Format.DATA_BAR_LIMITED -> BarcodeFormat.DATABAR_LIMITED
    Format.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Format.EAN_8 -> BarcodeFormat.EAN_8
    Format.EAN_13 -> BarcodeFormat.EAN_13
    Format.ITF -> BarcodeFormat.ITF
    Format.PDF_417 -> BarcodeFormat.PDF_417
    Format.QR_CODE -> BarcodeFormat.QR_CODE
    Format.UPC_A -> BarcodeFormat.UPC_A
    Format.UPC_E -> BarcodeFormat.UPC_E
    Format.NONE, Format.DX_FILM_EDGE, Format.MAXICODE, Format.MICRO_QR_CODE, Format.RMQR_CODE -> null
}

/**
 * A decoded result as a [ScannedBarcode], or `null` when it has no text or a format the app can't store. [text] is
 * kept exactly: it becomes the card's number as scanned (`LoyaltyCard.number`).
 */
fun scannedBarcode(format: Format, text: String?): ScannedBarcode? {
    if (text.isNullOrEmpty()) return null
    return ScannedBarcode(text, format.toBarcodeFormat() ?: return null)
}
