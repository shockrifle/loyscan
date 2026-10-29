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
    /** Opens the card, and says whether it did: not from a list that is no longer current (#69). */
    val toCard: (id: String, color: String) -> Boolean,
    val toAddCard: () -> Unit,
    /** Edits the card [id] of the store [storeId] (#7: one card selected). */
    val toEditCard: (id: String, storeId: String) -> Unit,
)

/**
 * Only while [list], its own entry, is current: a second tap before the next screen covers it opens nothing (#69). An
 * edit opens the card's form over the list; saving it opens the card, as an edit from the card does.
 */
fun NavController.listDirections(list: NavBackStackEntry) = ListDirections(
    toCard = { id, color ->
        whileCurrent(list) { navigate(Detail(id, color)) }
    },
    toAddCard = { whileCurrent(list) { navigate(StorePicker) } },
    toEditCard = { id, storeId -> whileCurrent(list) { navigate(CardForm(storeId, cardId = id)) } },
)


/** One card. [countUse] is false when it opens right after being added, which isn't a use at a till. */
@Serializable
data class Detail(val cardId: String, val color: String, val countUse: Boolean = true)

@Immutable
data class DetailDirections(
    val toBack: () -> Unit,
    val toEdit: (cardId: String, storeId: String) -> Unit,
)

/** Only while [card], its own entry, is current: after a delete (#65), and for a second tap on Edit (#69). */
fun NavController.detailDirections(card: NavBackStackEntry) = DetailDirections(
    toBack = { whileCurrent(card) { navigateUp() } },
    toEdit = { cardId, storeId -> whileCurrent(card) { navigate(CardForm(storeId, cardId = cardId)) } },
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

/** Only while [picker], its own entry, is current: a second tap opens nothing (#66, #69). */
fun NavController.storePickerDirections(picker: NavBackStackEntry) = StorePickerDirections(
    toStore = { storeId -> whileCurrent(picker) { navigate(Scanner(storeId)) } },
    toCustomStore = { whileCurrent(picker) { navigate(CustomStore()) } },
    toEditStore = { storeId -> whileCurrent(picker) { navigate(CustomStore(storeId)) } },
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

/**
 * A scan, or "enter manually", replaces the scanner with the card form, so back from the form returns to the picker.
 * Only while [scanner], its own entry, is current: a frame decoded after Back leaves the user on the picker (#68).
 */
fun NavController.scannerDirections(storeId: String, scanner: NavBackStackEntry) = ScannerDirections(
    onScanned = { barcode ->
        whileCurrent(scanner) {
            navigate(CardForm(storeId, scannedContent = barcode.content, scannedFormat = barcode.format)) {
                popUpTo<Scanner> { inclusive = true }
            }
        }
    },
    toManualEntry = {
        whileCurrent(scanner) {
            navigate(CardForm(storeId)) {
                popUpTo<Scanner> { inclusive = true }
            }
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
 * Navigates only while [entry] is the current screen. Every direction goes through it. A screen still runs its effects
 * and takes taps while it animates out after Back, or until the next screen covers it, so a write that ends, a scan
 * decoded late or a second tap would otherwise navigate from wherever the first navigation went (#64, #65, #66, #68,
 * #69).
 */
private inline fun NavController.whileCurrent(entry: NavBackStackEntry, navigation: () -> Unit): Boolean {
    val current = currentBackStackEntry == entry
    if (current) navigation()
    return current
}
