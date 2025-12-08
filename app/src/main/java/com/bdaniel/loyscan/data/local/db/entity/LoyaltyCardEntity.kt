package com.bdaniel.loyscan.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "loyalty_cards",
        foreignKeys = [
            ForeignKey(
                    entity = BrandEntity::class,
                    parentColumns = ["id"],
                    childColumns = ["brandId"],
                    onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [Index("brandId")]
)
data class LoyaltyCardEntity(
        @PrimaryKey(autoGenerate = true)
        val id: Int = 0,
        val name: String,
        val brandId: Int,
        val code: String,
        val barcodeFormat: String
)