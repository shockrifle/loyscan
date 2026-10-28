package com.bdaniel.loyscan

import androidx.compose.runtime.Immutable
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import kotlinx.serialization.Serializable

@Serializable
object CardList

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
    val toBack: () -> Unit,
    val toEdit: (cardId: String, storeId: String) -> Unit,
)

fun NavController.detailDirections() = DetailDirections(
    toBack = { navigateUp() },
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

fun NavController.storePickerDirections() = StorePickerDirections(
    toStore = { storeId -> navigate(Scanner(storeId)) },
    toCustomStore = { navigate(CustomStore()) },
    toEditStore = { storeId -> navigate(CustomStore(storeId)) },
    toBack = { navigateUp() }
)


/** "Other card": a store that isn't listed, created before its card is added. With [storeId], edits that custom store (#53). */
@Serializable
data class CustomStore(val storeId: String? = null)

@Immutable
data class CustomStoreDirections(
    val toScanner: (storeId: String) -> Unit,
    val toBack: () -> Unit
)

/** The created store's scanner replaces the form, so back returns to the picker, where the new store is listed. */
fun NavController.customStoreDirections() = CustomStoreDirections(
    toScanner = { storeId ->
        navigate(Scanner(storeId)) {
            popUpTo<CustomStore> { inclusive = true }
        }
    },
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
 * in place of the card and its form, in its store's current colour (#8). Only from the form: after Back during the
 * save, the user stays where Back took them (#64).
 */
fun NavController.cardFormDirections() = CardFormDirections(
    toCard = { id, color ->
        if (currentBackStackEntry?.destination?.hasRoute<CardForm>() == true) {
            navigate(Detail(id, color, countUse = false)) {
                popUpTo<CardList>()
            }
        }
    },
    toBack = { navigateUp() }
)
