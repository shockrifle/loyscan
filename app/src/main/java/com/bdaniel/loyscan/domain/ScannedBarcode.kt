package com.bdaniel.loyscan.domain

/**
 * A barcode read by the scanner: its [content] exactly as decoded, and the symbology it was printed in. [gs1] marks GS1
 * data (Application Identifiers): a GS1-128 barcode is a [BarcodeFormat.CODE_128] with [gs1] set, since its leading
 * FNC1 isn't part of [content].
 */
data class ScannedBarcode(val content: String, val format: BarcodeFormat, val gs1: Boolean = false)
