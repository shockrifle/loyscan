package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.Flow

/** Cards with their store, as domain models. ViewModels depend on this; tests swap in a fake. */
interface LoyaltyCardRepository {

    fun getAllWithStore(): Flow<List<LoyaltyCard>>

    /** Emits `null` while no card has this id (never existed, or deleted). */
    fun getByIdWithStore(id: String): Flow<LoyaltyCard?>

    /** Counts one use of the card, now. Does nothing for an unknown id. */
    suspend fun recordUse(id: String)

    /** Saves a new card. Its store must already exist. */
    suspend fun add(card: LoyaltyCard)

    /**
     * Saves an edit of an existing card (#8): its store, label, number, scanned format, note (#9), photos (#10) and
     * `updatedAt`. Its creation time and uses stay as stored. A photo the edit replaces or removes is deleted. Does
     * nothing for an unknown id.
     */
    suspend fun update(card: LoyaltyCard)

    /** Deletes the card and its photos (#10). Does nothing for an unknown id. */
    suspend fun delete(id: String)

    /** Deletes these cards and their photos in one write (#7: multi-select). Unknown ids are skipped. */
    suspend fun deleteAll(ids: Set<String>)

    /** How many cards the store [storeId] has, updated as cards change (#53). */
    fun countForStore(storeId: String): Flow<Int>
}
