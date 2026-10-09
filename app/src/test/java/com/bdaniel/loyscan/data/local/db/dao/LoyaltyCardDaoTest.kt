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
}
