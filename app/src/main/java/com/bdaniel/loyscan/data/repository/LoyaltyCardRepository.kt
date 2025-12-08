package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.mapper.toDomainModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoyaltyCardRepository @Inject constructor(private val loyaltyCardDao: LoyaltyCardDao) {

    fun getAllWithBrand() = loyaltyCardDao.getAllWithBrand().map { it.toDomainModel() }

    fun getByIdWithBrand(id: Int) = loyaltyCardDao.getByIdWithBrand(id).map { it.toDomainModel() }

    suspend fun insert(loyaltyCard: LoyaltyCardEntity) {
        loyaltyCardDao.insert(loyaltyCard)
    }

    suspend fun delete(loyaltyCard: LoyaltyCardEntity) {
        loyaltyCardDao.delete(loyaltyCard)
    }
}