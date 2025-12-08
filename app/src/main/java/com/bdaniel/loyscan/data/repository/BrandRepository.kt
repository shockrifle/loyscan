package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.BrandDao
import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrandRepository @Inject constructor(private val brandDao: BrandDao) {

    fun getAll() = brandDao.getAll()

    suspend fun insert(brand: BrandEntity) {
        brandDao.insert(brand)
    }
}