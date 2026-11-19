package com.bdaniel.loyscan.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.mapper.toDomainModel
import com.bdaniel.loyscan.data.mapper.toStoreEntity
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import com.bdaniel.loyscan.domain.cardnumber.RuleMatch
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.domain.catalogue.StoreCatalogue
import com.bdaniel.loyscan.domain.matchingScan
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

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

    private val vectors = listOf(
        Vector("hu-brendon", "16712348", "16712348", "16712348"),
        Vector("hu-coop", "6028532123456789", "906028532123456789", "6028 5321 2345 6789"),
        Vector("hu-decathlon", "2090000123456", "2090000123456", "2090000123456"),
        Vector("hu-dm", "2410000123452", "2410000123452", "2 410000 123452"),
        Vector("hu-auchan", "2097000123455", "2097000123455", "2097000123455"),
        Vector("hu-douglas", "60088400012345675", "60088400012345675", "60088400012345675"),
        Vector("hu-ikea-family", "6275980421234567893", "6275980421234567893", "627598 042 123 456 7893"),
        Vector("hu-kik", "98512345678901", "98512345678901", "98512345678901"),
        Vector("hu-libri", "2020000123453", "2020000123453", "2020000123453"),
        Vector("hu-lidl-plus", "77360001234567890", "77360001234567890", "77360001234567890"),
        Vector("hu-lira", "1234567", "0000012345670", "1234567"),
        Vector("hu-moebelix", "1121234567", "1121234567", "1121234567"),
        Vector("hu-mol-move", "6369000012345676", "6369000012345676", "6369 0000 1234 5676"),
        Vector("hu-momax", "3121234567", "3121234567", "3121234567"),
        Vector("hu-penny", "9940000123457", "9940000123457", "9940000123457"),
        Vector("hu-praktiker", "3601234567", "3601234567", "360 123 4567"),
        Vector("hu-rossmann", "9991234567890", "9991234567890", "9991234567890"),
        Vector("hu-shell-clubsmart", "70043400012345678", "70043400012345678", "7004 3400 0123 4567 8"),
        Vector("hu-supershop", "2000010112345678", "2000010112345678", "2000 0101 1234 5678"),
        Vector("hu-szerencsejatek", "400012345678901", "400012345678901", "400012345678901"),
        Vector("hu-tesco", "634009123456789012", "9799123456789012", "634009 1234 5678 9012"),
        Vector("hu-unix-auto", "H123456", "H123456", "H 123 456"),
    )

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
    fun theFrequentlyAddedHungarianStores_areTheEditorialPick() { // #6: groceries and drugstores, 2026-10-30; Lidl Plus added once it shipped
        val frequentlyAdded = shipped.filter { it.frequentlyAdded && "HU" in it.countryCodes }.map { it.id }

        assertThat(frequentlyAdded)
            .containsExactly("hu-auchan", "hu-coop", "hu-dm", "hu-lidl-plus", "hu-penny", "hu-rossmann", "hu-tesco")
    }

    @Test
    fun everyShippedStore_isAHungarianStore() { // #178: the Play listing offers "the built-in list of Hungarian stores"
        assertThat(shipped.filterNot { "HU" in it.countryCodes }.map { it.id }).isEmpty()
    }

    @Test
    fun mostShippedStores_haveANumberRule() { // #178: the Play listing says "for most listed stores" the number is checked
        val withARule = shipped.count { it.cardNumberRule != null }

        assertWithMessage("$withARule of ${shipped.size} stores have a rule").that(withARule * 2).isGreaterThan(shipped.size)
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
            // A scan of that barcode reads back as the same store's card (the add-card flow's check, #6), with the
            // printed number.
            assertWithMessage(v.storeId).that(CardNumberEngine.match(v.barcodeContent, rule!!.format, rule)?.core)
                .isEqualTo(v.printed)
        }
    }

    /** #43: the barcode leaves out the first six printed digits, and a scan puts them back. */
    @Test
    fun tescoScan_matchesWithThePrintedNumber_whetherReadAsGs1_128OrPlainCode128() {
        val rule = shipped.single { it.id == "hu-tesco" }.cardNumberRule!!

        assertThat(rule.format).isEqualTo(BarcodeFormat.GS1_128)
        assertThat(CardNumberEngine.match("9799123456789012", BarcodeFormat.GS1_128, rule))
            .isEqualTo(RuleMatch("634009123456789012", formatMatches = true))
        assertThat(CardNumberEngine.match("9799123456789012", BarcodeFormat.CODE_128, rule))
            .isEqualTo(RuleMatch("634009123456789012", formatMatches = true)) // same symbology (#47)
    }

    /** A Tesco GS1-128 barcode is 16 digits; SuperShop's rule, also 16 digits, requires SuperShop's start (#52). */
    @Test
    fun aTescoScan_isNotOfferedAsSuperShop() {
        val stores = shipped.map { it.toStoreEntity(Instant.EPOCH).toDomainModel() }

        val matching = stores.matchingScan("9799123456789012", BarcodeFormat.GS1_128).map { it.id }

        assertThat(matching.first()).isEqualTo("hu-tesco")
        assertThat(matching).doesNotContain("hu-supershop")
    }

    /** The stores found on real cards in #44 ship; Ofotért without a rule (its barcode drops digits from the middle). */
    @Test
    fun theStoresFoundOnRealCards_ship() {
        val found = listOf("hu-brendon", "hu-kik", "hu-lidl-plus", "hu-lira", "hu-moebelix", "hu-ofotert", "hu-unix-auto")

        assertThat(shipped.map { it.id }).containsAtLeastElementsIn(found)
    }

    /** SuperShop's own app shows the card as a QR code (#31, 2026-10-30); its old chip cards have no barcode. */
    @Test
    fun superShop_isDrawnAsAQrCode() {
        val superShop = shipped.single { it.id == "hu-supershop" }

        assertThat(superShop.defaultBarcodeFormat).isEqualTo(BarcodeFormat.QR_CODE)
        assertThat(superShop.cardNumberRule!!.format).isEqualTo(BarcodeFormat.QR_CODE)
    }

    /** Two SuperShop cards pass Luhn (#31, 2026-11-02), so a mistyped digit is caught. */
    @Test
    fun superShop_verifiesALuhnCheckDigit() {
        val rule = shipped.single { it.id == "hu-supershop" }.cardNumberRule

        assertThat(CardNumberEngine.validate("2000010112345679", rule))
            .isEqualTo(Validation.Invalid("2000010112345679", setOf(Validation.Error.BAD_CHECK_DIGIT)))
    }

    /** A digital SuperShop card starts `20170001`, not the `20000101` the FAQ names for plastic cards (#31). */
    @Test
    fun aDigitalSuperShopCard_isValid() {
        val rule = shipped.single { it.id == "hu-supershop" }.cardNumberRule

        assertThat(CardNumberEngine.validate("2017000112345670", rule)).isEqualTo(Validation.Valid("2017000112345670"))
    }

    /** Auchan's and Decathlon's EAN-13 numbers both start `209`; a scan of one isn't offered as the other (#52). */
    @Test
    fun anAuchanScan_isNotOfferedAsDecathlon() {
        val stores = shipped.map { it.toStoreEntity(Instant.EPOCH).toDomainModel() }

        val matching = stores.matchingScan("2097000123455", BarcodeFormat.EAN_13).map { it.id }

        assertThat(matching).containsExactly("hu-auchan")
    }

    /** Every rule requires its confirmed leading digits (real card or retailer, #31), so the rules tell scans apart. */
    @Test
    fun storesWithConfirmedLeadingDigits_rejectOtherStoresCards() {
        val confirmed = vectors.map { it.storeId }.toSet() - "hu-coop" // Coop's barcode prefix `90` sets it apart
        for (id in confirmed) {
            val rule = shipped.single { it.id == id }.cardNumberRule!!
            for (other in vectors.filter { it.storeId != id }) {
                assertWithMessage("$id matched ${other.storeId}")
                    .that(CardNumberEngine.match(other.barcodeContent, rule.format, rule)).isNull()
            }
        }
    }
}
