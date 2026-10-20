package com.bdaniel.loyscan.domain

/**
 * A barcode read by the scanner: its [content] exactly as decoded, and the symbology it was printed in. [gs1] marks GS1
 * data (Application Identifiers). A GS1-128 barcode is read as [BarcodeFormat.GS1_128], since its leading FNC1 isn't
 * part of [content]. Other GS1 carriers (DataBar, GS1 QR, GS1 Data Matrix) keep their own format with [gs1] set, but
 * a card has no place for the mark: saved, they are drawn without it (DataBar as plain Code 128).
 */
data class ScannedBarcode(val content: String, val format: BarcodeFormat, val gs1: Boolean = false)
