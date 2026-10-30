package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import java.text.Normalizer
import java.util.Locale

/**
 * The stores whose name or an alias contains [query], ignoring case and accents (so "mobelix" finds "Möbelix"),
 * sorted by name the same way. A blank query returns every store.
 */
fun List<Store>.search(query: String): List<Store> {
    val key = query.searchKey()
    return filter { store -> key.isEmpty() || (listOf(store.name) + store.aliases).any { key in it.searchKey() } }
        .sortedWith(BY_NAME)
}

/** The stores marked frequently added that operate in [country] (#6), by name; none when the country isn't known. */
fun List<Store>.frequentlyAddedIn(country: String?): List<Store> =
    filter { it.frequentlyAdded && country != null && country in it.countryCodes }.sortedWith(BY_NAME)

/**
 * The stores whose card-number rule can have produced the scanned [content] ([CardNumberEngine.match]): those whose
 * rule names the scan's exact format first, then those with the same symbology with or without the GS1 mark, then
 * the rest, each by name. The exact format ranks first because the GS1 mark is evidence too: a Tesco GS1-128 scan is
 * also 16 digits SuperShop's Code 128 rule reads. A store without a rule is left out, since it would accept any scan.
 */
fun List<Store>.matchingScan(content: String, format: BarcodeFormat): List<Store> =
    mapNotNull { store ->
        val rule = store.cardNumberRule ?: return@mapNotNull null
        val match = CardNumberEngine.match(content, format, rule) ?: return@mapNotNull null
        val tier = when {
            rule.format == format -> 0
            match.formatMatches -> 1
            else -> 2
        }
        store to tier
    }
        .sortedWith(compareBy<Pair<Store, Int>> { it.second }.thenBy(BY_NAME) { it.first })
        .map { it.first }

private val BY_NAME = compareBy<Store>({ it.name.searchKey() }, { it.name })

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** The text as searched and sorted: trimmed, lower case, without accents. Cards are searched the same way. */
internal fun String.searchKey(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
