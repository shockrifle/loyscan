package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.core.content.contentValuesOf
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.FrozenSchemaTest.Companion.FIRST_RELEASED_VERSION
import com.bdaniel.loyscan.data.local.db.FrozenSchemaTest.Companion.FROZEN_SCHEMAS
import com.bdaniel.loyscan.di.CardPhotosModule
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
    private fun SQLiteDatabase.insertACardAsTheFirstReleaseStoredIt() {
        insertOrThrow(
            "stores", null,
            contentValuesOf(
                "id" to "custom-1", "name" to "Corner Shop", "color" to "#336699", "logo" to null,
                "aliases" to "[]", "countryCodes" to "[]", "defaultBarcodeFormat" to "EAN_13",
                "cardNumberRule" to null, "frequentlyAdded" to 0, "isCustom" to 1, "updatedAt" to 1_700_000_000_000L,
            ),
        )
        insertOrThrow(
            "loyalty_cards", null,
            contentValuesOf(
                "id" to "card-1", "storeId" to "custom-1", "label" to "Everyday", "number" to "1234567890128",
                "scannedFormat" to null, "createdAt" to 1_700_000_000_000L, "lastUsedAt" to 1_700_000_100_000L,
                "useCount" to 3, "note" to "Behind the counter", "frontPhotoPath" to null, "backPhotoPath" to null,
                "updatedAt" to 1_700_000_000_000L,
            ),
        )
    }

    private companion object {
        const val DATABASE = "loyscan.db"
    }
}
