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
}
