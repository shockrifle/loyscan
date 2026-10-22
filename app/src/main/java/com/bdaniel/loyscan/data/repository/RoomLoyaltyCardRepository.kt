package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.data.mapper.toEntity
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLoyaltyCardRepository @Inject constructor(
    private val loyaltyCardDao: LoyaltyCardDao,
    private val clock: Clock,
) : LoyaltyCardRepository {

    override fun getAllWithStore() = loyaltyCardDao.getAllWithStore().map { it.toDomainModel() }

    override fun getByIdWithStore(id: String) = loyaltyCardDao.getByIdWithStore(id).map { it?.toDomainModel() }

    override suspend fun recordUse(id: String) = loyaltyCardDao.recordUse(id, clock.instant())

    override suspend fun add(card: LoyaltyCard) = loyaltyCardDao.insert(card.toEntity())
}
