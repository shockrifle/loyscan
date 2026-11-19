package com.bdaniel.loyscan.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.AppDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration

/** The app's database as the app builds it: a new one holds the catalogue and nothing else, and opening it sweeps photos. */
@RunWith(AndroidJUnit4::class)
class DatabaseModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photos = CardPhotosModule.provideFileCardPhotos(context)
    private val db: AppDatabase = DatabaseModule.provideAppDatabase(context, photos)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("loyscan.db")
    }

    @Test
    fun theDatabase_isKeptInTheAppsPrivateStorage() { // the privacy policy says so (#75)
        val path = File(db.openHelper.writableDatabase.path!!).canonicalPath

        assertThat(path).startsWith(context.dataDir.canonicalPath + File.separator)
    }

    @Test
    fun opening_sweepsAnOldPhotoNoCardNames() = runTest { // #10: a form killed with the process left it
        val orphan = photos.file("orphan").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
            setLastModified(System.currentTimeMillis() - Duration.ofDays(2).toMillis())
        }

        db.loyaltyCardDao().getAllWithStore().first()

        assertThat(orphan.exists()).isFalse()
    }

    @Test
    fun aNewDatabase_holdsTheCatalogueStores_andNoCards() = runTest { // #55: no demo cards, in debug builds either
        val stores = db.storeDao().getAll().first()

        assertThat(db.loyaltyCardDao().getAllWithStore().first()).isEmpty()
        assertThat(stores).isNotEmpty()
        assertThat(stores.map { it.id }).containsNoneOf("demo-a", "demo-b", "demo-c", "demo-d", "demo-e")
    }
}
