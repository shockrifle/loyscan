package com.bdaniel.loyscan.domain

import kotlinx.serialization.Serializable

/** Barcode symbologies a loyalty card can use. Names match ZXing's `BarcodeFormat` where one exists. */
@Serializable
enum class BarcodeFormat {
    AZTEC,
    CODABAR,
    CODE_39,
    CODE_93,
    CODE_128,
    DATA_MATRIX,
    DATABAR,
    DATABAR_EXPANDED,
    DATABAR_LIMITED,
    EAN_8,
    EAN_13,
    ITF,
    PDF_417,
    QR_CODE,
    UPC_A,
    UPC_E,
}
