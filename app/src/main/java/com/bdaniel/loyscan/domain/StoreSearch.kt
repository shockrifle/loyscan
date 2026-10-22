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

/**
 * The stores whose card-number rule can have produced the scanned [content] ([CardNumberEngine.match]): those whose
 * rule names the scan's symbology first (with or without the GS1 mark), then by name. A store without a rule is left
 * out, since it would accept any scan.
 */
fun List<Store>.matchingScan(content: String, format: BarcodeFormat): List<Store> =
    mapNotNull { store ->
        store.cardNumberRule?.let { CardNumberEngine.match(content, format, it) }?.let { store to it.formatMatches }
    }
        .sortedWith(compareBy<Pair<Store, Boolean>> { !it.second }.thenBy(BY_NAME) { it.first })
        .map { it.first }

private val BY_NAME = compareBy<Store>({ it.name.searchKey() }, { it.name })

private val COMBINING_MARKS = Regex("\\p{Mn}+")

private fun String.searchKey(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
