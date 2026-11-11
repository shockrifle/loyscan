package com.bdaniel.loyscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bdaniel.loyscan.R
import com.bdaniel.loyscan.domain.BarcodeFormat

/**
 * The name the user sees for [format], in the phone's language (#72). Only the QR names are translated ("QR-kód");
 * the other symbology names (EAN-13, Code 128, Data Matrix…) are proper names, shown as [BarcodeFormat.displayName]
 * says ([wiki: 2026-11-09-ui-language]).
 */
@Composable
fun barcodeFormatName(format: BarcodeFormat): String = when (format) {
    BarcodeFormat.QR_CODE -> stringResource(R.string.barcode_format_qr_code)
    BarcodeFormat.GS1_QR_CODE -> stringResource(R.string.barcode_format_gs1_qr_code)
    else -> format.displayName
}
