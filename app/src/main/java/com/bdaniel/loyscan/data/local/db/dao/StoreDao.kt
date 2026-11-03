package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface StoreDao {
    @Insert
    suspend fun insert(store: StoreEntity)

    @Update
    suspend fun update(store: StoreEntity)

    @Delete
    suspend fun delete(store: StoreEntity)

    /** What editing a custom store changes (#53). A catalogue store is left alone: the catalogue owns it. */
    @Query(
        "UPDATE stores SET name = :name, color = :color, defaultBarcodeFormat = :defaultBarcodeFormat, " +
            "updatedAt = :updatedAt WHERE id = :id AND isCustom = 1"
    )
    suspend fun updateCustom(id: String, name: String, color: String, defaultBarcodeFormat: BarcodeFormat, updatedAt: Instant)

    /** Deletes a custom store (#53); the foreign key deletes its cards. A catalogue store is left alone. */
    @Query("DELETE FROM stores WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)

    /** Deletes a custom store, as [deleteCustom] does, and returns the photo names of the cards that went with it (#10). */
    @Transaction
    suspend fun deleteCustomReturningCardPhotos(id: String): List<String> {
        val photos = cardPhotosOfCustomStore(id)
        deleteCustom(id)
        return photos.flatMap { it.names }
    }

    @Query(
        "SELECT frontPhotoPath, backPhotoPath FROM loyalty_cards " +
            "WHERE storeId = :id AND storeId IN (SELECT id FROM stores WHERE isCustom = 1)"
    )
    suspend fun cardPhotosOfCustomStore(id: String): List<CardPhotoNames>

    @Query("SELECT * FROM stores")
    fun getAll(): Flow<List<StoreEntity>>

    // Nullable on purpose: with a non-null type, Room throws when no row matches instead of emitting null.
    @Query("SELECT * FROM stores WHERE id = :id")
    fun getById(id: String): Flow<StoreEntity?>
}
