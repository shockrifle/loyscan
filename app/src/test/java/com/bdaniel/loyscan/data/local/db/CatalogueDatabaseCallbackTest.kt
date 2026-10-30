package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.local.db.entity.StoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aLoyaltyCardEntity
import com.bdaniel.loyscan.testing.aStoreEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The catalogue loader against an on-disk database that is closed and reopened, as after an app update that ships
 * a changed catalogue.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueDatabaseCallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun firstOpen_insertsEveryCatalogueStore() = runTest {
        val db = open(catalogue(entry("hu-one", name = "One"), entry("hu-two", name = "Two")), at = FIRST)

        assertThat(db.storeDao().getAll().first()).containsExactly(
            store("hu-one", name = "One", updatedAt = FIRST),
            store("hu-two", name = "Two", updatedAt = FIRST),
        )
    }

    @Test
    fun reopen_changedEntry_isRewrittenBeforeItIsRead_andKeepsItsCards() = runTest {
        open(catalogue(entry("hu-one", rule = """{ "id": "hu-one", "format": "CODE_128", "prefix": "1" }""")), at = FIRST)
            .loyaltyCardDao().insert(aLoyaltyCardEntity(storeId = "hu-one"))
        closeAll()

        val db = open(
            catalogue(entry("hu-one", name = "Renamed", rule = """{ "id": "hu-one", "format": "CODE_39", "prefix": "2" }""")),
            at = SECOND,
        )

        val card = db.loyaltyCardDao().getAllWithStore().first().single()
        assertThat(card.store).isEqualTo(
            store(
                "hu-one",
                name = "Renamed",
                rule = CardNumberRule(id = "hu-one", format = BarcodeFormat.CODE_39, prefix = "2"),
                updatedAt = SECOND,
            )
        )
    }

    @Test
    fun reopen_anEntryNowFrequentlyAdded_isRewritten() = runTest { // #6
        open(catalogue(entry("hu-one")), at = FIRST).storeDao().getAll().first()
        closeAll()

        val db = open(catalogue(entry("hu-one", frequentlyAdded = true)), at = SECOND)

        assertThat(db.storeDao().getAll().first()).containsExactly(store("hu-one", frequentlyAdded = true, updatedAt = SECOND))
    }

    @Test
    fun reopen_storedRuleThatNoLongerParses_isRewrittenBeforeItIsRead() = runTest {
        // A rule stored by an older app version that today's CardNumberRule rejects (strict decoding throws on read).
        open(catalogue(entry("hu-one")), at = FIRST).openHelper.writableDatabase
            .execSQL("""UPDATE stores SET cardNumberRule = '{"id":"hu-one","format":"EAN_14"}' WHERE id = 'hu-one'""")
        closeAll()

        val db = open(catalogue(entry("hu-one", rule = """{ "id": "hu-one", "format": "QR_CODE" }""")), at = SECOND)

        assertThat(db.storeDao().getById("hu-one").first()?.cardNumberRule)
            .isEqualTo(CardNumberRule(id = "hu-one", format = BarcodeFormat.QR_CODE))
    }

    @Test
    fun reopen_unchangedEntry_keepsItsUpdatedAt() = runTest {
        // Room opens lazily: the query makes this first open happen.
        open(catalogue(entry("hu-one", rule = """{ "id": "hu-one", "format": "CODE_128" }""")), at = FIRST)
            .storeDao().getAll().first()
        closeAll()

        val db = open(catalogue(entry("hu-one", rule = """{ "id": "hu-one", "format": "CODE_128" }""")), at = SECOND)

        assertThat(db.storeDao().getById("hu-one").first()?.updatedAt).isEqualTo(FIRST)
    }

    @Test
    fun reopen_storesNotInTheCatalogue_areLeftAsTheyAre() = runTest {
        val custom = aStoreEntity(id = "00000000-0000-4000-8000-00000000abcd", isCustom = true, updatedAt = FIRST)
        open(catalogue(entry("hu-one"), entry("hu-dropped")), at = FIRST).storeDao().insert(custom)
        closeAll()

        val db = open(catalogue(entry("hu-one")), at = SECOND)

        assertThat(db.storeDao().getAll().first()).containsExactly(
            store("hu-one", updatedAt = FIRST),
            store("hu-dropped", updatedAt = FIRST),
            custom,
        )
    }

    @Test
    fun invalidCatalogue_failsTheFirstQuery_andWritesNothing() = runTest {
        val db = open(catalogue(entry("hu-one"), entry("hu-one")), at = FIRST)

        assertThrows(IllegalArgumentException::class.java) { db.openHelper.writableDatabase }
        db.close()
        val reopened = open(catalogue(), at = SECOND)
        assertThat(reopened.storeDao().getAll().first()).isEmpty()
    }

    private fun open(catalogueJson: String, at: Instant): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addCallback(CatalogueDatabaseCallback({ catalogueJson }, Clock.fixed(at, ZoneOffset.UTC)))
            .allowMainThreadQueries()
            .build()
            .also(opened::add)

    private fun closeAll() {
        opened.forEach { it.close() }
        opened.clear()
    }

    private fun catalogue(vararg entries: String) = entries.joinToString(",", "[", "]")

    private fun entry(id: String, name: String = "Store", rule: String = "null", frequentlyAdded: Boolean = false) =
        """{ "id": "$id", "name": "$name", "aliases": ["alias"], "countryCodes": ["HU"], "color": "#102030",
             "defaultBarcodeFormat": "EAN_13", "cardNumberRule": $rule, "frequentlyAdded": $frequentlyAdded }"""

    private fun store(
        id: String,
        name: String = "Store",
        rule: CardNumberRule? = null,
        frequentlyAdded: Boolean = false,
        updatedAt: Instant,
    ) =
        StoreEntity(
            id = id,
            name = name,
            color = "#102030",
            logo = null,
            aliases = listOf("alias"),
            countryCodes = listOf("HU"),
            defaultBarcodeFormat = BarcodeFormat.EAN_13,
            cardNumberRule = rule,
            frequentlyAdded = frequentlyAdded,
            isCustom = false,
            updatedAt = updatedAt,
        )

    private companion object {
        const val DB_NAME = "catalogue-test.db"
        val FIRST: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val SECOND: Instant = Instant.parse("2026-02-01T00:00:00Z")
    }
}
