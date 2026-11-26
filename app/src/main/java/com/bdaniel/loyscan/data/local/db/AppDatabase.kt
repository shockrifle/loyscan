package com.bdaniel.loyscan.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardDao
import com.bdaniel.loyscan.data.local.db.dao.StoreDao
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity

@Database(
    entities = [StoreEntity::class, LoyaltyCardEntity::class],
    version = AppDatabase.VERSION,
    autoMigrations = [
        // loyalty_cards.showPhotoOnList, off for every existing card (#184).
        AutoMigration(from = 2, to = 3),
        // loyalty_cards.showPhotoOnWidget, off for every existing card (#185).
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun loyaltyCardDao(): LoyaltyCardDao

    /** Schemas from version 2 (the first release) on are frozen: wiki/workflows/database-schema-changes.md (#12). */
    companion object {
        const val VERSION = 4

        /**
         * Every hand-written migration from a released version on; `DatabaseModule` adds them to the builder. The
         * `autoMigrations` above are wired by Room itself.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
