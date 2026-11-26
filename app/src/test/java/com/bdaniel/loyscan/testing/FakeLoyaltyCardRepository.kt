package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * In-memory [LoyaltyCardRepository]. Set [cards] to drive what observers see. With [photos], [update] deletes the photos
 * the edit drops from it, as the Room repository does (#141); without, photo files are left alone.
 */
class FakeLoyaltyCardRepository(
    cards: List<LoyaltyCard> = emptyList(),
    private val now: Instant = SOME_INSTANT,
    private val photos: FakeCardPhotos? = null,
) : LoyaltyCardRepository {

    val cards = MutableStateFlow(cards)

    /** When set, [add] throws it instead of saving, as Room does on a failed insert. */
    var addFailure: Exception? = null

    /** When set, [update] throws it instead of saving. */
    var updateFailure: Exception? = null

    /** When set, [delete] and [deleteAll] throw it instead of deleting. */
    var deleteFailure: Exception? = null

    /** When set, [add] suspends until it completes, as a Room insert in progress does. */
    var addGate: CompletableDeferred<Unit>? = null

    /** When set, [update] suspends until it completes. */
    var updateGate: CompletableDeferred<Unit>? = null

    /** When set, [delete] and [deleteAll] suspend until it completes, as a Room delete in progress does. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** When set, [getAllWithStore] emits nothing until it completes, as Room before its first query returns. */
    var loadGate: CompletableDeferred<Unit>? = null

    /** How many times [delete] or [deleteAll] was called. */
    var deleteCalls = 0
        private set

    override fun getAllWithStore(): Flow<List<LoyaltyCard>> = flow {
        loadGate?.await()
        emitAll(cards)
    }

    override fun getByIdWithStore(id: String): Flow<LoyaltyCard?> =
        cards.map { all -> all.firstOrNull { it.id == id } }

    /** Like the Room repository: counts a use at [now] on the card with this id, if there is one. */
    override suspend fun recordUse(id: String) {
        cards.value = cards.value.map { if (it.id == id) it.copy(useCount = it.useCount + 1, lastUsedAt = now) else it }
    }

    override suspend fun add(card: LoyaltyCard) {
        addGate?.await()
        addFailure?.let { throw it }
        cards.value += card
    }

    /**
     * Like the Room repository: only the store, label, number, scanned format, note, photos and `updatedAt` change, and
     * a photo the edit replaces or removes is deleted from [photos].
     */
    override suspend fun update(card: LoyaltyCard) {
        updateGate?.await()
        updateFailure?.let { throw it }
        val stored = cards.value.firstOrNull { it.id == card.id }
        if (stored != null) {
            val kept = setOf(card.frontPhotoPath, card.backPhotoPath)
            photos?.delete(listOfNotNull(stored.frontPhotoPath, stored.backPhotoPath).filterNot { it in kept })
        }
        cards.value = cards.value.map {
            if (it.id != card.id) it
            else it.copy(
                store = card.store,
                label = card.label,
                number = card.number,
                scannedFormat = card.scannedFormat,
                note = card.note,
                frontPhotoPath = card.frontPhotoPath,
                backPhotoPath = card.backPhotoPath,
                showPhotoOnList = card.showPhotoOnList,
                updatedAt = card.updatedAt,
            )
        }
    }

    override fun countForStore(storeId: String): Flow<Int> = cards.map { all -> all.count { it.store.id == storeId } }

    override suspend fun delete(id: String) {
        deleteCalls++
        deleteGate?.await()
        deleteFailure?.let { throw it }
        cards.value = cards.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll(ids: Set<String>) {
        deleteCalls++
        deleteGate?.await()
        deleteFailure?.let { throw it }
        cards.value = cards.value.filterNot { it.id in ids }
    }
}
