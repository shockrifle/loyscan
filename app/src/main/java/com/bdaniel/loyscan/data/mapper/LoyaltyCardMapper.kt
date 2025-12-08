package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithBrand
import com.bdaniel.loyscan.domain.Brand
import com.bdaniel.loyscan.domain.LoyaltyCard

fun List<LoyaltyCardWithBrand>.toDomainModel(): List<LoyaltyCard> {
    return map { it.toDomainModel() }
}

fun LoyaltyCardWithBrand.toDomainModel(): LoyaltyCard {
    return LoyaltyCard(
        id = loyaltyCard.id,
        name = loyaltyCard.name,
        brand = Brand(
            id = brand.id,
            name = brand.name,
            color = brand.color,
            logo = brand.logo
        ),
        code = loyaltyCard.code,
        barcodeFormat = loyaltyCard.barcodeFormat
    )
}