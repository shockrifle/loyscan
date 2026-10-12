package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import java.time.Instant

/** A user's card for one [store]. The id is a UUID, so cards from imports and restores merge without id clashes. */
data class LoyaltyCard(
    val id: String,
    val store: Store,
    /** The user's own name for the card; `null` when they didn't give one. */
    val label: String?,
    /** The raw input: the number as the user typed it, or the barcode content exactly as scanned. */
    val number: String,
    /** The format the card's barcode was scanned in; `null` if the number was typed. Marks [number] as a scan. */
    val scannedFormat: BarcodeFormat?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val useCount: Int,
    val note: String?,
    val frontPhotoPath: String?,
    val backPhotoPath: String?,
    val updatedAt: Instant,
) {
    /**
     * What the barcode encodes. A scan is drawn as scanned, so a later rule change can't alter it; a typed number
     * is turned into barcode content by the store's rule.
     */
    val barcodeContent: String
        get() = if (scannedFormat != null) number else CardNumberEngine.barcodeContent(number, store.cardNumberRule)

    /**
     * The number formatted for humans by the store's rule. For a scan, the core number the rule recovers from it;
     * the scanned content itself when the rule doesn't match.
     */
    val displayNumber: String
        get() {
            val rule = store.cardNumberRule
            val core = if (scannedFormat != null && rule != null) {
                CardNumberEngine.match(number, scannedFormat, rule)?.core ?: return number
            } else {
                number
            }
            return CardNumberEngine.displayNumber(core, rule)
        }

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
