package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Insert
    suspend fun insert(brand: BrandEntity)

    @Update
    suspend fun update(brand: BrandEntity)

    @Delete
    suspend fun delete(brand: BrandEntity)

    @Query("SELECT * FROM brands")
    fun getAll(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE id = :id")
    fun getById(id: Int): Flow<BrandEntity>
}