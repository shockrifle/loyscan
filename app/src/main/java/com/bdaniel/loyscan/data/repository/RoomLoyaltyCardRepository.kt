package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.data.mapper.toEdit
import com.bdaniel.loyscan.data.mapper.toEntity
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLoyaltyCardRepository @Inject constructor(
    private val loyaltyCardDao: LoyaltyCardDao,
    private val clock: Clock,
    private val photos: CardPhotos,
) : LoyaltyCardRepository {

    override fun getAllWithStore() = loyaltyCardDao.getAllWithStore().map { it.toDomainModel() }

    override fun getByIdWithStore(id: String) = loyaltyCardDao.getByIdWithStore(id).map { it?.toDomainModel() }

    override suspend fun recordUse(id: String) = loyaltyCardDao.recordUse(id, clock.instant())

    override suspend fun add(card: LoyaltyCard) = loyaltyCardDao.insert(card.toEntity())

    // A photo's file goes once its row no longer names it: a failed write leaves the card's photos as they were.
    override suspend fun update(card: LoyaltyCard) = photos.delete(loyaltyCardDao.updateReturningDroppedPhotos(card.toEdit()))

    override suspend fun delete(id: String) = deleteAll(setOf(id))

    override suspend fun deleteAll(ids: Set<String>) = photos.delete(loyaltyCardDao.deleteReturningPhotos(ids))

    override fun countForStore(storeId: String): Flow<Int> = loyaltyCardDao.countForStore(storeId)
}
