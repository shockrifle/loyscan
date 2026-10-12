package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import java.time.Instant

/**
 * The issuer of a loyalty card: a catalogue store, or one the user created ([isCustom]). Catalogue stores have a
 * stable slug [id]; custom stores have a UUID, so imports and restores can be merged without id clashes.
 */
data class Store(
    val id: String,
    val name: String,
    /** Brand colour as `#RRGGBB`; parsed at render time with a fallback. */
    val color: String,
    /** Logo URL; `null` means an initials tile. */
    val logo: String?,
    /** Other names the store is searched by. */
    val aliases: List<String>,
    /** ISO 3166-1 alpha-2 codes of the countries the store operates in. */
    val countryCodes: List<String>,
    /** Used when a card wasn't scanned and there is no [cardNumberRule]. */
    val defaultBarcodeFormat: BarcodeFormat,
    /** How the store's card numbers become barcode content; `null` means the number is the content. */
    val cardNumberRule: CardNumberRule?,
    val isCustom: Boolean,
    val updatedAt: Instant,
) {
    companion object {
        val EMPTY = Store(
            id = "",
            name = "",
            color = "",
            logo = null,
            aliases = emptyList(),
            countryCodes = emptyList(),
            defaultBarcodeFormat = BarcodeFormat.CODE_128,
            cardNumberRule = null,
            isCustom = false,
            updatedAt = Instant.EPOCH,
        )
    }
}
