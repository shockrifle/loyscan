package com.bdaniel.loyscan.domain

/**
 * A barcode read by the scanner: its [content] exactly as decoded, and the symbology it was printed in. [gs1] marks GS1
 * data (Application Identifiers). The leading FNC1 that marks it isn't part of [content], so a GS1 Code 128, Data
 * Matrix or QR code is read as [BarcodeFormat.GS1_128], [BarcodeFormat.GS1_DATA_MATRIX] or [BarcodeFormat.GS1_QR_CODE],
 * which a card keeps (#45). DataBar is always GS1. A GS1 Aztec or PDF417 keeps its plain format, since ZXing core can't
 * draw them in GS1 mode: saved, it loses the mark.
 */
data class ScannedBarcode(val content: String, val format: BarcodeFormat, val gs1: Boolean = false)
