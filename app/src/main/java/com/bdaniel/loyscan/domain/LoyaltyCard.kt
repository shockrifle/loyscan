package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import java.time.Instant

/** A user's card for one [store]. The id is a UUID, so cards from imports and restores merge without id clashes. */
data class LoyaltyCard(
    val id: String,
    val store: Store,
    /** The user's own name for the card; `null` when they didn't give one. */
    val label: String?,
    /** The number as the user entered it, or the core number recovered from a scan. */
    val number: String,
    /** The format the card's barcode was scanned in; `null` if the number was typed. */
    val scannedFormat: BarcodeFormat?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val useCount: Int,
    val note: String?,
    val frontPhotoPath: String?,
    val backPhotoPath: String?,
    val updatedAt: Instant,
) {
    /** What the barcode encodes. Derived from [number] by the store's rule, never stored. */
    val barcodeContent: String get() = CardNumberEngine.barcodeContent(number, store.cardNumberRule)

    /** [number] formatted for humans by the store's rule. */
    val displayNumber: String get() = CardNumberEngine.displayNumber(number, store.cardNumberRule)

    /** The symbology to draw: as scanned, else the rule's, else the store's default. */
    val barcodeFormat: BarcodeFormat
        get() = scannedFormat ?: store.cardNumberRule?.format ?: store.defaultBarcodeFormat

    companion object {
        val EMPTY = LoyaltyCard(
            id = "",
            store = Store.EMPTY,
            label = null,
            number = "",
            scannedFormat = null,
            createdAt = Instant.EPOCH,
            lastUsedAt = null,
            useCount = 0,
            note = null,
            frontPhotoPath = null,
            backPhotoPath = null,
            updatedAt = Instant.EPOCH,
        )
    }
}
