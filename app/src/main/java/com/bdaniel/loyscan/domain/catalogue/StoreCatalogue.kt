package com.bdaniel.loyscan.domain.catalogue

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One store of the curated catalogue (`assets/catalogue/stores.json`). The [id] is a stable slug that stored rows
 * are matched by, so it never changes once shipped. Invalid entries fail at construction (and so at parse time).
 */
@Serializable
data class CatalogueEntry(
    /** Country-prefixed slug, e.g. `hu-ikea-family`: the same chain can have a different card in each country. */
    val id: String,
    val name: String,
    /** Other names the store is searched by. */
    val aliases: List<String> = emptyList(),
    /** ISO 3166-1 alpha-2 codes; the id's country is one of them. */
    val countryCodes: List<String>,
    /** Brand colour as `#RRGGBB`. */
    val color: String,
    val defaultBarcodeFormat: BarcodeFormat,
    /** Its id equals the store's [id]. */
    val cardNumberRule: CardNumberRule? = null,
    /** Offered first when adding a card in one of [countryCodes] (#6): an editorial pick, [[store-catalogue]]. */
    val frequentlyAdded: Boolean = false,
) {
    init {
        require(ID.matches(id)) { "'$id': id must be a country-prefixed slug like hu-ikea-family" }
        require(name.isNotBlank()) { "$id: name must not be blank" }
        require(aliases.none { it.isBlank() }) { "$id: aliases must not be blank" }
        require(countryCodes.isNotEmpty() && countryCodes.all(COUNTRY::matches)) {
            "$id: countryCodes must be upper-case ISO 3166-1 alpha-2 codes"
        }
        require(id.substringBefore('-').uppercase() in countryCodes) { "$id: the id's country is not in countryCodes" }
        require(COLOR.matches(color)) { "$id: color must be #RRGGBB" }
        require(cardNumberRule == null || cardNumberRule.id == id) { "$id: the rule's id must be the store's id" }
    }
}

// Not in a companion: @Serializable classes use theirs for serializer().
private val ID = Regex("[a-z]{2}(-[a-z0-9]+)+")
private val COUNTRY = Regex("[A-Z]{2}")
private val COLOR = Regex("#[0-9A-Fa-f]{6}")

/** Parses the store catalogue. Strict like [com.bdaniel.loyscan.domain.cardnumber.CardNumberRules]. */
object StoreCatalogue {

    /** Parses a JSON array of entries. Throws on malformed JSON, unknown keys, invalid entries or duplicate ids. */
    fun parse(text: String): List<CatalogueEntry> {
        val entries = Json.decodeFromString<List<CatalogueEntry>>(text)
        val duplicates = entries.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate store ids: $duplicates" }
        return entries
    }
}
