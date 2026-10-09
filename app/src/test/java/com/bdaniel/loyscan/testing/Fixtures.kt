package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.domain.Brand
import com.bdaniel.loyscan.domain.LoyaltyCard

// Fixture builders: every field has a valid default, so a test names only the fields it is about.
// Example data only — no real stores.

fun aBrand(
    id: Int = 1,
    name: String = "Test Store",
    color: String = "#336699",
    logo: String = "",
) = Brand(id = id, name = name, color = color, logo = logo)

fun aLoyaltyCard(
    id: Int = 1,
    name: String = "My card",
    brand: Brand = aBrand(),
    code: String = "1234567890",
    barcodeFormat: String = "CODE_128",
) = LoyaltyCard(id = id, name = name, brand = brand, code = code, barcodeFormat = barcodeFormat)

fun aBrandEntity(
    id: Int = 1,
    name: String = "Test Store",
    color: String = "#336699",
    logo: String = "",
) = BrandEntity(id = id, name = name, color = color, logo = logo)

fun aLoyaltyCardEntity(
    id: Int = 1,
    name: String = "My card",
    brandId: Int = 1,
    code: String = "1234567890",
    barcodeFormat: String = "CODE_128",
) = LoyaltyCardEntity(id = id, name = name, brandId = brandId, code = code, barcodeFormat = barcodeFormat)
