package com.bdaniel.loyscan.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.dao.LoyaltyCardWithStore
import com.bdaniel.loyscan.data.local.db.entity.LoyaltyCardEntity
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** The debug-only demo data, seeded into a new in-memory database next to the real catalogue loader. */
@RunWith(AndroidJUnit4::class)
class DemoSeedCallbackTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(CatalogueDatabaseCallback.fromAssets(ApplicationProvider.getApplicationContext()))
            .addCallback(DemoSeedCallback())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seed_loadsEveryStoreAndCard() = runTest {
        assertThat(db.storeDao().getAll().first().filter { it.id.startsWith("demo-") }).hasSize(5)
        // Reading a card's store fails when its storeId names no store, so this also checks every storeId.
        assertThat(db.loyaltyCardDao().getAllWithStore().first()).hasSize(11)
    }

    @Test
    fun seed_rowsHaveTheScriptedValues() = runTest {
        val seededAt = Instant.parse("2026-01-01T00:00:00Z")
        assertThat(db.loyaltyCardDao().getByIdWithStore("00000000-0000-4000-8000-000000000005").first()).isEqualTo(
            LoyaltyCardWithStore(
                LoyaltyCardEntity(
                    id = "00000000-0000-4000-8000-000000000005",
                    storeId = "demo-b",
                    label = "Coffee Bean Club",
                    number = "678901234",
                    scannedFormat = BarcodeFormat.QR_CODE,
                    createdAt = seededAt,
                    lastUsedAt = null,
                    useCount = 0,
                    note = null,
                    frontPhotoPath = null,
                    backPhotoPath = null,
                    updatedAt = seededAt,
                ),
                StoreEntity(
                    id = "demo-b",
                    name = "Brand B",
                    color = "#00FF00",
                    logo = "https://picsum.photos/seed/2/200",
                    aliases = emptyList(),
                    countryCodes = listOf("HU"),
                    defaultBarcodeFormat = BarcodeFormat.CODE_128,
                    cardNumberRule = null,
                    isCustom = false,
                    updatedAt = seededAt,
                ),
            )
        )
    }
}
