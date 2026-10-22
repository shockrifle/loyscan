package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithStore
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.Store

fun List<LoyaltyCardWithStore>.toDomainModel(): List<LoyaltyCard> {
    return map { it.toDomainModel() }
}

fun LoyaltyCardWithStore.toDomainModel(): LoyaltyCard {
    return LoyaltyCard(
        id = loyaltyCard.id,
        store = store.toDomainModel(),
        label = loyaltyCard.label,
        number = loyaltyCard.number,
        scannedFormat = loyaltyCard.scannedFormat,
        createdAt = loyaltyCard.createdAt,
        lastUsedAt = loyaltyCard.lastUsedAt,
        useCount = loyaltyCard.useCount,
        note = loyaltyCard.note,
        frontPhotoPath = loyaltyCard.frontPhotoPath,
        backPhotoPath = loyaltyCard.backPhotoPath,
        updatedAt = loyaltyCard.updatedAt
    )
}

/** The card's row; its store is referenced by id only. */
fun LoyaltyCard.toEntity(): LoyaltyCardEntity {
    return LoyaltyCardEntity(
        id = id,
        storeId = store.id,
        label = label,
        number = number,
        scannedFormat = scannedFormat,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        useCount = useCount,
        note = note,
        frontPhotoPath = frontPhotoPath,
        backPhotoPath = backPhotoPath,
        updatedAt = updatedAt
    )
}

fun StoreEntity.toDomainModel(): Store {
    return Store(
        id = id,
        name = name,
        color = color,
        logo = logo,
        aliases = aliases,
        countryCodes = countryCodes,
        defaultBarcodeFormat = defaultBarcodeFormat,
        cardNumberRule = cardNumberRule,
        isCustom = isCustom,
        updatedAt = updatedAt
    )
}

/** The store's row. */
fun Store.toEntity(): StoreEntity {
    return StoreEntity(
        id = id,
        name = name,
        color = color,
        logo = logo,
        aliases = aliases,
        countryCodes = countryCodes,
        defaultBarcodeFormat = defaultBarcodeFormat,
        cardNumberRule = cardNumberRule,
        isCustom = isCustom,
        updatedAt = updatedAt
    )
}
