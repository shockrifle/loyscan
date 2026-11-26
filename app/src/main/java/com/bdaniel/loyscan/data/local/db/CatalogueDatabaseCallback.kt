package com.bdaniel.loyscan.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdaniel.loyscan.data.mapper.toStoreEntity
import com.bdaniel.loyscan.domain.catalogue.StoreCatalogue
import timber.log.Timber
import java.time.Clock

/**
 * Brings the catalogue stores in the database up to date with the shipped catalogue (`assets/catalogue/stores.json`)
 * every time the database is opened. Room runs [onOpen] before the first query returns, so no store row is read
 * before it has the catalogue's current fields and rule ([wiki: store-catalogue]).
 *
 * Rows are matched by slug id, inserted when missing and updated in place when a field differs; `updatedAt` moves
 * only on a real change. Never `REPLACE`: that deletes the row first, and the foreign key would delete its cards.
 * Stores that are not in the catalogue (custom ones, entries dropped from it) are left alone. So are a store's
 * [USER_COLUMNS], its `logo` and how it is drawn: the catalogue ships no logos, and a logo there is the user's own (#151,
 * #240), which must survive a restart and a changed entry.
 */
class CatalogueDatabaseCallback(
    private val loadCatalogue: () -> String,
    private val clock: Clock = Clock.systemUTC(),
) : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        try {
            val entries = StoreCatalogue.parse(loadCatalogue())
            val now = clock.instant()
            db.beginTransaction()
            try {
                entries.forEach { sync(db, it.toStoreEntity(updatedAt = now).toContentValues()) }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            // A broken shipped catalogue is a build error (CatalogueDatabaseCallbackTest loads the real asset).
            Timber.e(e, "Loading the store catalogue failed")
            throw e
        }
    }

    private fun sync(db: SupportSQLiteDatabase, row: ContentValues) {
        val id = row.getAsString("id")
        val compared = row.keySet() - setOf("id", "updatedAt") - USER_COLUMNS
        val stored = db.query("SELECT ${compared.joinToString()} FROM stores WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) compared.associateWith { cursor.getString(cursor.getColumnIndexOrThrow(it)) } else null
        }
        when {
            stored == null -> db.insert("stores", SQLiteDatabase.CONFLICT_ABORT, row)
            compared.any { stored[it] != row.getAsString(it) } -> {
                USER_COLUMNS.forEach(row::remove)
                db.update("stores", SQLiteDatabase.CONFLICT_ABORT, row, "id = ?", arrayOf(id))
            }
        }
    }

    companion object {
        const val CATALOGUE_ASSET = "catalogue/stores.json"

        /** The columns the user owns on a catalogue row: its logo (#151) and how it is drawn (#240). Inserted empty, never rewritten. */
        private val USER_COLUMNS = setOf("logo", "logoStyle", "logoTint", "logoOutline")

        fun fromAssets(context: Context) = CatalogueDatabaseCallback({ readShippedCatalogue(context) })

        fun readShippedCatalogue(context: Context): String =
            context.assets.open(CATALOGUE_ASSET).bufferedReader().use { it.readText() }
    }
}
