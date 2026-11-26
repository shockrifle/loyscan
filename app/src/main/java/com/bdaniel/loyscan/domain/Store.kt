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
    /**
     * The user's own logo for the store (#151): a file name in the card photos' private storage, never a URL (the app
     * ships no logos). `null` shows the name on the brand colour.
     */
    val logo: String?,
    /** Other names the store is searched by. */
    val aliases: List<String>,
    /** ISO 3166-1 alpha-2 codes of the countries the store operates in. */
    val countryCodes: List<String>,
    /** Used when a card wasn't scanned and there is no [cardNumberRule]. */
    val defaultBarcodeFormat: BarcodeFormat,
    /** How the store's card numbers become barcode content; `null` means the number is the content. */
    val cardNumberRule: CardNumberRule?,
    /** Offered first when adding a card in one of [countryCodes]; only catalogue stores (#6). */
    val frequentlyAdded: Boolean,
    val isCustom: Boolean,
    val updatedAt: Instant,
    /** How [logo] is drawn on [color] (#240); As is until the user picks another. */
    val logoTreatment: LogoTreatment = LogoTreatment.AS_IS,
) {
    /** What an initials tile shows: the first letter or digit of the name's first two words, in upper case. */
    val initials: String
        get() = storeInitials(name)

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
            frequentlyAdded = false,
            isCustom = false,
            updatedAt = Instant.EPOCH,
        )
    }
}

/** A store's [Store.initials] from a [name] (#192): the custom store form's preview has only the name as typed. */
fun storeInitials(name: String): String = name.split(WHITESPACE)
    .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
    .take(2)
    .joinToString("") { it.uppercase() }

private val WHITESPACE = Regex("\\s+")
