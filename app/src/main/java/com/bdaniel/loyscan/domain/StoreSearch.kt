package com.bdaniel.loyscan.domain

import java.text.Normalizer
import java.util.Locale

/**
 * The stores whose name or an alias contains [query], ignoring case and accents (so "mobelix" finds "Möbelix"),
 * sorted by name the same way. A blank query returns every store.
 */
fun List<Store>.search(query: String): List<Store> {
    val key = query.searchKey()
    return filter { store -> key.isEmpty() || (listOf(store.name) + store.aliases).any { key in it.searchKey() } }
        .sortedWith(compareBy({ it.name.searchKey() }, { it.name }))
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")

private fun String.searchKey(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
