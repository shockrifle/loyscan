package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.CompletableDeferred
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

    /** When set, [add] throws it instead of saving, as Room does on a failed insert. */
    var addFailure: Exception? = null

    /** When set, [update] throws it instead of saving. */
    var updateFailure: Exception? = null

    /** When set, [delete] throws it instead of deleting. */
    var deleteFailure: Exception? = null

    /** When set, [delete] suspends until it completes, as a Room delete in progress does. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** How many times [delete] was called. */
    var deleteCalls = 0
        private set

    override fun getAllWithStore(): Flow<List<LoyaltyCard>> = cards

    override fun getByIdWithStore(id: String): Flow<LoyaltyCard?> =
        cards.map { all -> all.firstOrNull { it.id == id } }

    /** Like the Room repository: counts a use at [now] on the card with this id, if there is one. */
    override suspend fun recordUse(id: String) {
        cards.value = cards.value.map { if (it.id == id) it.copy(useCount = it.useCount + 1, lastUsedAt = now) else it }
    }

    override suspend fun add(card: LoyaltyCard) {
        addFailure?.let { throw it }
        cards.value += card
    }

    /** Like the Room repository: only the store, label, number, scanned format and `updatedAt` change. */
    override suspend fun update(card: LoyaltyCard) {
        updateFailure?.let { throw it }
        cards.value = cards.value.map {
            if (it.id != card.id) it
            else it.copy(
                store = card.store,
                label = card.label,
                number = card.number,
                scannedFormat = card.scannedFormat,
                updatedAt = card.updatedAt,
            )
        }
    }

    override suspend fun delete(id: String) {
        deleteCalls++
        deleteGate?.await()
        deleteFailure?.let { throw it }
        cards.value = cards.value.filterNot { it.id == id }
    }
}
