package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.catalogue.CatalogueEntry
import java.time.Instant

/** The row a catalogue store is stored as. The catalogue ships no logos, so tiles show the name on the colour. */
fun CatalogueEntry.toStoreEntity(updatedAt: Instant) = StoreEntity(
    id = id,
    name = name,
    color = color,
    logo = null,
    aliases = aliases,
    countryCodes = countryCodes,
    defaultBarcodeFormat = defaultBarcodeFormat,
    cardNumberRule = cardNumberRule,
    frequentlyAdded = frequentlyAdded,
    isCustom = false,
    updatedAt = updatedAt,
)
