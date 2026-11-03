package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The sweep of orphaned card photos (#10) against an on-disk database that is closed and reopened, as on the next start
 * after the process died with a form open: no `onCleared` ran to delete what that form had picked.
 */
@RunWith(AndroidJUnit4::class)
class CardPhotoSweepCallbackTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val photos by lazy { FileCardPhotos(temporaryFolder.newFolder("card_photos"), context.contentResolver) }
    private val opened = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun reopen_deletesOldPhotosNoCardNames_andKeepsTheCardsOwnAndRecentOnes() = runTest {
        open(sweep = false).apply {
            storeDao().insert(aStoreEntity())
            loyaltyCardDao().insert(aLoyaltyCardEntity(id = cardId(1), frontPhotoPath = "front", backPhotoPath = "back"))
        }
        opened.forEach { it.close() }
        val front = photo("front", modified = NOW - Duration.ofDays(30))
        val back = photo("back", modified = NOW - Duration.ofDays(30))
        val abandoned = photo("abandoned", modified = NOW - Duration.ofDays(1) - Duration.ofMinutes(1))
        // Perhaps picked in a form still open in another task, or restored after process death.
        val recent = photo("recent", modified = NOW - Duration.ofHours(23))

        open(sweep = true).loyaltyCardDao().getAllWithStore().first()

        assertThat(front.exists()).isTrue()
        assertThat(back.exists()).isTrue()
        assertThat(abandoned.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    @Test
    fun aDatabaseWithoutPhotos_opens() = runTest {
        val db = open(sweep = true)

        assertThat(db.loyaltyCardDao().getAllWithStore().first()).isEmpty()
    }

    private fun open(sweep: Boolean): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .apply { if (sweep) addCallback(CardPhotoSweepCallback(photos, Clock.fixed(NOW, ZoneOffset.UTC))) }
            .build()
            .also { opened += it }

    private fun photo(name: String, modified: Instant): File =
        photos.file(name).apply {
            writeBytes(byteArrayOf(1))
            setLastModified(modified.toEpochMilli())
        }

    private companion object {
        const val DB_NAME = "photo-sweep-test.db"
        val NOW: Instant = Instant.parse("2026-05-05T05:05:05Z")
    }
}
