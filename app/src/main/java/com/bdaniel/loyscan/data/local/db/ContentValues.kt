package com.bdaniel.loyscan.data.local.db

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity

// Rows for the raw SupportSQLiteDatabase, which Room callbacks get instead of DAOs. Columns are converted exactly as
// Room does it ([Converters]; Boolean as 0/1, enums by name), so the DAOs read them back unchanged (ContentValuesTest).

private val converters = Converters()

fun StoreEntity.toContentValues(): ContentValues = contentValuesOf(
    "id" to id,
    "name" to name,
    "color" to color,
    "logo" to logo,
    "aliases" to converters.fromStringList(aliases),
    "countryCodes" to converters.fromStringList(countryCodes),
    "defaultBarcodeFormat" to defaultBarcodeFormat.name,
    "cardNumberRule" to converters.fromCardNumberRule(cardNumberRule),
    "frequentlyAdded" to if (frequentlyAdded) 1 else 0,
    "isCustom" to if (isCustom) 1 else 0,
    "updatedAt" to converters.fromInstant(updatedAt),
    "logoStyle" to logoStyle?.name,
    "logoTint" to logoTint,
    "logoOutline" to logoOutline?.let { if (it) 1 else 0 },
)

fun LoyaltyCardEntity.toContentValues(): ContentValues = contentValuesOf(
    "id" to id,
    "storeId" to storeId,
    "label" to label,
    "number" to number,
    "scannedFormat" to scannedFormat?.name,
    "barcodeFormat" to barcodeFormat.name,
    "createdAt" to converters.fromInstant(createdAt),
    "lastUsedAt" to converters.fromInstant(lastUsedAt),
    "useCount" to useCount,
    "note" to note,
    "frontPhotoPath" to frontPhotoPath,
    "backPhotoPath" to backPhotoPath,
    "showPhotoOnList" to if (showPhotoOnList) 1 else 0,
    "showPhotoOnWidget" to if (showPhotoOnWidget) 1 else 0,
    "updatedAt" to converters.fromInstant(updatedAt),
)
