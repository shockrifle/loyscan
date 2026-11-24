package com.bdaniel.loyscan.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The Room-backed repository over a real in-memory database: DAO rows come out as domain models. */
@RunWith(AndroidJUnit4::class)
class RoomLoyaltyCardRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var photos: FileCardPhotos
    private lateinit var repository: RoomLoyaltyCardRepository

    private val store = aStore(id = "other", name = "Other Store", color = "#112233")
    private val card = aLoyaltyCard(id = cardId(3), label = "Gold card", store = store, number = "0042")

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photos = FileCardPhotos(temporaryFolder.newFolder("card_photos"), ApplicationProvider.getApplicationContext<Context>().contentResolver)
        repository = RoomLoyaltyCardRepository(db.loyaltyCardDao(), Clock.fixed(NOW, ZoneOffset.UTC), photos)
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
    fun getAllWithStore_emitsAgain_whenItsStoresLogoIsSetOrRemoved() = runTest { // #181: the widget redraws on it
        val stores = RoomStoreRepository(db.storeDao(), photos)

        repository.getAllWithStore().test {
            assertThat(awaitItem().single().store.logo).isNull()

            stores.setLogo("other", "logo-1")
            assertThat(awaitItem().single().store.logo).isEqualTo("logo-1")

            stores.setLogo("other", null)
            assertThat(awaitItem().single().store.logo).isNull()
        }
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
    fun add_storesTheCard_andItReadsBackWithItsStore() = runTest {
        val added = aLoyaltyCard(id = cardId(4), store = store, label = null, number = "5901234123457", scannedFormat = BarcodeFormat.EAN_13)

        repository.add(added)

        assertThat(repository.getByIdWithStore(cardId(4)).first()).isEqualTo(added)
    }

    @Test
    fun update_savesTheEdit_butKeepsTheStoredUseAndCreation() = runTest { // #8, note: #9, photos: #10
        repository.recordUse(cardId(3))
        val edited = card.copy(
            label = null,
            number = "777",
            scannedFormat = BarcodeFormat.QR_CODE,
            note = "PIN hint: birthday",
            frontPhotoPath = "front",
            backPhotoPath = "back",
            updatedAt = NOW,
            // An edit doesn't change these, whatever the card passed in says.
            createdAt = NOW,
            useCount = 0,
            lastUsedAt = null,
        )

        repository.update(edited)

        assertThat(repository.getByIdWithStore(cardId(3)).first()).isEqualTo(
            card.copy(
                label = null,
                number = "777",
                scannedFormat = BarcodeFormat.QR_CODE,
                note = "PIN hint: birthday",
                frontPhotoPath = "front",
                backPhotoPath = "back",
                updatedAt = NOW,
                useCount = 1,
                lastUsedAt = NOW,
            )
        )
    }

    @Test
    fun update_deletesAReplacedOrRemovedPhoto_andKeepsTheOthers() = runTest { // #10
        val replaced = storedPhoto("replaced")
        val removed = storedPhoto("removed")
        val replacement = storedPhoto("replacement")
        val anotherCards = storedPhoto("another")
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(4), storeId = "other", frontPhotoPath = "another"))
        repository.update(card.copy(frontPhotoPath = "replaced", backPhotoPath = "removed"))

        repository.update(card.copy(frontPhotoPath = "replacement", backPhotoPath = null))

        assertThat(replaced.exists()).isFalse()
        assertThat(removed.exists()).isFalse()
        assertThat(replacement.exists()).isTrue()
        assertThat(anotherCards.exists()).isTrue()
    }

    @Test
    fun update_keepingThePhotos_deletesNone() = runTest { // #10
        val front = storedPhoto("front")
        repository.update(card.copy(frontPhotoPath = "front"))

        repository.update(card.copy(label = "Renamed", frontPhotoPath = "front"))

        assertThat(front.exists()).isTrue()
    }

    @Test
    fun delete_removesTheCard() = runTest {
        repository.delete(cardId(3))

        assertThat(repository.getByIdWithStore(cardId(3)).first()).isNull()
    }

    @Test
    fun delete_deletesTheCardsPhotos() = runTest { // #10, from the card's screen
        val front = storedPhoto("front")
        val back = storedPhoto("back")
        repository.update(card.copy(frontPhotoPath = "front", backPhotoPath = "back"))

        repository.delete(cardId(3))

        assertThat(front.exists()).isFalse()
        assertThat(back.exists()).isFalse()
    }

    @Test
    fun deleteAll_deletesThoseCardsPhotos_only() = runTest { // #10, multi-select
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(4), storeId = "other", frontPhotoPath = "fourth"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(5), storeId = "other", backPhotoPath = "fifth"))
        repository.update(card.copy(backPhotoPath = "third"))
        val third = storedPhoto("third")
        val fourth = storedPhoto("fourth")
        val fifth = storedPhoto("fifth")

        repository.deleteAll(setOf(cardId(3), cardId(4)))

        assertThat(third.exists()).isFalse()
        assertThat(fourth.exists()).isFalse()
        assertThat(fifth.exists()).isTrue()
    }

    @Test
    fun deleteAll_removesThoseCards() = runTest { // #7: multi-select
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(4), storeId = "other"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(5), storeId = "other"))

        repository.deleteAll(setOf(cardId(3), cardId(4)))

        assertThat(repository.getAllWithStore().first().map { it.id }).containsExactly(cardId(5))
    }

    @Test
    fun getByIdWithStore_unknownId_emitsNull() = runTest {
        assertThat(repository.getByIdWithStore(cardId(99)).first()).isNull()
    }

    @Test
    fun countForStore_countsThatStoresCards_andFollowsChanges() = runTest { // #53
        db.storeDao().insert(aStoreEntity(id = "empty", name = "Empty Store"))

        assertThat(repository.countForStore("other").first()).isEqualTo(1)
        assertThat(repository.countForStore("empty").first()).isEqualTo(0)

        repository.add(aLoyaltyCard(id = cardId(4), store = store))

        assertThat(repository.countForStore("other").first()).isEqualTo(2)
    }

    /** A photo file as [FileCardPhotos] keeps it, named [name]. */
    private fun storedPhoto(name: String): File = photos.file(name).apply { writeBytes(byteArrayOf(1)) }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-05T05:05:05Z")
    }
}
