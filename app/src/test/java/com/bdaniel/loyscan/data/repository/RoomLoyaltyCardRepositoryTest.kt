package com.bdaniel.loyscan.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.aBrand
import com.bdaniel.loyscan.testing.aBrandEntity
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Room-backed repository over a real in-memory database: DAO rows come out as domain models. */
@RunWith(AndroidJUnit4::class)
class RoomLoyaltyCardRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomLoyaltyCardRepository

    private val brand = aBrand(id = 7, name = "Other Store", color = "#112233")
    private val card = aLoyaltyCard(id = 3, name = "Gold card", brand = brand, code = "0042")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLoyaltyCardRepository(db.loyaltyCardDao())
        db.brandDao().insert(aBrandEntity(id = 7, name = "Other Store", color = "#112233"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = 3, name = "Gold card", brandId = 7, code = "0042"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAllWithBrand_returnsDomainCards() = runTest {
        assertThat(repository.getAllWithBrand().first()).containsExactly(card)
    }

    @Test
    fun getByIdWithBrand_returnsTheDomainCard() = runTest {
        assertThat(repository.getByIdWithBrand(3).first()).isEqualTo(card)
    }

    @Test
    fun getByIdWithBrand_unknownId_emitsNull() = runTest {
        assertThat(repository.getByIdWithBrand(99).first()).isNull()
    }
}
