package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Fills a newly created database from `assets/database/loyscan.sql`, one statement per line. A stopgap loader:
 * see wiki/hacks/line-based-sql-seed-parser.md. Tests pass a direct [executor] to seed synchronously.
 */
class SeedDatabaseCallback(
    private val context: Context,
    private val executor: Executor = Executors.newSingleThreadExecutor(),
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        executor.execute {
            context.assets.open(SEED_ASSET).use { inputStream ->
                inputStream.reader().buffered().lines()?.forEach { line ->
                    line.trim().takeIf { it.isNotEmpty() && !it.startsWith("--") }?.let {
                        db.execSQL(it)
                    }
                }
            }
        }
    }

    companion object {
        const val SEED_ASSET = "database/loyscan.sql"
    }
}
