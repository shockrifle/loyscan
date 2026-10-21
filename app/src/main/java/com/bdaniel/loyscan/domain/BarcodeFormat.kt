package com.bdaniel.loyscan.domain

import kotlinx.serialization.Serializable

/**
 * Barcode symbologies a loyalty card can use. Names match ZXing's `BarcodeFormat` where one exists. The `GS1_` formats
 * aren't ZXing formats: each is its symbology with a leading FNC1 that marks GS1 data (Code 128 for [GS1_128]), and is
 * drawn that way. A scan marked GS1 is stored in one, so the card keeps the mark (#33, #45).
 */
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
    GS1_128("GS1-128"),
    GS1_DATA_MATRIX("GS1 Data Matrix"),
    GS1_QR_CODE("GS1 QR code"),
    ITF("ITF"),
    PDF_417("PDF417"),
    QR_CODE("QR code"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    ;

    /** Always holds GS1 data. */
    val isGs1: Boolean
        get() = this == GS1_128 || this == GS1_DATA_MATRIX || this == GS1_QR_CODE ||
            this == DATABAR || this == DATABAR_EXPANDED || this == DATABAR_LIMITED
}
