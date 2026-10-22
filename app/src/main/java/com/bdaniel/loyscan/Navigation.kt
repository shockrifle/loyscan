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


/** One card. [countUse] is false when it opens right after being added, which isn't a use at a till. */
@Serializable
data class Detail(val cardId: String, val color: String, val countUse: Boolean = true)

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

/** A scan, or "enter manually", replaces the scanner with the card form, so back from the form returns to the picker. */
fun NavController.scannerDirections(storeId: String) = ScannerDirections(
    onScanned = { barcode ->
        navigate(CardForm(storeId, scannedContent = barcode.content, scannedFormat = barcode.format)) {
            popUpTo<Scanner> { inclusive = true }
        }
    },
    toManualEntry = {
        navigate(CardForm(storeId)) {
            popUpTo<Scanner> { inclusive = true }
        }
    },
    toBack = { navigateUp() }
)


/**
 * Last step of adding a card for the store [storeId]: label and number. [scannedContent] and [scannedFormat] are set
 * when the number comes from a scan.
 */
@Serializable
data class CardForm(val storeId: String, val scannedContent: String? = null, val scannedFormat: BarcodeFormat? = null)

@Immutable
data class CardFormDirections(
    val toCard: (id: String, color: String) -> Unit,
    val toBack: () -> Unit
)

/** The saved card opens in place of the whole add-card flow, so back from it returns to the list. */
fun NavController.cardFormDirections() = CardFormDirections(
    toCard = { id, color ->
        navigate(Detail(id, color, countUse = false)) {
            popUpTo<List>()
        }
    },
    toBack = { navigateUp() }
)
