package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.aBrandEntity
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Example of a Room test: real SQLite, in memory, on the JVM via Robolectric. */
@RunWith(AndroidJUnit4::class)
class LoyaltyCardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LoyaltyCardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.loyaltyCardDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAllWithBrand_returnsEachCardJoinedWithItsBrand() = runTest {
        val brand = aBrandEntity(id = 7, name = "Other Store", color = "#112233", logo = "https://example.com/logo.svg")
        val card = aLoyaltyCardEntity(id = 3, brandId = 7)
        db.brandDao().insert(brand)
        dao.insert(card)

        assertThat(dao.getAllWithBrand().first()).containsExactly(LoyaltyCardWithBrand(card, brand))
    }

    @Test
    fun getAllWithBrand_joinsEachCardWithItsOwnBrand() = runTest {
        val first = aBrandEntity(id = 1, name = "First Store", color = "#111111")
        val second = aBrandEntity(id = 2, name = "Second Store", color = "#222222")
        db.brandDao().insert(first)
        db.brandDao().insert(second)
        val cardOfSecond = aLoyaltyCardEntity(id = 10, brandId = 2)
        val cardOfFirst = aLoyaltyCardEntity(id = 11, brandId = 1)
        dao.insert(cardOfSecond)
        dao.insert(cardOfFirst)

        assertThat(dao.getAllWithBrand().first()).containsExactly(
            LoyaltyCardWithBrand(cardOfSecond, second),
            LoyaltyCardWithBrand(cardOfFirst, first),
        )
    }

    @Test
    fun getByIdWithBrand_mapsEveryCardAndBrandField() = runTest {
        // Every field differs from its counterpart, so a wrong alias (card id read as brand id, …) shows up.
        val brand = aBrandEntity(id = 7, name = "Other Store", color = "#112233", logo = "https://example.com/logo.svg")
        val card = aLoyaltyCardEntity(id = 3, name = "Gold card", brandId = 7, code = "0042", barcodeFormat = "EAN_13")
        db.brandDao().insert(brand)
        dao.insert(card)
        dao.insert(aLoyaltyCardEntity(id = 4, brandId = 7))

        assertThat(dao.getByIdWithBrand(3).first()).isEqualTo(LoyaltyCardWithBrand(card, brand))
    }

    @Test
    fun getByIdWithBrand_unknownId_emitsNull() = runTest {
        assertThat(dao.getByIdWithBrand(99).first()).isNull()
    }
}
