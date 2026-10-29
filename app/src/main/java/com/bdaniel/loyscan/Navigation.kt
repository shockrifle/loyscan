package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import kotlinx.serialization.Serializable

@Serializable
object CardList

@Immutable
data class ListDirections(
    val toCard: (id: String, color: String) -> Unit,
    val toAddCard: () -> Unit,
)

fun NavController.listDirections() = ListDirections(
    toCard = { id, color ->
        navigate(Detail(id, color))
    },
    toAddCard = { navigate(StorePicker) },
)


/** One card. [countUse] is false when it opens right after being added, which isn't a use at a till. */
@Serializable
data class Detail(val cardId: String, val color: String, val countUse: Boolean = true)

@Immutable
data class DetailDirections(
    val toBack: () -> Unit,
    val toEdit: (cardId: String, storeId: String) -> Unit,
)

/** After a delete, back leaves the card only while [card], its own entry, is current (#65). */
fun NavController.detailDirections(card: NavBackStackEntry) = DetailDirections(
    toBack = { whileCurrent(card) { navigateUp() } },
    toEdit = { cardId, storeId -> navigate(CardForm(storeId, cardId = cardId)) },
)


/** First step of adding a card (#6): which store it is for. */
@Serializable
object StorePicker

@Immutable
data class StorePickerDirections(
    val toStore: (storeId: String) -> Unit,
    val toCustomStore: () -> Unit,
    /** Edits the custom store [storeId] (#53). */
    val toEditStore: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

fun NavController.storePickerDirections(picker: NavBackStackEntry) = StorePickerDirections(
    toStore = { storeId -> navigate(Scanner(storeId)) },
    toCustomStore = { navigate(CustomStore()) },
    toEditStore = { storeId -> navigate(CustomStore(storeId)) },
    toBack = { whileCurrent(picker) { navigateUp() } }
)


/** "Other card": a store that isn't listed, created before its card is added. With [storeId], edits that custom store (#53). */
@Serializable
data class CustomStore(val storeId: String? = null)

@Immutable
data class CustomStoreDirections(
    val toScanner: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

/**
 * The created store's scanner replaces the form, so back returns to the picker, where the new store is listed. Only
 * while [form], the form's own entry, is current: a save or delete that ends after Back leaves the user there (#65).
 */
fun NavController.customStoreDirections(form: NavBackStackEntry) = CustomStoreDirections(
    toScanner = { storeId ->
        whileCurrent(form) {
            navigate(Scanner(storeId)) {
                popUpTo<CustomStore> { inclusive = true }
            }
        }
    },
    toBack = { whileCurrent(form) { navigateUp() } }
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
fun NavController.scannerDirections(storeId: String, scanner: NavBackStackEntry) = ScannerDirections(
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
    toBack = { whileCurrent(scanner) { navigateUp() } }
)


/**
 * Last step of adding a card for the store [storeId]: label and number. [scannedContent] and [scannedFormat] are set
 * when the number comes from a scan. With [cardId], the form edits that card of [storeId] instead (#8).
 */
@Serializable
data class CardForm(
    val storeId: String,
    val scannedContent: String? = null,
    val scannedFormat: BarcodeFormat? = null,
    val cardId: String? = null,
)

@Immutable
data class CardFormDirections(
    val toCard: (id: String, color: String) -> Unit,
    val toBack: () -> Unit
)

/**
 * The saved card opens in place of the whole add-card flow, so back from it returns to the list. An edited card opens
 * in place of the card and its form, in its store's current colour (#8). Only while [form], the form's own entry, is
 * current: after Back during the save, the user stays where Back took them, even on a form opened since (#64).
 */
fun NavController.cardFormDirections(form: NavBackStackEntry) = CardFormDirections(
    toCard = { id, color ->
        whileCurrent(form) {
            navigate(Detail(id, color, countUse = false)) {
                popUpTo<CardList>()
            }
        }
    },
    toBack = { whileCurrent(form) { navigateUp() } }
)


/**
 * Navigates only while [entry] is the current screen. A screen still runs its effects and takes taps while it animates
 * out after Back, so one that navigates when a write ends, or a second tap on its Back arrow, would otherwise do it from
 * wherever Back went (#64, #65, #66).
 */
private inline fun NavController.whileCurrent(entry: NavBackStackEntry, navigation: () -> Unit) {
    if (currentBackStackEntry == entry) navigation()
}
