package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.catalogue.CatalogueEntry
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class CatalogueMapperTest {

    @Test
    fun toStoreEntity_copiesEveryField_withoutLogo_notCustom() {
        val updatedAt = Instant.parse("2026-10-13T00:00:00Z")
        val entry = CatalogueEntry(
            id = "hu-example",
            name = "Example",
            aliases = listOf("Ex"),
            countryCodes = listOf("HU", "SK"),
            color = "#123456",
            defaultBarcodeFormat = BarcodeFormat.EAN_13,
            cardNumberRule = CardNumberRule(id = "hu-example", format = BarcodeFormat.CODE_39),
            frequentlyAdded = true,
        )

        assertThat(entry.toStoreEntity(updatedAt)).isEqualTo(
            aStoreEntity(
                id = "hu-example",
                name = "Example",
                color = "#123456",
                logo = null,
                aliases = listOf("Ex"),
                countryCodes = listOf("HU", "SK"),
                defaultBarcodeFormat = BarcodeFormat.EAN_13,
                cardNumberRule = CardNumberRule(id = "hu-example", format = BarcodeFormat.CODE_39),
                frequentlyAdded = true,
                isCustom = false,
                updatedAt = updatedAt,
            )
        )
    }
}
