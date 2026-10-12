package com.bdaniel.loyscan.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bdaniel.loyscan.domain.BarcodeFormat
import java.time.Instant

@Entity(
        tableName = "loyalty_cards",
        foreignKeys = [
            ForeignKey(
                    entity = StoreEntity::class,
                    parentColumns = ["id"],
                    childColumns = ["storeId"],
                    onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [Index("storeId")]
)
data class LoyaltyCardEntity(
        @PrimaryKey
        val id: String,
        val storeId: String,
        val label: String?,
        val number: String,
        val scannedFormat: BarcodeFormat?,
        val createdAt: Instant,
        val lastUsedAt: Instant?,
        val useCount: Int,
        val note: String?,
        val frontPhotoPath: String?,
        val backPhotoPath: String?,
        val updatedAt: Instant,
)
