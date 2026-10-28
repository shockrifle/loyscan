package com.bdaniel.loyscan.domain

import java.time.Instant

/** How the card list is ordered (#7). Ties are broken by [NAME]. */
enum class CardSort {
    /** By store name, then label (an unlabelled card first), ignoring case and accents. */
    NAME,

    /** Most uses first. */
    MOST_USED,

    /** Most recent use first; a card never used comes last. */
    RECENTLY_USED,
}

private val BY_NAME = compareBy<LoyaltyCard>(
    { it.store.name.searchKey() },
    { it.label?.searchKey() ?: "" },
    // Same name and label: a stable order, so tiles don't swap places on every update.
    { it.id },
)

fun List<LoyaltyCard>.sortedBy(sort: CardSort): List<LoyaltyCard> = sortedWith(
    when (sort) {
        CardSort.NAME -> BY_NAME
        CardSort.MOST_USED -> compareByDescending<LoyaltyCard> { it.useCount }.then(BY_NAME)
        CardSort.RECENTLY_USED -> compareBy<LoyaltyCard, Instant?>(nullsLast(reverseOrder())) { it.lastUsedAt }
            .then(BY_NAME)
    }
)

/**
 * The cards whose store name, store alias, label or note contains [query], ignoring case and accents, in the order
 * given. A blank query returns every card. The number isn't searched.
 */
fun List<LoyaltyCard>.search(query: String): List<LoyaltyCard> {
    val key = query.searchKey()
    if (key.isEmpty()) return this
    return filter { card ->
        (listOf(card.store.name) + card.store.aliases + listOfNotNull(card.label, card.note)).any { key in it.searchKey() }
    }
}
