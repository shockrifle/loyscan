package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Embedded

data class LoyaltyCardWithBrand(
    @Embedded
    val loyaltyCard: LoyaltyCardEntity,
    @Embedded(prefix = "brand_")
    val brand: BrandEntity
)

@Dao
interface LoyaltyCardDao {
    @Insert
    suspend fun insert(loyaltyCard: LoyaltyCardEntity)

    @Update
    suspend fun update(loyaltyCard: LoyaltyCardEntity)

    @Delete
    suspend fun delete(loyaltyCard: LoyaltyCardEntity)

    @Query("SELECT lc.*, b.id as brand_id, b.name as brand_name, b.color as brand_color, b.logo as brand_logo FROM loyalty_cards lc JOIN brands b ON lc.brandId = b.id")
    fun getAllWithBrand(): Flow<List<LoyaltyCardWithBrand>>

    @Query("SELECT lc.*, b.id as brand_id, b.name as brand_name, b.color as brand_color, b.logo as brand_logo FROM loyalty_cards lc JOIN brands b ON lc.brandId = b.id WHERE lc.id = :id")
    fun getByIdWithBrand(id: Int): Flow<LoyaltyCardWithBrand>
}