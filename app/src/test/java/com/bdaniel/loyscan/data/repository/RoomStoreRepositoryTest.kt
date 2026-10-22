package com.bdaniel.loyscan.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** The Room-backed store repository over a real in-memory database: rows come out as domain stores. */
@RunWith(AndroidJUnit4::class)
class RoomStoreRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomStoreRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomStoreRepository(db.storeDao())
        db.storeDao().insert(aStoreEntity(id = "first", name = "First Store", aliases = listOf("First")))
        db.storeDao().insert(aStoreEntity(id = "second", name = "Second Store", color = "#112233"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getAll_returnsDomainStores() = runTest {
        assertThat(repository.getAll().first()).containsExactly(
            aStore(id = "first", name = "First Store", aliases = listOf("First")),
            aStore(id = "second", name = "Second Store", color = "#112233"),
        )
    }

    @Test
    fun add_insertsTheStore_andGetAllEmitsIt() = runTest {
        val custom = aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true)

        repository.add(custom)

        assertThat(repository.getAll().first()).contains(custom)
    }

    @Test
    fun update_savesACustomStoresNameColourAndFormat() = runTest { // #53
        val custom = aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true)
        repository.add(custom)
        val edited = custom.copy(
            name = "Renamed Store",
            color = "#445566",
            defaultBarcodeFormat = BarcodeFormat.QR_CODE,
            updatedAt = Instant.parse("2026-10-22T12:00:00Z"),
        )

        repository.update(edited)

        assertThat(repository.getAll().first()).contains(edited)
    }

    @Test
    fun update_leavesACatalogueStoreAsItIs() = runTest { // #53: only custom stores are edited
        val before = repository.getAll().first()

        repository.update(aStore(id = "first", name = "Renamed Store", aliases = listOf("First")))

        assertThat(repository.getAll().first()).isEqualTo(before)
    }

    @Test
    fun aCustomStoresNewFormat_drawsItsTypedCards_butNotItsScannedOnes() = runTest { // #53
        val custom = aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true)
        repository.add(custom)
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "custom", number = "4242"))
        db.loyaltyCardDao().insert(
            aLoyaltyCardEntity(id = cardId(2), storeId = "custom", number = "5901234123457", scannedFormat = BarcodeFormat.EAN_13)
        )

        repository.update(custom.copy(defaultBarcodeFormat = BarcodeFormat.QR_CODE))

        val cards = db.loyaltyCardDao().getAllWithStore().first().map { it.toDomainModel() }.associateBy { it.id }
        assertThat(cards.getValue(cardId(1)).barcodeFormat).isEqualTo(BarcodeFormat.QR_CODE)
        assertThat(cards.getValue(cardId(2)).barcodeFormat).isEqualTo(BarcodeFormat.EAN_13)
    }

    @Test
    fun delete_removesACustomStore_andItsCards() = runTest { // #53
        repository.add(aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "custom"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), storeId = "first"))

        repository.delete("custom")

        assertThat(repository.getAll().first().map { it.id }).containsExactly("first", "second")
        assertThat(db.loyaltyCardDao().getAllWithStore().first().map { it.loyaltyCard.id }).containsExactly(cardId(2))
    }

    @Test
    fun delete_leavesACatalogueStoreAndItsCards() = runTest { // #53: catalogue stores can't be deleted
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "first"))

        repository.delete("first")

        assertThat(repository.getAll().first().map { it.id }).containsExactly("first", "second")
        assertThat(db.loyaltyCardDao().getAllWithStore().first()).hasSize(1)
    }
}
