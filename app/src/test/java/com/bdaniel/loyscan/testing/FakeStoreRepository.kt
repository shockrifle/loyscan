package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [StoreRepository]. Set [stores] to drive what observers see. */
class FakeStoreRepository(stores: List<Store> = emptyList()) : StoreRepository {

    val stores = MutableStateFlow(stores)

    override fun getAll(): Flow<List<Store>> = stores

    override fun getById(id: String): Flow<Store?> = stores.map { all -> all.firstOrNull { it.id == id } }
}
