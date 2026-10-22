package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [StoreRepository]. Set [stores] to drive what observers see. */
class FakeStoreRepository(stores: List<Store> = emptyList()) : StoreRepository {

    val stores = MutableStateFlow(stores)

    /** When set, [add] throws it instead of saving, as Room does on a failed insert. */
    var addFailure: Exception? = null

    /** When set, [add] suspends until it completes, as a Room insert in progress does. */
    var addGate: CompletableDeferred<Unit>? = null

    override fun getAll(): Flow<List<Store>> = stores

    override suspend fun add(store: Store) {
        addGate?.await()
        addFailure?.let { throw it }
        stores.value += store
    }
}
