package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A card with its store. Room loads [store] with a second query, so no store column is aliased by hand. */
data class LoyaltyCardWithStore(
    @Embedded
    val loyaltyCard: LoyaltyCardEntity,
    @Relation(parentColumn = "storeId", entityColumn = "id")
    val store: StoreEntity
)

/** The columns an edit of a card writes (#8, the note #9, the photos #10 and whether the list #184 and the widget #185 show one); the rest of the row stays as stored. */
data class LoyaltyCardEdit(
    val id: String,
    val storeId: String,
    val label: String?,
    val number: String,
    val scannedFormat: BarcodeFormat?,
    val barcodeFormat: BarcodeFormat,
    val note: String?,
    val frontPhotoPath: String?,
    val backPhotoPath: String?,
    val showPhotoOnList: Boolean,
    val showPhotoOnWidget: Boolean,
    val updatedAt: Instant,
)

/** A card's photo names, as stored (#10). */
data class CardPhotoNames(val frontPhotoPath: String?, val backPhotoPath: String?) {
    val names: List<String> get() = listOfNotNull(frontPhotoPath, backPhotoPath)
}

@Dao
interface LoyaltyCardDao {
    @Insert
    suspend fun insert(loyaltyCard: LoyaltyCardEntity)

    @Update(entity = LoyaltyCardEntity::class)
    suspend fun update(edit: LoyaltyCardEdit)

    /** Saves [edit] and returns the names of the photos it replaced or removed, whose files can go (#10). */
    @Transaction
    suspend fun updateReturningDroppedPhotos(edit: LoyaltyCardEdit): List<String> {
        val before = photosOf(setOf(edit.id))
        update(edit)
        val kept = setOfNotNull(edit.frontPhotoPath, edit.backPhotoPath)
        return before.flatMap { it.names }.filterNot { it in kept }
    }

    @Query("SELECT frontPhotoPath, backPhotoPath FROM loyalty_cards WHERE id IN (:ids)")
    suspend fun photosOf(ids: Set<String>): List<CardPhotoNames>

    // One bound variable per id: SQLite on API 29 allows 999, far more cards than a phone holds.
    @Query("DELETE FROM loyalty_cards WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Set<String>)

    /** Deletes these cards and returns their photo names, whose files can go (#10). */
    @Transaction
    suspend fun deleteReturningPhotos(ids: Set<String>): List<String> {
        val photos = photosOf(ids)
        deleteByIds(ids)
        return photos.flatMap { it.names }
    }

    @Query("SELECT COUNT(*) FROM loyalty_cards WHERE storeId = :storeId")
    fun countForStore(storeId: String): Flow<Int>

    // Usage isn't an edit of the card, so updatedAt stays as it is.
    @Query("UPDATE loyalty_cards SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    suspend fun recordUse(id: String, at: Instant)

    @Transaction
    @Query("SELECT * FROM loyalty_cards")
    fun getAllWithStore(): Flow<List<LoyaltyCardWithStore>>

    // Nullable on purpose: with a non-null type, Room throws when no row matches instead of emitting null.
    @Transaction
    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    fun getByIdWithStore(id: String): Flow<LoyaltyCardWithStore?>
}
