package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.Flow

/** Stores as domain models. ViewModels depend on this; tests swap in a fake. */
interface StoreRepository {

    fun getAll(): Flow<List<Store>>

    /** Saves a new store, e.g. a custom one the user created (#6). */
    suspend fun add(store: Store)

    /**
     * Saves an edit of a custom store (#53): its name, colour, barcode format for typed numbers and `updatedAt`. Does
     * nothing for a catalogue store or an unknown id.
     */
    suspend fun update(store: Store)

    /** Deletes a custom store and its cards with their photos (#53, #10). Does nothing for a catalogue store or an unknown id. */
    suspend fun delete(id: String)
}
