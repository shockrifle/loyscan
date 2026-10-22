package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [StoreRepository]. Set [stores] to drive what observers see. */
class FakeStoreRepository(stores: List<Store> = emptyList()) : StoreRepository {

    val stores = MutableStateFlow(stores)

    override fun getAll(): Flow<List<Store>> = stores
}
