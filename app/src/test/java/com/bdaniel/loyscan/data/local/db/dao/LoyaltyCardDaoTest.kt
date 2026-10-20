package com.bdaniel.loyscan.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

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
    fun getAllWithStore_returnsEachCardWithItsStore() = runTest {
        val store = aStoreEntity(id = "other", name = "Other Store", color = "#112233", logo = "https://example.com/logo.svg")
        val card = aLoyaltyCardEntity(id = cardId(3), storeId = "other")
        db.storeDao().insert(store)
        dao.insert(card)

        assertThat(dao.getAllWithStore().first()).containsExactly(LoyaltyCardWithStore(card, store))
    }

    @Test
    fun getAllWithStore_pairsEachCardWithItsOwnStore() = runTest {
        val first = aStoreEntity(id = "first", name = "First Store", color = "#111111")
        val second = aStoreEntity(id = "second", name = "Second Store", color = "#222222")
        db.storeDao().insert(first)
        db.storeDao().insert(second)
        val cardOfSecond = aLoyaltyCardEntity(id = cardId(10), storeId = "second")
        val cardOfFirst = aLoyaltyCardEntity(id = cardId(11), storeId = "first")
        dao.insert(cardOfSecond)
        dao.insert(cardOfFirst)

        assertThat(dao.getAllWithStore().first()).containsExactly(
            LoyaltyCardWithStore(cardOfSecond, second),
            LoyaltyCardWithStore(cardOfFirst, first),
        )
    }

    @Test
    fun getByIdWithStore_storesEveryCardField() = runTest {
        // Every field is set to a non-default value, so a column that isn't persisted or converted shows up.
        val store = aStoreEntity(id = "other")
        val card = aLoyaltyCardEntity(
            id = cardId(3),
            storeId = "other",
            label = "Gold card",
            number = "0042",
            scannedFormat = BarcodeFormat.EAN_13,
            createdAt = Instant.parse("2026-01-01T00:00:00.001Z"),
            lastUsedAt = Instant.parse("2026-02-02T00:00:00.002Z"),
            useCount = 7,
            note = "Expires in May",
            frontPhotoPath = "photos/front.jpg",
            backPhotoPath = "photos/back.jpg",
            updatedAt = Instant.parse("2026-03-03T00:00:00.003Z"),
        )
        db.storeDao().insert(store)
        dao.insert(card)
        dao.insert(aLoyaltyCardEntity(id = cardId(4), storeId = "other"))

        assertThat(dao.getByIdWithStore(cardId(3)).first()).isEqualTo(LoyaltyCardWithStore(card, store))
    }

    @Test
    fun aCardScannedAsGs1_128_isStoredAsGs1_128() = runTest { // #33: not as plain Code 128
        val card = aLoyaltyCardEntity(id = cardId(5), number = "9799123456789012", scannedFormat = BarcodeFormat.GS1_128)
        db.storeDao().insert(aStoreEntity(id = card.storeId))
        dao.insert(card)

        assertThat(dao.getByIdWithStore(cardId(5)).first()?.loyaltyCard?.scannedFormat).isEqualTo(BarcodeFormat.GS1_128)
    }

    @Test
    fun getByIdWithStore_unknownId_emitsNull() = runTest {
        assertThat(dao.getByIdWithStore(cardId(99)).first()).isNull()
    }

    @Test
    fun recordUse_countsTheUseAndStampsTheTime_ofThatCardOnly() = runTest {
        db.storeDao().insert(aStoreEntity())
        dao.insert(aLoyaltyCardEntity(id = cardId(1), useCount = 2, lastUsedAt = null))
        dao.insert(aLoyaltyCardEntity(id = cardId(2), useCount = 5))
        val at = Instant.parse("2026-04-04T00:00:00.004Z")

        dao.recordUse(cardId(1), at)

        val used = dao.getByIdWithStore(cardId(1)).first()!!.loyaltyCard
        assertThat(used.useCount).isEqualTo(3)
        assertThat(used.lastUsedAt).isEqualTo(at)
        assertThat(dao.getByIdWithStore(cardId(2)).first()!!.loyaltyCard.useCount).isEqualTo(5)
    }

    @Test
    fun recordUse_leavesUpdatedAtAlone() = runTest {
        db.storeDao().insert(aStoreEntity())
        dao.insert(aLoyaltyCardEntity(id = cardId(1), updatedAt = Instant.parse("2026-03-03T00:00:00Z")))

        dao.recordUse(cardId(1), Instant.parse("2026-04-04T00:00:00Z"))

        assertThat(dao.getByIdWithStore(cardId(1)).first()!!.loyaltyCard.updatedAt)
            .isEqualTo(Instant.parse("2026-03-03T00:00:00Z"))
    }

    @Test
    fun deletingAStore_deletesItsCards() = runTest {
        val store = aStoreEntity(id = "doomed")
        db.storeDao().insert(store)
        dao.insert(aLoyaltyCardEntity(id = cardId(1), storeId = "doomed"))

        db.storeDao().delete(store)

        assertThat(dao.getAllWithStore().first()).isEmpty()
    }
}
