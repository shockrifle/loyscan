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
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A card with its store. Room loads [store] with a second query, so no store column is aliased by hand. */
data class LoyaltyCardWithStore(
    @Embedded
    val loyaltyCard: LoyaltyCardEntity,
    @Relation(parentColumn = "storeId", entityColumn = "id")
    val store: StoreEntity
)

@Dao
interface LoyaltyCardDao {
    @Insert
    suspend fun insert(loyaltyCard: LoyaltyCardEntity)

    @Update
    suspend fun update(loyaltyCard: LoyaltyCardEntity)

    @Query("DELETE FROM loyalty_cards WHERE id = :id")
    suspend fun deleteById(id: String)

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
