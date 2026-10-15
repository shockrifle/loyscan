package com.bdaniel.loyscan.domain

import kotlinx.serialization.Serializable

/** Barcode symbologies a loyalty card can use. Names match ZXing's `BarcodeFormat` where one exists. */
@Serializable
enum class BarcodeFormat(
    /** The symbology's usual name, for showing to the user. */
    val displayName: String,
) {
    AZTEC("Aztec"),
    CODABAR("Codabar"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    CODE_128("Code 128"),
    DATA_MATRIX("Data Matrix"),
    DATABAR("GS1 DataBar"),
    DATABAR_EXPANDED("GS1 DataBar Expanded"),
    DATABAR_LIMITED("GS1 DataBar Limited"),
    EAN_8("EAN-8"),
    EAN_13("EAN-13"),
    ITF("ITF"),
    PDF_417("PDF417"),
    QR_CODE("QR code"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
}
