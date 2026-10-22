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

/** The app's database as the app builds it: a new one holds the catalogue and nothing else. */
@RunWith(AndroidJUnit4::class)
class DatabaseModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db: AppDatabase = DatabaseModule.provideAppDatabase(context)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("loyscan.db")
    }

    @Test
    fun aNewDatabase_holdsTheCatalogueStores_andNoCards() = runTest { // #55: no demo cards, in debug builds either
        val stores = db.storeDao().getAll().first()

        assertThat(db.loyaltyCardDao().getAllWithStore().first()).isEmpty()
        assertThat(stores).isNotEmpty()
        assertThat(stores.map { it.id }).containsNoneOf("demo-a", "demo-b", "demo-c", "demo-d", "demo-e")
    }
}
