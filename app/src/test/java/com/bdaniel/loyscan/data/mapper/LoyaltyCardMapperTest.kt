package com.bdaniel.loyscan.data.mapper

import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithBrand
import com.bdaniel.loyscan.domain.Brand
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.aBrandEntity
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoyaltyCardMapperTest {

    private val withBrand = LoyaltyCardWithBrand(
        loyaltyCard = aLoyaltyCardEntity(id = 3, name = "Gold card", brandId = 7, code = "0042", barcodeFormat = "EAN_13"),
        brand = aBrandEntity(id = 7, name = "Other Store", color = "#112233", logo = "https://example.com/logo.svg"),
    )

    @Test
    fun toDomainModel_copiesEveryField() {
        assertThat(withBrand.toDomainModel()).isEqualTo(
            LoyaltyCard(
                id = 3,
                name = "Gold card",
                brand = Brand(id = 7, name = "Other Store", color = "#112233", logo = "https://example.com/logo.svg"),
                code = "0042",
                barcodeFormat = "EAN_13",
            )
        )
    }

    @Test
    fun listToDomainModel_mapsEachElementInOrder() {
        val other = withBrand.copy(loyaltyCard = withBrand.loyaltyCard.copy(id = 4))

        assertThat(listOf(withBrand, other).toDomainModel().map { it.id }).containsExactly(3, 4).inOrder()
    }
}
