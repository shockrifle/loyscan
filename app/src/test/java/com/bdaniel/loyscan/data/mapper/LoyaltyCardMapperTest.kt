package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithStore
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class LoyaltyCardMapperTest {

    private val rule = CardNumberRule(id = "example", format = BarcodeFormat.CODE_128, minLength = 4, maxLength = 10)

    private val withStore = LoyaltyCardWithStore(
        loyaltyCard = aLoyaltyCardEntity(
            id = cardId(3),
            storeId = "other",
            label = "Gold card",
            number = "0042",
            scannedFormat = BarcodeFormat.EAN_13,
            createdAt = Instant.ofEpochMilli(1),
            lastUsedAt = Instant.ofEpochMilli(2),
            useCount = 7,
            note = "Expires in May",
            frontPhotoPath = "photos/front.jpg",
            backPhotoPath = "photos/back.jpg",
            updatedAt = Instant.ofEpochMilli(3),
        ),
        store = aStoreEntity(
            id = "other",
            name = "Other Store",
            color = "#112233",
            logo = "https://example.com/logo.svg",
            aliases = listOf("Other"),
            countryCodes = listOf("AT"),
            defaultBarcodeFormat = BarcodeFormat.CODE_39,
            cardNumberRule = rule,
            isCustom = true,
            updatedAt = Instant.ofEpochMilli(4),
        ),
    )

    @Test
    fun toDomainModel_copiesEveryField() {
        assertThat(withStore.toDomainModel()).isEqualTo(
            LoyaltyCard(
                id = cardId(3),
                store = Store(
                    id = "other",
                    name = "Other Store",
                    color = "#112233",
                    logo = "https://example.com/logo.svg",
                    aliases = listOf("Other"),
                    countryCodes = listOf("AT"),
                    defaultBarcodeFormat = BarcodeFormat.CODE_39,
                    cardNumberRule = rule,
                    isCustom = true,
                    updatedAt = Instant.ofEpochMilli(4),
                ),
                label = "Gold card",
                number = "0042",
                scannedFormat = BarcodeFormat.EAN_13,
                createdAt = Instant.ofEpochMilli(1),
                lastUsedAt = Instant.ofEpochMilli(2),
                useCount = 7,
                note = "Expires in May",
                frontPhotoPath = "photos/front.jpg",
                backPhotoPath = "photos/back.jpg",
                updatedAt = Instant.ofEpochMilli(3),
            )
        )
    }

    @Test
    fun listToDomainModel_mapsEachElementInOrder() {
        val other = withStore.copy(loyaltyCard = withStore.loyaltyCard.copy(id = cardId(4)))

        assertThat(listOf(withStore, other).toDomainModel().map { it.id }).containsExactly(cardId(3), cardId(4)).inOrder()
    }

    @Test
    fun toEntity_copiesEveryFieldAndKeepsOnlyTheStoreId() {
        assertThat(withStore.toDomainModel().toEntity()).isEqualTo(withStore.loyaltyCard)
    }
}
