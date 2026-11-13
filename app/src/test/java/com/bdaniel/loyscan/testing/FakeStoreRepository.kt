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

    /** When set, [delete] throws it instead of deleting. */
    var deleteFailure: Exception? = null

    /** When set, [delete] suspends until it completes, as a Room delete in progress does. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** How many times [delete] was called. */
    var deleteCalls = 0
        private set

    /** Like the Room repository: removes a custom store; a catalogue store stays. Cards are the card fake's. */
    override suspend fun delete(id: String) {
        deleteCalls++
        deleteGate?.await()
        deleteFailure?.let { throw it }
        stores.value = stores.value.filterNot { it.id == id && it.isCustom }
    }

    /** When set, [setLogo] throws it instead of saving. */
    var setLogoFailure: Exception? = null

    /** When set, [setLogo] suspends until it completes. */
    var setLogoGate: CompletableDeferred<Unit>? = null

    /** Like the Room repository: any store's logo; the files are the photos fake's. */
    override suspend fun setLogo(storeId: String, logo: String?) {
        setLogoGate?.await()
        setLogoFailure?.let { throw it }
        stores.value = stores.value.map { if (it.id == storeId) it.copy(logo = logo) else it }
    }

    /** When set, [update] throws it instead of saving. */
    var updateFailure: Exception? = null

    /** When set, [update] suspends until it completes. */
    var updateGate: CompletableDeferred<Unit>? = null

    /** Like the Room repository: only a custom store's name, colour, format and `updatedAt` change. */
    override suspend fun update(store: Store) {
        updateGate?.await()
        updateFailure?.let { throw it }
        stores.value = stores.value.map {
            if (it.id != store.id || !it.isCustom) it
            else it.copy(
                name = store.name,
                color = store.color,
                defaultBarcodeFormat = store.defaultBarcodeFormat,
                updatedAt = store.updatedAt,
            )
        }
    }
}
