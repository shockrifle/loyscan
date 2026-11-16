package com.bdaniel.loyscan

import app.cash.turbine.test
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StorePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val first = aStore(id = "first", name = "First Store")
    private val second = aStore(id = "second", name = "Second Store", aliases = listOf("Other"))
    private val repository = FakeStoreRepository(listOf(second, first))

    @Test
    fun aPictureForALogo_putsThePickerInLogoMode_andAddingACardDoesNot() { // #151
        val repository = FakeStoreRepository(emptyList())

        assertThat(StorePickerViewModel(repository, StorePicker(logoPicture = "staged-1")) { "HU" }.logoMode).isTrue()
        assertThat(StorePickerViewModel(repository) { "HU" }.logoMode).isFalse()
    }

    @Test
    fun stores_areEveryStoreByName_andFollowTheRepository() = runTest {
        val viewModel = StorePickerViewModel(repository) { "HU" }

        viewModel.stores.test {
            assertThat(awaitItem()).containsExactly(first, second).inOrder()

            val third = aStore(id = "third", name = "Third Store")
            repository.stores.value += third
            assertThat(awaitItem()).containsExactly(first, second, third).inOrder()
        }
    }

    @Test
    fun query_narrowsTheStores_andClearingItShowsThemAll() = runTest {
        val viewModel = StorePickerViewModel(repository) { "HU" }

        viewModel.stores.test {
            skipItems(1)

            viewModel.onQueryChange("other")
            assertThat(viewModel.query.value).isEqualTo("other")
            assertThat(awaitItem()).containsExactly(second)

            viewModel.onQueryChange("")
            assertThat(awaitItem()).containsExactly(first, second).inOrder()
        }
    }

    @Test
    fun frequentlyAdded_areTheFlaggedStoresOfTheUsersCountry_byName() = runTest { // #6
        val tesco = aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true)
        val auchan = aStore(id = "hu-auchan", name = "Auchan", frequentlyAdded = true)
        val notFlagged = aStore(id = "hu-libri", name = "Libri")
        val elsewhere = aStore(id = "at-billa", name = "Billa", countryCodes = listOf("AT"), frequentlyAdded = true)
        val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(tesco, notFlagged, elsewhere, auchan))) { "HU" }

        viewModel.frequentlyAdded.test {
            assertThat(expectMostRecentItem()).containsExactly(auchan, tesco).inOrder()
        }
    }

    @Test
    fun aSearch_hidesTheFrequentlyAddedStores_andClearingItShowsThemAgain() = runTest { // #6
        val tesco = aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true)
        val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(tesco))) { "HU" }

        viewModel.frequentlyAdded.test {
            assertThat(expectMostRecentItem()).containsExactly(tesco)

            viewModel.onQueryChange("tes")
            assertThat(awaitItem()).isEmpty()

            viewModel.onQueryChange(" ")
            assertThat(awaitItem()).containsExactly(tesco)
        }
    }

    @Test
    fun anUnknownCountry_offersNoFrequentlyAddedStores() = runTest { // #6
        val tesco = aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true)
        val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(tesco))) { null }

        viewModel.frequentlyAdded.test {
            assertThat(expectMostRecentItem()).isEmpty()
        }
    }

    @Test
    fun withASharedScan_suggested_areTheStoresWhoseRuleReadsIt_rankedAsTheCardFormRanksThem() = runTest { // #77
        val zeta = aStore(id = "zeta", name = "Zeta", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128))
        val alpha = aStore(id = "alpha", name = "Alpha", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128))
        val gs1 = aStore(id = "gs1", name = "Gs1", cardNumberRule = luhn8Rule(BarcodeFormat.GS1_128))
        val qr = aStore(id = "qr", name = "A Qr", cardNumberRule = luhn8Rule(BarcodeFormat.QR_CODE))
        val otherStart = aStore(id = "other", name = "Other", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128, "9"))
        val noRule = aStore(id = "none", name = "No Rule")
        val stores = FakeStoreRepository(listOf(zeta, noRule, qr, otherStart, gs1, alpha))

        val viewModel = StorePickerViewModel(stores, StorePicker("12345674", BarcodeFormat.CODE_128)) { "HU" }

        viewModel.suggested.test {
            assertThat(expectMostRecentItem()).containsExactly(alpha, zeta, gs1, qr).inOrder()
        }
    }

    @Test
    fun withoutASharedScan_nothingIsSuggested() = runTest { // #77
        val store = aStore(id = "alpha", name = "Alpha", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128))

        for (route in listOf(StorePicker(), StorePicker(manualEntry = true))) {
            val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(store)), route) { "HU" }

            viewModel.suggested.test {
                assertThat(expectMostRecentItem()).isEmpty()
            }
        }
    }

    @Test
    fun aSharedScanNoRuleReads_suggestsNothing() = runTest { // #77
        val store = aStore(id = "alpha", name = "Alpha", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128, "9"))

        val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(store)), StorePicker("12345674", BarcodeFormat.CODE_128)) { "HU" }

        viewModel.suggested.test {
            assertThat(expectMostRecentItem()).isEmpty()
        }
    }

    @Test
    fun aSearch_hidesTheSuggestions_andClearingItShowsThemAgain() = runTest { // #77
        val store = aStore(id = "alpha", name = "Alpha", cardNumberRule = luhn8Rule(BarcodeFormat.CODE_128))
        val viewModel = StorePickerViewModel(FakeStoreRepository(listOf(store)), StorePicker("12345674", BarcodeFormat.CODE_128)) { "HU" }

        viewModel.suggested.test {
            assertThat(expectMostRecentItem()).containsExactly(store)

            viewModel.onQueryChange("alp")
            assertThat(awaitItem()).isEmpty()

            viewModel.onQueryChange("")
            assertThat(awaitItem()).containsExactly(store)
        }
    }
}

/** Eight digits with a Luhn check digit in [format], optionally starting with one of [starts]. */
internal fun luhn8Rule(format: BarcodeFormat, vararg starts: String) = CardNumberRule(
    id = "luhn8",
    format = format,
    minLength = 8,
    maxLength = 8,
    checkDigit = CardNumberRule.CheckDigit(CheckDigitAlgorithm.LUHN, CardNumberRule.CheckDigit.Mode.VERIFY),
    coreStartsWith = starts.toList(),
)
