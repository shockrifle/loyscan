package com.bdaniel.loyscan.data.local.db.entity

import androidx.room.ColumnInfo
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
        /**
         * The symbology the card is drawn in (#187): a scan's [scannedFormat], else the type the typed number was saved
         * with. Schema v6; the migration wrote each older card's type as it was then drawn.
         */
        val barcodeFormat: BarcodeFormat,
        val createdAt: Instant,
        val lastUsedAt: Instant?,
        val useCount: Int,
        val note: String?,
        val frontPhotoPath: String?,
        val backPhotoPath: String?,
        /** Whether the front photo is the card's tile on My cards (#184); off for a card from before it (schema v3). */
        @ColumnInfo(defaultValue = "0")
        val showPhotoOnList: Boolean,
        /** Whether the front photo is the card's tile on the home-screen widget (#185); off for a card from before it (schema v4). */
        @ColumnInfo(defaultValue = "0")
        val showPhotoOnWidget: Boolean,
        val updatedAt: Instant,
)
