package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LogoStyle
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

    /**
     * Deletes a custom store, as [deleteCustom] does, and returns the photo names of the cards that went with it (#10)
     * and its logo's (#151).
     */
    @Transaction
    suspend fun deleteCustomReturningCardPhotos(id: String): List<String> {
        val photos = cardPhotosOfCustomStore(id)
        val logo = logoOfCustom(id)
        deleteCustom(id)
        return photos.flatMap { it.names } + listOfNotNull(logo)
    }

    @Query(
        "SELECT frontPhotoPath, backPhotoPath FROM loyalty_cards " +
            "WHERE storeId = :id AND storeId IN (SELECT id FROM stores WHERE isCustom = 1)"
    )
    suspend fun cardPhotosOfCustomStore(id: String): List<CardPhotoNames>

    /**
     * Sets the store's logo (#151) and returns the logo files nothing names any more. A new logo, or none, is drawn As is
     * (#240): how the old one was drawn was chosen for it.
     */
    @Transaction
    suspend fun setLogoReturningDropped(id: String, logo: String?): List<String> {
        val old = logoOf(id)
        if (updateLogo(id, logo) == 0) return listOfNotNull(logo)
        if (old == logo) return emptyList()
        updateLogoTreatment(id, style = null, tint = null, outline = null)
        return listOfNotNull(old)
    }

    @Query("UPDATE stores SET logo = :logo WHERE id = :id")
    suspend fun updateLogo(id: String, logo: String?): Int

    /** How the store's logo is drawn (#240); returns the rows changed, 0 for an unknown id. */
    @Query("UPDATE stores SET logoStyle = :style, logoTint = :tint, logoOutline = :outline WHERE id = :id")
    suspend fun updateLogoTreatment(id: String, style: LogoStyle?, tint: String?, outline: Boolean?): Int

    @Query("SELECT logo FROM stores WHERE id = :id")
    suspend fun logoOf(id: String): String?

    @Query("SELECT logo FROM stores WHERE id = :id AND isCustom = 1")
    suspend fun logoOfCustom(id: String): String?

    @Query("SELECT * FROM stores")
    fun getAll(): Flow<List<StoreEntity>>

    // Nullable on purpose: with a non-null type, Room throws when no row matches instead of emitting null.
    @Query("SELECT * FROM stores WHERE id = :id")
    fun getById(id: String): Flow<StoreEntity?>
}
