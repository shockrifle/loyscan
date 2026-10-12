package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Insert
    suspend fun insert(store: StoreEntity)

    @Update
    suspend fun update(store: StoreEntity)

    @Delete
    suspend fun delete(store: StoreEntity)

    @Query("SELECT * FROM stores")
    fun getAll(): Flow<List<StoreEntity>>

    // Nullable on purpose: with a non-null type, Room throws when no row matches instead of emitting null.
    @Query("SELECT * FROM stores WHERE id = :id")
    fun getById(id: String): Flow<StoreEntity?>
}
