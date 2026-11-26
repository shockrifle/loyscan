package com.bdaniel.loyscan.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LogoStyle
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
    /** How the logo is drawn (#240); these three are the user's, null = As is, reset with the logo. */
    val logoStyle: LogoStyle? = null,
    val logoTint: String? = null,
    val logoOutline: Boolean? = null,
)
