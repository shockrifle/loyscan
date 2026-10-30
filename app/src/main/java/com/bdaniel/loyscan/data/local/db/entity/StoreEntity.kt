package com.bdaniel.loyscan.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import java.time.Instant

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val color: String,
    val logo: String?,
    val aliases: List<String>,
    val countryCodes: List<String>,
    val defaultBarcodeFormat: BarcodeFormat,
    val cardNumberRule: CardNumberRule?,
    val frequentlyAdded: Boolean,
    val isCustom: Boolean,
    val updatedAt: Instant,
)
