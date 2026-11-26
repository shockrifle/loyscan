package com.bdaniel.loyscan.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** The Room-backed store repository over a real in-memory database: rows come out as domain stores. */
@RunWith(AndroidJUnit4::class)
class RoomStoreRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var photos: FileCardPhotos
    private lateinit var repository: RoomStoreRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photos = FileCardPhotos(temporaryFolder.newFolder("card_photos"), ApplicationProvider.getApplicationContext<Context>().contentResolver)
        repository = RoomStoreRepository(db.storeDao(), photos)
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
    fun update_keepsACustomStoresLogo() = runTest { // #151: the edit form saves a store whose logo may be stale or none
        repository.add(aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true))
        repository.setLogo("custom", "logo")

        repository.update(aStore(id = "custom", name = "Renamed Store", countryCodes = emptyList(), isCustom = true, logo = null))

        val saved = repository.getAll().first().single { it.id == "custom" }
        assertThat(saved.name).isEqualTo("Renamed Store")
        assertThat(saved.logo).isEqualTo("logo")
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

    @Test
    fun delete_deletesThePhotosOfTheStoresCards() = runTest { // #10
        repository.add(aStore(id = "custom", name = "Custom Store", countryCodes = emptyList(), isCustom = true))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "custom", frontPhotoPath = "front", backPhotoPath = "back"))
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(2), storeId = "first", frontPhotoPath = "kept"))
        val front = storedPhoto("front")
        val back = storedPhoto("back")
        val kept = storedPhoto("kept")

        repository.delete("custom")

        assertThat(front.exists()).isFalse()
        assertThat(back.exists()).isFalse()
        assertThat(kept.exists()).isTrue()
    }

    @Test
    fun delete_ofACatalogueStore_keepsItsCardsPhotos() = runTest { // #10: the store and its cards stay
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "first", frontPhotoPath = "front"))
        val front = storedPhoto("front")

        repository.delete("first")

        assertThat(front.exists()).isTrue()
    }

    @Test
    fun setLogo_isTheLogoOfTheStoresCards() = runTest { // #151
        db.loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), storeId = "first"))

        repository.setLogo("first", "logo")

        assertThat(repository.getAll().first().single { it.id == "first" }.logo).isEqualTo("logo")
        assertThat(db.loyaltyCardDao().getAllWithStore().first().single().toDomainModel().store.logo).isEqualTo("logo")
    }

    @Test
    fun setLogo_ofACustomStore_setsIt() = runTest { // #151: any store, custom or catalogue
        repository.add(aStore(id = "custom", isCustom = true))

        repository.setLogo("custom", "logo")

        assertThat(repository.getAll().first().single { it.id == "custom" }.logo).isEqualTo("logo")
    }

    @Test
    fun setLogo_replacingALogo_deletesTheOldFile() = runTest { // #151
        val old = storedPhoto("old")
        val new = storedPhoto("new")
        repository.setLogo("first", "old")

        repository.setLogo("first", "new")

        assertThat(old.exists()).isFalse()
        assertThat(new.exists()).isTrue()
    }

    @Test
    fun setLogo_null_removesTheLogo_andDeletesItsFile() = runTest { // #151
        val logo = storedPhoto("logo")
        repository.setLogo("first", "logo")

        repository.setLogo("first", null)

        assertThat(repository.getAll().first().single { it.id == "first" }.logo).isNull()
        assertThat(logo.exists()).isFalse()
    }

    @Test
    fun setLogo_theSameLogoAgain_keepsItsFile() = runTest { // #151
        val logo = storedPhoto("logo")
        repository.setLogo("first", "logo")

        repository.setLogo("first", "logo")

        assertThat(logo.exists()).isTrue()
    }

    @Test
    fun setLogo_ofAStoreThatIsGone_deletesTheNewFile() = runTest { // #151: nothing would name it
        val logo = storedPhoto("logo")

        repository.setLogo("gone", "logo")

        assertThat(logo.exists()).isFalse()
        assertThat(repository.getAll().first().map { it.id }).containsExactly("first", "second")
    }

    @Test
    fun delete_deletesTheStoresLogo() = runTest { // #151
        repository.add(aStore(id = "custom", isCustom = true))
        val logo = storedPhoto("logo")
        repository.setLogo("custom", "logo")

        repository.delete("custom")

        assertThat(logo.exists()).isFalse()
    }

    @Test
    fun delete_ofACatalogueStore_keepsItsLogo() = runTest { // #151: the store stays
        val logo = storedPhoto("logo")
        repository.setLogo("first", "logo")

        repository.delete("first")

        assertThat(logo.exists()).isTrue()
    }

    @Test
    fun setLogoTreatment_isHowTheStoresLogoIsDrawn() = runTest { // #240
        repository.setLogo("first", "logo")

        repository.setLogoTreatment("first", SINGLE_BLACK_OUTLINED)

        assertThat(repository.getAll().first().single { it.id == "first" }.logoTreatment).isEqualTo(SINGLE_BLACK_OUTLINED)
        assertThat(repository.getAll().first().single { it.id == "second" }.logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun setLogoTreatment_ofAStoreThatIsGone_addsNothing() = runTest { // #240
        val before = repository.getAll().first()

        repository.setLogoTreatment("gone", SINGLE_BLACK_OUTLINED)

        assertThat(repository.getAll().first()).isEqualTo(before)
    }

    @Test
    fun setLogo_aNewLogo_drawsItAsIs() = runTest { // #240: the treatment was for the old logo
        repository.setLogo("first", "old")
        repository.setLogoTreatment("first", SINGLE_BLACK_OUTLINED)

        repository.setLogo("first", "new")

        assertThat(repository.getAll().first().single { it.id == "first" }.logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun setLogo_null_resetsHowTheLogoIsDrawn() = runTest { // #240
        repository.setLogo("first", "logo")
        repository.setLogoTreatment("first", SINGLE_BLACK_OUTLINED)

        repository.setLogo("first", null)

        assertThat(repository.getAll().first().single { it.id == "first" }.logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun setLogo_theSameLogoAgain_keepsHowItIsDrawn() = runTest { // #240: not a new logo
        repository.setLogo("first", "logo")
        repository.setLogoTreatment("first", SINGLE_BLACK_OUTLINED)

        repository.setLogo("first", "logo")

        assertThat(repository.getAll().first().single { it.id == "first" }.logoTreatment).isEqualTo(SINGLE_BLACK_OUTLINED)
    }

    @Test
    fun setLogo_aNewLogoWithATreatment_savesBoth() = runTest { // #240: the treatment the contrast check chose
        repository.setLogo("first", "old")

        repository.setLogo("first", "new", RECOLOUR_WHITE)

        val stored = repository.getAll().first().single { it.id == "first" }
        assertThat(stored.logo).isEqualTo("new")
        assertThat(stored.logoTreatment).isEqualTo(RECOLOUR_WHITE)
    }

    @Test
    fun setLogo_aTreatmentForNoLogo_orForTheSameLogoAgain_isNotSaved() = runTest { // #240: it is a new logo's alone
        repository.setLogo("first", "logo")
        repository.setLogoTreatment("first", SINGLE_BLACK_OUTLINED)

        repository.setLogo("first", "logo", RECOLOUR_WHITE)
        val again = repository.getAll().first().single { it.id == "first" }.logoTreatment
        repository.setLogo("first", null, RECOLOUR_WHITE)
        val removed = repository.getAll().first().single { it.id == "first" }.logoTreatment

        assertThat(again).isEqualTo(SINGLE_BLACK_OUTLINED)
        assertThat(removed).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun update_aCustomStoresNewColour_keepsHowItsLogoIsDrawn() = runTest { // #240
        repository.add(aStore(id = "custom", color = "#112233", countryCodes = emptyList(), isCustom = true))
        repository.setLogo("custom", "logo")
        repository.setLogoTreatment("custom", SINGLE_BLACK_OUTLINED)

        repository.update(aStore(id = "custom", color = "#C62828", countryCodes = emptyList(), isCustom = true))

        val saved = repository.getAll().first().single { it.id == "custom" }
        assertThat(saved.color).isEqualTo("#C62828")
        assertThat(saved.logoTreatment).isEqualTo(SINGLE_BLACK_OUTLINED)
    }

    /** A photo file as [FileCardPhotos] keeps it, named [name]. */
    private fun storedPhoto(name: String): File = photos.file(name).apply { writeBytes(byteArrayOf(1)) }

    private companion object {
        val SINGLE_BLACK_OUTLINED = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000", outline = true)
        val RECOLOUR_WHITE = LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#FFFFFF")
    }
}
