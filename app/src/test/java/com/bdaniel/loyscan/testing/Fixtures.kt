package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import java.time.Instant

// Fixture builders: every field has a valid default, so a test names only the fields it is about.
// Example data only — no real stores.

const val STORE_ID = "test-store"
const val CARD_ID = "00000000-0000-4000-8000-000000000001"

/** A UUID-shaped card id that differs per [n], for tests that need several cards. */
fun cardId(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

val SOME_INSTANT: Instant = Instant.parse("2026-01-02T03:04:05Z")

fun aStore(
    id: String = STORE_ID,
    name: String = "Test Store",
    color: String = "#336699",
    logo: String? = null,
    aliases: List<String> = emptyList(),
    countryCodes: List<String> = listOf("HU"),
    defaultBarcodeFormat: BarcodeFormat = BarcodeFormat.CODE_128,
    cardNumberRule: CardNumberRule? = null,
    frequentlyAdded: Boolean = false,
    isCustom: Boolean = false,
    updatedAt: Instant = SOME_INSTANT,
    logoTreatment: LogoTreatment = LogoTreatment.AS_IS,
) = Store(
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
    logoTreatment = logoTreatment,
)

fun aLoyaltyCard(
    id: String = CARD_ID,
    store: Store = aStore(),
    label: String? = null,
    number: String = "1234567890",
    scannedFormat: BarcodeFormat? = null,
    barcodeFormat: BarcodeFormat = scannedFormat ?: store.cardNumberRule?.format ?: store.defaultBarcodeFormat,
    createdAt: Instant = SOME_INSTANT,
    lastUsedAt: Instant? = null,
    useCount: Int = 0,
    note: String? = null,
    frontPhotoPath: String? = null,
    backPhotoPath: String? = null,
    showPhotoOnList: Boolean = false,
    showPhotoOnWidget: Boolean = false,
    updatedAt: Instant = SOME_INSTANT,
) = LoyaltyCard(
    id = id,
    store = store,
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
    updatedAt = updatedAt,
)

fun aStoreEntity(
    id: String = STORE_ID,
    name: String = "Test Store",
    color: String = "#336699",
    logo: String? = null,
    aliases: List<String> = emptyList(),
    countryCodes: List<String> = listOf("HU"),
    defaultBarcodeFormat: BarcodeFormat = BarcodeFormat.CODE_128,
    cardNumberRule: CardNumberRule? = null,
    frequentlyAdded: Boolean = false,
    isCustom: Boolean = false,
    updatedAt: Instant = SOME_INSTANT,
    logoStyle: LogoStyle? = null,
    logoTint: String? = null,
    logoOutline: Boolean? = null,
) = StoreEntity(
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
    logoStyle = logoStyle,
    logoTint = logoTint,
    logoOutline = logoOutline,
)

fun aLoyaltyCardEntity(
    id: String = CARD_ID,
    storeId: String = STORE_ID,
    label: String? = null,
    number: String = "1234567890",
    scannedFormat: BarcodeFormat? = null,
    barcodeFormat: BarcodeFormat = scannedFormat ?: BarcodeFormat.CODE_128,
    createdAt: Instant = SOME_INSTANT,
    lastUsedAt: Instant? = null,
    useCount: Int = 0,
    note: String? = null,
    frontPhotoPath: String? = null,
    backPhotoPath: String? = null,
    showPhotoOnList: Boolean = false,
    showPhotoOnWidget: Boolean = false,
    updatedAt: Instant = SOME_INSTANT,
) = LoyaltyCardEntity(
    id = id,
    storeId = storeId,
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
    updatedAt = updatedAt,
)
