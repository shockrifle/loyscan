package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.StoreDao
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(private val storeDao: StoreDao) {

    fun getAll() = storeDao.getAll()

    suspend fun insert(store: StoreEntity) {
        storeDao.insert(store)
    }
}
