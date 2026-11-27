package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardEdit
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithStore
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.LogoTreatment
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
        barcodeFormat = loyaltyCard.barcodeFormat,
        createdAt = loyaltyCard.createdAt,
        lastUsedAt = loyaltyCard.lastUsedAt,
        useCount = loyaltyCard.useCount,
        note = loyaltyCard.note,
        frontPhotoPath = loyaltyCard.frontPhotoPath,
        backPhotoPath = loyaltyCard.backPhotoPath,
        showPhotoOnList = loyaltyCard.showPhotoOnList,
        showPhotoOnWidget = loyaltyCard.showPhotoOnWidget,
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
        barcodeFormat = barcodeFormat,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        useCount = useCount,
        note = note,
        frontPhotoPath = frontPhotoPath,
        backPhotoPath = backPhotoPath,
        showPhotoOnList = showPhotoOnList,
        showPhotoOnWidget = showPhotoOnWidget,
        updatedAt = updatedAt
    )
}

/** The columns an edit writes. */
fun LoyaltyCard.toEdit(): LoyaltyCardEdit {
    return LoyaltyCardEdit(
        id = id,
        storeId = store.id,
        label = label,
        number = number,
        scannedFormat = scannedFormat,
        barcodeFormat = barcodeFormat,
        note = note,
        frontPhotoPath = frontPhotoPath,
        backPhotoPath = backPhotoPath,
        showPhotoOnList = showPhotoOnList,
        showPhotoOnWidget = showPhotoOnWidget,
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
        frequentlyAdded = frequentlyAdded,
        isCustom = isCustom,
        updatedAt = updatedAt,
        logoTreatment = LogoTreatment(style = logoStyle, tint = logoTint, outline = logoOutline == true),
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
        frequentlyAdded = frequentlyAdded,
        isCustom = isCustom,
        updatedAt = updatedAt,
        logoStyle = logoTreatment.style,
        logoTint = logoTreatment.tint,
        // Null, not false, without an outline: null throughout is As is (#240).
        logoOutline = logoTreatment.outline.takeIf { it },
    )
}
