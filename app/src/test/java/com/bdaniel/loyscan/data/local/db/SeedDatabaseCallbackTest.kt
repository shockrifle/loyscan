package com.bdaniel.loyscan.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithBrand
import com.bdaniel.loyscan.data.local.db.entity.BrandEntity
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real seed script (`assets/database/loyscan.sql`) through the real loader into an in-memory database.
 * A statement the loader can't execute (for example one split across lines) fails here instead of on a device.
 */
@RunWith(AndroidJUnit4::class)
class SeedDatabaseCallbackTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback(ApplicationProvider.getApplicationContext(), executor = Runnable::run))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seed_loadsEveryBrandAndCard() = runTest {
        assertThat(db.brandDao().getAll().first()).hasSize(5)
        // The JOIN drops a card whose brand is missing, so this also checks every card's brandId.
        assertThat(db.loyaltyCardDao().getAllWithBrand().first()).hasSize(11)
    }

    @Test
    fun seed_rowsHaveTheScriptedValues() = runTest {
        assertThat(db.loyaltyCardDao().getByIdWithBrand(5).first()).isEqualTo(
            LoyaltyCardWithBrand(
                LoyaltyCardEntity(id = 5, name = "Coffee Bean Club", brandId = 2, code = "678901234", barcodeFormat = "QR_CODE"),
                BrandEntity(id = 2, name = "Brand B", color = "#00FF00", logo = "https://picsum.photos/seed/2/200"),
            )
        )
    }
}
