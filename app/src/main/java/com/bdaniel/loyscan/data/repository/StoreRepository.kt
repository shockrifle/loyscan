package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.Flow

/** Stores as domain models. ViewModels depend on this; tests swap in a fake. */
interface StoreRepository {

    fun getAll(): Flow<List<Store>>

    /** Saves a new store, e.g. a custom one the user created (#6). */
    suspend fun add(store: Store)
}
