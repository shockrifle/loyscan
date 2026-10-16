package com.bdaniel.loyscan.domain

/** A barcode read by the scanner: its [content] exactly as decoded, and the symbology it was printed in. */
data class ScannedBarcode(val content: String, val format: BarcodeFormat)
