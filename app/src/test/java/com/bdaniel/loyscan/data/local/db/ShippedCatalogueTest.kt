package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.domain.catalogue.StoreCatalogue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalogue that ships (`assets/catalogue/stores.json`): it loads, and each store's rule turns a made-up printed
 * number into the expected barcode content and display number. How to add a store: wiki/workflows/add-a-catalogue-store.md.
 */
@RunWith(AndroidJUnit4::class)
class ShippedCatalogueTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shipped = StoreCatalogue.parse(CatalogueDatabaseCallback.readShippedCatalogue(context))

    /** A made-up printed number per store with a rule; never a real person's card. */
    private data class Vector(val storeId: String, val printed: String, val barcodeContent: String, val display: String)

    private val vectors = listOf<Vector>()

    @Test
    fun shippedCatalogue_loadsIntoTheDatabase() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(CatalogueDatabaseCallback.fromAssets(context))
            .allowMainThreadQueries()
            .build()

        assertThat(db.storeDao().getAll().first().map { it.id }).containsExactlyElementsIn(shipped.map { it.id })
        db.close()
    }

    @Test
    fun everyStoreWithARule_hasATestVector() {
        assertThat(vectors.map { it.storeId })
            .containsExactlyElementsIn(shipped.filter { it.cardNumberRule != null }.map { it.id })
    }

    @Test
    fun eachRule_turnsThePrintedNumberIntoItsBarcode() {
        for (v in vectors) {
            val rule = shipped.single { it.id == v.storeId }.cardNumberRule
            assertWithMessage(v.storeId).that(CardNumberEngine.validate(v.printed, rule))
                .isInstanceOf(Validation.Valid::class.java)
            assertWithMessage(v.storeId).that(CardNumberEngine.barcodeContent(v.printed, rule)).isEqualTo(v.barcodeContent)
            assertWithMessage(v.storeId).that(CardNumberEngine.displayNumber(v.printed, rule)).isEqualTo(v.display)
            // A scan of that barcode reads back as the same store's card (the add-card flow's check, #6).
            assertWithMessage(v.storeId).that(CardNumberEngine.match(v.barcodeContent, rule!!.format, rule)?.core)
                .isNotNull()
        }
    }
}
