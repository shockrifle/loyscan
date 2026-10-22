package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import kotlinx.serialization.Serializable

@Serializable
object List

@Immutable
data class ListDirections(
    val toCard: (id: String, color: String) -> Unit,
    val toAddCard: () -> Unit,
    val toBack: () -> Unit
)

fun NavController.listDirections() = ListDirections(
    toCard = { id, color ->
        navigate(Detail(id, color))
    },
    toAddCard = { navigate(StorePicker) },
    toBack = { navigateUp() }
)


@Serializable
data class Detail(val cardId: String, val color: String)

@Immutable
data class DetailDirections(
    val toBack: () -> Unit
)

fun NavController.detailDirections() = DetailDirections(
    toBack = { navigateUp() }
)


/** First step of adding a card (#6): which store it is for. */
@Serializable
object StorePicker

@Immutable
data class StorePickerDirections(
    val toStore: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

fun NavController.storePickerDirections() = StorePickerDirections(
    toStore = { storeId -> navigate(Scanner(storeId)) },
    toBack = { navigateUp() }
)


/** Scans the card being added for the store [storeId]. */
@Serializable
data class Scanner(val storeId: String)

@Immutable
data class ScannerDirections(
    val onScanned: (ScannedBarcode) -> Unit,
    val toManualEntry: () -> Unit,
    val toBack: () -> Unit
)

// Interim until the add-card flow (#6): a scan is only shown, and "enter manually" goes back. See
// wiki/hacks/scanner-result-dialog-until-add-card-flow.md.
fun NavController.scannerDirections() = ScannerDirections(
    onScanned = { barcode ->
        navigate(ScanResult(barcode.content, barcode.format, barcode.gs1)) {
            popUpTo<Scanner> { inclusive = true }
        }
    },
    toManualEntry = { navigateUp() },
    toBack = { navigateUp() }
)


/** Interim result of a scan, shown as a dialog over the list (see [scannerDirections]). */
@Serializable
data class ScanResult(val content: String, val format: BarcodeFormat, val gs1: Boolean = false)
