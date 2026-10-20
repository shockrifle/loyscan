package com.bdaniel.loyscan.domain

/**
 * A barcode read by the scanner: its [content] exactly as decoded, and the symbology it was printed in. [gs1] marks GS1
 * data (Application Identifiers). A GS1-128 barcode is read as [BarcodeFormat.GS1_128], since its leading FNC1 isn't
 * part of [content]; other GS1 carriers (DataBar, GS1 QR, GS1 Data Matrix) keep their own format with [gs1] set.
 */
data class ScannedBarcode(val content: String, val format: BarcodeFormat, val gs1: Boolean = false)
