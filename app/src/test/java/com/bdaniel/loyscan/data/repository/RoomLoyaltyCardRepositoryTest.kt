package com.bdaniel.loyscan.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The Room-backed repository over a real in-memory database: DAO rows come out as domain models. */
@RunWith(AndroidJUnit4::class)
class RoomLoyaltyCardRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomLoyaltyCardRepository

    private val store = aStore(id = "other", name = "Other Store", color = "#112233")
    private val card = aLoyaltyCard(id = cardId(3), label = "Gold card", store = store, number = "0042")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLoyaltyCardRepository(db.loyaltyCardDao(), Clock.fixed(NOW, ZoneOffset.UTC))
        db.storeDao().insert(aStoreEntity(id = "other", name = "Other Store", color = "#112233"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(3), label = "Gold card", storeId = "other", number = "0042"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAllWithStore_returnsDomainCards() = runTest {
        assertThat(repository.getAllWithStore().first()).containsExactly(card)
    }

    @Test
    fun getByIdWithStore_returnsTheDomainCard() = runTest {
        assertThat(repository.getByIdWithStore(cardId(3)).first()).isEqualTo(card)
    }

    @Test
    fun recordUse_countsAUseAtTheClocksTime() = runTest {
        repository.recordUse(cardId(3))

        assertThat(repository.getByIdWithStore(cardId(3)).first()).isEqualTo(card.copy(useCount = 1, lastUsedAt = NOW))
    }

    @Test
    fun getByIdWithStore_unknownId_emitsNull() = runTest {
        assertThat(repository.getByIdWithStore(cardId(99)).first()).isNull()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-05T05:05:05Z")
    }
}
