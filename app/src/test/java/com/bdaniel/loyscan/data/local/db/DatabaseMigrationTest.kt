package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.core.content.contentValuesOf
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.FrozenSchemaTest.Companion.FIRST_RELEASED_VERSION
import com.bdaniel.loyscan.data.local.db.FrozenSchemaTest.Companion.FROZEN_SCHEMAS
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.di.DatabaseModule
import com.bdaniel.loyscan.testing.createDatabaseFromExportedSchema
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database an installed release left behind (or a new phone got by device-to-device transfer) opens in the current
 * app, through the app's own builder and its migrations, with its cards (#12). The old database is built from its
 * exported schema in `app/schemas/`. Without a migration Room throws on open, a migration that leaves the tables unlike
 * the entities fails Room's check after migrating, and a database with no migration path must fail to open with its
 * cards kept (a destructive fallback would open it empty).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var opened: AppDatabase? = null

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun aDatabaseFromTheFirstRelease_opensInTheCurrentApp_withItsCards() = runTest {
        createDatabaseFromExportedSchema(context, DATABASE, FIRST_RELEASED_VERSION).use { it.insertACardAsTheFirstReleaseStoredIt() }

        val cards = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first()

        assertThat(cards).hasSize(1)
        with(cards.single()) {
            assertThat(loyaltyCard.id).isEqualTo("card-1")
            assertThat(loyaltyCard.label).isEqualTo("Everyday")
            assertThat(loyaltyCard.number).isEqualTo("1234567890128")
            assertThat(loyaltyCard.note).isEqualTo("Behind the counter")
            assertThat(loyaltyCard.useCount).isEqualTo(3)
            assertThat(store.name).isEqualTo("Corner Shop")
        }
    }

    @Test
    fun aCardFromTheFirstRelease_withAFrontPhoto_doesNotShowItOnTheList_afterTheMigration() = runTest { // #184
        createDatabaseFromExportedSchema(context, DATABASE, FIRST_RELEASED_VERSION).use {
            it.insertACardAsTheFirstReleaseStoredIt(frontPhotoPath = "front")
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.frontPhotoPath).isEqualTo("front")
        assertThat(card.showPhotoOnList).isFalse()
    }

    @Test
    fun aCardFromTheFirstRelease_withAFrontPhoto_doesNotShowItOnTheWidget_afterTheMigration() = runTest { // #185
        createDatabaseFromExportedSchema(context, DATABASE, FIRST_RELEASED_VERSION).use {
            it.insertACardAsTheFirstReleaseStoredIt(frontPhotoPath = "front")
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.showPhotoOnWidget).isFalse()
    }

    @Test
    fun aCardShowingItsPhotoOnTheList_atVersion3_keepsIt_andDoesNotShowItOnTheWidget_afterTheMigration() = runTest { // #185
        createDatabaseFromExportedSchema(context, DATABASE, 3).use {
            it.insertACardAsTheFirstReleaseStoredIt(frontPhotoPath = "front", showPhotoOnList = true)
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.frontPhotoPath).isEqualTo("front")
        assertThat(card.showPhotoOnList).isTrue()
        assertThat(card.showPhotoOnWidget).isFalse()
    }

    @Test
    fun aStoreWithALogo_atVersion4_drawsItAsIs_afterTheMigration() = runTest { // #240
        createDatabaseFromExportedSchema(context, DATABASE, 4).use { it.insertACardAsTheFirstReleaseStoredIt(logo = "logo-1") }

        val store = openAsTheAppDoes().storeDao().getById("custom-1").first()!!

        assertThat(store.logo).isEqualTo("logo-1")
        assertThat(store.logoStyle).isNull()
        assertThat(store.logoTint).isNull()
        assertThat(store.logoOutline).isNull()
        assertThat(store.toDomainModel().logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun aTypedCardOfAStoreWithARule_atVersion5_getsTheRulesFormat_afterTheMigration() = runTest { // #187
        createDatabaseFromExportedSchema(context, DATABASE, 5).use {
            it.insertACardAsTheFirstReleaseStoredIt(defaultBarcodeFormat = "EAN_13", cardNumberRule = QR_RULE)
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.QR_CODE)
    }

    @Test
    fun aTypedCardOfAStoreWithoutARule_atVersion5_getsTheStoresDefault_afterTheMigration() = runTest { // #187
        createDatabaseFromExportedSchema(context, DATABASE, 5).use {
            it.insertACardAsTheFirstReleaseStoredIt(defaultBarcodeFormat = "ITF")
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.ITF)
    }

    @Test
    fun aScannedCard_atVersion5_keepsItsScannedFormat_afterTheMigration() = runTest { // #187
        createDatabaseFromExportedSchema(context, DATABASE, 5).use {
            it.insertACardAsTheFirstReleaseStoredIt(
                defaultBarcodeFormat = "EAN_13", cardNumberRule = QR_RULE, scannedFormat = "CODE_39",
            )
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.scannedFormat).isEqualTo(BarcodeFormat.CODE_39)
        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.CODE_39)
    }

    @Test
    fun aTypedCardWhoseStoresRuleCantBeRead_atVersion5_getsTheStoresDefault_afterTheMigration() = runTest { // #187
        // A catalogue id: the catalogue sync after the migration rewrites the store's rule, so the card reads back.
        createDatabaseFromExportedSchema(context, DATABASE, 5).use {
            it.insertACardAsTheFirstReleaseStoredIt(
                storeId = "hu-ofotert", defaultBarcodeFormat = "CODE_39", cardNumberRule = "not a rule",
            )
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.CODE_39)
    }

    @Test
    fun aTypedCardFromTheFirstRelease_getsItsStoresDefault_afterTheMigrations() = runTest { // #187
        createDatabaseFromExportedSchema(context, DATABASE, FIRST_RELEASED_VERSION).use {
            it.insertACardAsTheFirstReleaseStoredIt(defaultBarcodeFormat = "UPC_A")
        }

        val card = openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first().single().loyaltyCard

        assertThat(card.barcodeFormat).isEqualTo(BarcodeFormat.UPC_A)
    }

    @Test
    fun anEmptyDatabaseAtEveryFrozenVersion_opensInTheCurrentApp() = runTest {
        for (version in FROZEN_SCHEMAS.keys.sorted()) {
            createDatabaseFromExportedSchema(context, DATABASE, version).close()

            val stores = openAsTheAppDoes().storeDao().getAll().first()

            assertThat(stores).isNotEmpty() // the catalogue, synced on open
            opened!!.close()
            opened = null
            context.deleteDatabase(DATABASE)
        }
    }

    @Test
    fun aDatabaseWithNoMigrationPath_failsToOpen_andKeepsItsCards() = runTest {
        // A version the app has no migration from (here: one newer than the app). A destructive fallback would open it
        // empty; the app must refuse it and leave the cards on disk.
        createDatabaseFromExportedSchema(context, DATABASE, FIRST_RELEASED_VERSION).use {
            it.insertACardAsTheFirstReleaseStoredIt()
            it.version = AppDatabase.VERSION + 1
        }

        val failure = runCatching { openAsTheAppDoes().loyaltyCardDao().getAllWithStore().first() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        opened?.close()
        opened = null
        val cardsOnDisk = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE).path, null, SQLiteDatabase.OPEN_READONLY)
            .use { db -> db.rawQuery("SELECT id FROM loyalty_cards", null).use { it.count } }
        assertThat(cardsOnDisk).isEqualTo(1)
    }

    private fun openAsTheAppDoes(): AppDatabase =
        DatabaseModule.provideAppDatabase(context, CardPhotosModule.provideFileCardPhotos(context)).also { opened = it }

    // The first release's columns, written out: the entities and their ContentValues mappers follow the current schema.
    // [showPhotoOnList] is v3's column (#184): only for a database built at version 3.
    private fun SQLiteDatabase.insertACardAsTheFirstReleaseStoredIt(
        frontPhotoPath: String? = null,
        showPhotoOnList: Boolean? = null,
        logo: String? = null,
        storeId: String = "custom-1",
        defaultBarcodeFormat: String = "EAN_13",
        cardNumberRule: String? = null,
        scannedFormat: String? = null,
    ) {
        insertOrThrow(
            "stores", null,
            contentValuesOf(
                "id" to storeId, "name" to "Corner Shop", "color" to "#336699", "logo" to logo,
                "aliases" to "[]", "countryCodes" to "[]", "defaultBarcodeFormat" to defaultBarcodeFormat,
                "cardNumberRule" to cardNumberRule, "frequentlyAdded" to 0, "isCustom" to 1, "updatedAt" to 1_700_000_000_000L,
            ),
        )
        insertOrThrow(
            "loyalty_cards", null,
            contentValuesOf(
                "id" to "card-1", "storeId" to storeId, "label" to "Everyday", "number" to "1234567890128",
                "scannedFormat" to scannedFormat, "createdAt" to 1_700_000_000_000L, "lastUsedAt" to 1_700_000_100_000L,
                "useCount" to 3, "note" to "Behind the counter", "frontPhotoPath" to frontPhotoPath, "backPhotoPath" to null,
                "updatedAt" to 1_700_000_000_000L,
            ).apply { showPhotoOnList?.let { put("showPhotoOnList", if (it) 1 else 0) } },
        )
    }

    private companion object {
        const val DATABASE = "loyscan.db"

        /** A rule as v5 stored it (its JSON), in a format other than the stores' default in these tests. */
        const val QR_RULE = """{"id":"r","format":"QR_CODE"}"""
    }
}
