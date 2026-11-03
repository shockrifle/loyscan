package com.bdaniel.loyscan.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import timber.log.Timber
import java.time.Clock
import java.time.Duration

/**
 * Deletes orphaned card photos when the database opens (#10): files no card names that are older than [minAge]. A form
 * deletes the photos picked in it when it closes unsaved, but a process killed with the form open (swiped from Recents,
 * a low-memory kill never returned to) skips that. Recent files stay: a form may still be open in another task, or come
 * back after process death, and save them.
 */
class CardPhotoSweepCallback(
    private val photos: FileCardPhotos,
    private val clock: Clock = Clock.systemUTC(),
    private val minAge: Duration = Duration.ofDays(1),
) : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        try {
            val named = mutableSetOf<String>()
            db.query("SELECT frontPhotoPath, backPhotoPath FROM loyalty_cards").use { cursor ->
                while (cursor.moveToNext()) {
                    for (column in 0..1) if (!cursor.isNull(column)) named += cursor.getString(column)
                }
            }
            photos.deleteUnnamed(named, olderThan = clock.instant() - minAge)
        } catch (e: Exception) { // a failed sweep mustn't keep the app from opening its cards
            Timber.e(e, "Couldn't sweep the card photos")
        }
    }
}
