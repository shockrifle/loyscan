package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.toRoute
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
    toAddCard = { whileCurrent(list) { navigate(StorePicker()) } },
    toEditCard = { id, storeId -> whileCurrent(list) { navigate(CardForm(storeId, cardId = id)) } },
)


/**
 * One card. [countUse] is false when it opens right after being added or edited, which isn't a use at a till. [added]
 * is true right after adding it: the card offers to add another (#6).
 */
@Serializable
data class Detail(val cardId: String, val color: String, val countUse: Boolean = true, val added: Boolean = false)

@Immutable
data class DetailDirections(
    val toBack: () -> Unit,
    val toEdit: (cardId: String, storeId: String) -> Unit,
    /** "Add another" on a card just added (#6). */
    val toAddCard: () -> Unit,
)

/**
 * Only while [card], its own entry, is current: after a delete (#65), and for a second tap on Edit (#69) or "Add
 * another". Another card is added from the picker over this one, so back from the picker returns to it.
 */
fun NavController.detailDirections(card: NavBackStackEntry) = DetailDirections(
    toBack = { whileCurrent(card) { navigateUp() } },
    toEdit = { cardId, storeId -> whileCurrent(card) { navigate(CardForm(storeId, cardId = cardId)) } },
    toAddCard = { whileCurrent(card) { navigate(StorePicker()) } },
)


/**
 * First step of adding a card (#6): which store it is for. Started from a shared picture (#77), it holds what the
 * picture gave: its barcode, [scannedContent] and [scannedFormat], or [manualEntry] when it gave none and the number is
 * typed by hand. Either way the store's card form follows it, without the scanner.
 */
@Serializable
data class StorePicker(
    val scannedContent: String? = null,
    val scannedFormat: BarcodeFormat? = null,
    val manualEntry: Boolean = false,
)

@Immutable
data class StorePickerDirections(
    val toStore: (storeId: String) -> Unit,
    val toCustomStore: () -> Unit,
    /** Edits the custom store [storeId] (#53). */
    val toEditStore: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

/**
 * A store opens its scanner, or for a shared picture its card form (#77). "Other card" carries the shared picture's
 * barcode on to the new store. Only while [picker], its own entry, is current: a second tap opens nothing (#66, #69).
 */
fun NavController.storePickerDirections(picker: NavBackStackEntry) = StorePickerDirections(
    toStore = { storeId ->
        whileCurrent(picker) {
            val route = picker.toRoute<StorePicker>()
            navigate(addCardStep(storeId, route.scannedContent, route.scannedFormat, route.manualEntry))
        }
    },
    toCustomStore = {
        whileCurrent(picker) {
            val route = picker.toRoute<StorePicker>()
            navigate(CustomStore(scannedContent = route.scannedContent, scannedFormat = route.scannedFormat, manualEntry = route.manualEntry))
        }
    },
    toEditStore = { storeId -> whileCurrent(picker) { navigate(CustomStore(storeId)) } },
    toBack = { whileCurrent(picker) { navigateUp() } }
)


/**
 * "Other card": a store that isn't listed, created before its card is added. With [storeId], edits that custom store
 * (#53). [scannedContent], [scannedFormat] and [manualEntry] are a shared picture's, as on [StorePicker] (#77).
 */
@Serializable
data class CustomStore(
    val storeId: String? = null,
    val scannedContent: String? = null,
    val scannedFormat: BarcodeFormat? = null,
    val manualEntry: Boolean = false,
)

@Immutable
data class CustomStoreDirections(
    val onCreated: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

/**
 * The created store's scanner, or for a shared picture its card form (#77), replaces the form, so back returns to the
 * picker, where the new store is listed. Only while [form], the form's own entry, is current: a save or delete that
 * ends after Back leaves the user there (#65).
 */
fun NavController.customStoreDirections(form: NavBackStackEntry) = CustomStoreDirections(
    onCreated = { storeId ->
        whileCurrent(form) {
            val route = form.toRoute<CustomStore>()
            navigate(addCardStep(storeId, route.scannedContent, route.scannedFormat, route.manualEntry)) {
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
 * The saved card opens in place of the whole add-card flow, so back from it returns to the list; a new card offers to
 * add another (#6). An edited card opens in place of the card and its form, in its store's current colour (#8). Only
 * while [form], the form's own entry, is current: after Back during the save, the user stays where Back took them,
 * even on a form opened since (#64).
 */
fun NavController.cardFormDirections(form: NavBackStackEntry) = CardFormDirections(
    toCard = { id, color ->
        whileCurrent(form) {
            val added = form.toRoute<CardForm>().cardId == null
            navigate(Detail(id, color, countUse = false, added = added)) {
                popUpTo<CardList>()
            }
        }
    },
    toBack = { whileCurrent(form) { navigateUp() } }
)


/**
 * After the store: its scanner, or the card form with a shared picture's barcode ([scannedContent], [scannedFormat])
 * or, with [manualEntry], for a number typed by hand (#77).
 */
private fun addCardStep(storeId: String, scannedContent: String?, scannedFormat: BarcodeFormat?, manualEntry: Boolean): Any =
    when {
        scannedContent != null -> CardForm(storeId, scannedContent = scannedContent, scannedFormat = scannedFormat)
        manualEntry -> CardForm(storeId)
        else -> Scanner(storeId)
    }


/** A picture shared to LoyScan (#77), at the `content://` [uri] granted with the share, read before the store is picked. */
@Serializable
data class SharedPicture(val uri: String)

@Immutable
data class SharedPictureDirections(
    val onScanned: (ScannedBarcode) -> Unit,
    val toManualEntry: () -> Unit,
    val toBack: () -> Unit,
)

/** Starts adding a card from a shared picture (#77) over the list, in place of whatever was open above it. */
fun NavController.openSharedPicture(uri: String) {
    navigate(SharedPicture(uri)) {
        popUpTo<CardList>()
    }
}

/**
 * The store picker replaces the shared picture, holding its barcode, or without one for a number typed by hand, so back
 * from the picker returns to the list. Only while [shared], its own entry, is current: a picture read after Back
 * leaves the user on the list.
 */
fun NavController.sharedPictureDirections(shared: NavBackStackEntry) = SharedPictureDirections(
    onScanned = { barcode ->
        whileCurrent(shared) {
            navigate(StorePicker(scannedContent = barcode.content, scannedFormat = barcode.format)) {
                popUpTo<SharedPicture> { inclusive = true }
            }
        }
    },
    toManualEntry = {
        whileCurrent(shared) {
            navigate(StorePicker(manualEntry = true)) {
                popUpTo<SharedPicture> { inclusive = true }
            }
        }
    },
    toBack = { whileCurrent(shared) { navigateUp() } },
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
