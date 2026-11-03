package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.StoreDao
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.data.mapper.toEntity
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomStoreRepository @Inject constructor(
    private val storeDao: StoreDao,
    private val photos: CardPhotos,
) : StoreRepository {

    override fun getAll() = storeDao.getAll().map { stores -> stores.map { it.toDomainModel() } }

    override suspend fun add(store: Store) {
        storeDao.insert(store.toEntity())
    }

    override suspend fun update(store: Store) {
        storeDao.updateCustom(store.id, store.name, store.color, store.defaultBarcodeFormat, store.updatedAt)
    }

    override suspend fun delete(id: String) {
        photos.delete(storeDao.deleteCustomReturningCardPhotos(id))
    }
}
