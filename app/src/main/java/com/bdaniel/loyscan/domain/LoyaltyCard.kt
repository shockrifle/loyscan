package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
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
    /**
     * The symbology the card is drawn in (#187): a scan's [scannedFormat], else the type the typed number was saved
     * with. The store's rule makes a typed number's barcode only while this is the rule's format ([ruleApplies]).
     */
    val barcodeFormat: BarcodeFormat,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val useCount: Int,
    val note: String?,
    val frontPhotoPath: String?,
    val backPhotoPath: String?,
    /** The user chose to show [frontPhotoPath] as the card's tile on My cards (#184); off by default. */
    val showPhotoOnList: Boolean,
    /** The user chose to show [frontPhotoPath] as the card's tile on the home-screen widget (#185); off by default. */
    val showPhotoOnWidget: Boolean,
    val updatedAt: Instant,
) {
    /**
     * The store's rule, while it applies to this typed number: the card is in the rule's type (#187). In any other type
     * the number is drawn and shown as typed.
     */
    private val typedRule: CardNumberRule?
        get() = store.cardNumberRule?.takeIf { it.format == barcodeFormat }

    /**
     * What the barcode encodes. A scan is drawn as scanned, so a later rule change can't alter it; a typed number
     * is turned into barcode content by the store's rule while the card is in the rule's type, else used as typed.
     */
    val barcodeContent: String
        get() = if (scannedFormat != null) number else CardNumberEngine.barcodeContent(number, typedRule)

    /**
     * The number formatted for humans by the store's rule. For a scan, the core number the rule recovers from it;
     * without a rule that matches, the scanned content as text ([scannedText]).
     */
    val displayNumber: String
        get() {
            val rule = store.cardNumberRule
            if (scannedFormat == null) return CardNumberEngine.displayNumber(number, typedRule)
            val match = rule?.let { CardNumberEngine.match(number, scannedFormat, it) }
                ?: return scannedText(scannedFormat)
            return CardNumberEngine.displayNumber(match.core, rule)
        }

    /**
     * GS1 data as printed under its barcode, `(01)…(10)…` (#42). Otherwise, or if it doesn't split into GS1 fields,
     * the content with each run of control characters (e.g. the GS that separates GS1 fields) shown as one space.
     */
    private fun scannedText(format: BarcodeFormat): String =
        (if (format.isGs1) Gs1.humanReadable(number) else null)
            ?: number.replace(CONTROL_CHARACTERS, " ").trim()

    companion object {
        private val CONTROL_CHARACTERS = Regex("\\p{Cc}+")

        val EMPTY = LoyaltyCard(
            id = "",
            store = Store.EMPTY,
            label = null,
            number = "",
            scannedFormat = null,
            barcodeFormat = BarcodeFormat.CODE_128,
            createdAt = Instant.EPOCH,
            lastUsedAt = null,
            useCount = 0,
            note = null,
            frontPhotoPath = null,
            backPhotoPath = null,
            showPhotoOnList = false,
            showPhotoOnWidget = false,
            updatedAt = Instant.EPOCH,
        )
    }
}
