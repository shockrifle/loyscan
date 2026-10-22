package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.StoreDao
import com.bdaniel.loyscan.data.mapper.toDomainModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomStoreRepository @Inject constructor(private val storeDao: StoreDao) : StoreRepository {

    override fun getAll() = storeDao.getAll().map { stores -> stores.map { it.toDomainModel() } }
}
