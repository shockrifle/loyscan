package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** In-memory [LoyaltyCardRepository]. Set [cards] to drive what observers see. */
class FakeLoyaltyCardRepository(
    cards: List<LoyaltyCard> = emptyList(),
    private val now: Instant = SOME_INSTANT,
) : LoyaltyCardRepository {

    val cards = MutableStateFlow(cards)

    override fun getAllWithStore(): Flow<List<LoyaltyCard>> = cards

    override fun getByIdWithStore(id: String): Flow<LoyaltyCard?> =
        cards.map { all -> all.firstOrNull { it.id == id } }

    /** Like the Room repository: counts a use at [now] on the card with this id, if there is one. */
    override suspend fun recordUse(id: String) {
        cards.value = cards.value.map { if (it.id == id) it.copy(useCount = it.useCount + 1, lastUsedAt = now) else it }
    }

    override suspend fun add(card: LoyaltyCard) {
        cards.value += card
    }
}
