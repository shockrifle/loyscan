package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.mapper.toDomainModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLoyaltyCardRepository @Inject constructor(
    private val loyaltyCardDao: LoyaltyCardDao
) : LoyaltyCardRepository {

    override fun getAllWithBrand() = loyaltyCardDao.getAllWithBrand().map { it.toDomainModel() }

    override fun getByIdWithBrand(id: Int) = loyaltyCardDao.getByIdWithBrand(id).map { it.toDomainModel() }
}
