package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CardFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Example rule: 8 digits shown in two groups, the last one a Luhn check digit (12345674 is valid).
    private val rule = CardNumberRule(
        id = "example",
        format = BarcodeFormat.CODE_128,
        minLength = 8,
        maxLength = 8,
        checkDigit = CardNumberRule.CheckDigit(CheckDigitAlgorithm.LUHN, CardNumberRule.CheckDigit.Mode.VERIFY),
        display = CardNumberRule.Display(pattern = "#### ####"),
    )
    private val store = aStore(id = "ruled", name = "Ruled Store", color = "#112233", cardNumberRule = rule)
    private val plainStore = aStore(id = "plain", name = "Plain Store", defaultBarcodeFormat = BarcodeFormat.QR_CODE)
    private val stores = FakeStoreRepository(listOf(store, plainStore))
    private val cards = FakeLoyaltyCardRepository()

    @Test
    fun typedForm_startsEmpty_andCantBeSaved() = runTest {
        viewModel(CardForm("ruled")).state.test {
            val state = awaitLoaded()
            assertThat(state.store).isEqualTo(store)
            assertThat(state.number).isEmpty()
            assertThat(state.check).isEqualTo(NumberCheck.Empty)
            assertThat(state.canSave).isFalse()
        }
    }

    @Test
    fun aValidNumber_canBeSaved_andIsShownFormattedByTheRule() = runTest {
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")

            val state = awaitItem()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.canSave).isTrue()
            assertThat(state.shownAs).isEqualTo("1234 5674")
            assertThat(state.barcodeFormat).isEqualTo(BarcodeFormat.CODE_128)
        }
    }

    @Test
    fun anInvalidNumber_saysWhy_andCanStillBeSavedAnyway() = runTest {
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345675")
            assertThat(awaitItem().check).isEqualTo(NumberCheck.Invalid(setOf(Validation.Error.BAD_CHECK_DIGIT)))

            viewModel.onNumberChange("123")
            val state = awaitItem()
            assertThat(state.check).isEqualTo(NumberCheck.Invalid(setOf(Validation.Error.TOO_SHORT)))
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun aNumberAlreadyInTheRulesFormat_isNotShownTwice() = runTest {
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("1234 5674")

            assertThat(awaitItem().shownAs).isNull()
        }
    }

    @Test
    fun aScan_isShownAsTheStoresNumber_andKeepsItsFormat() = runTest {
        viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.number).isEqualTo("1234 5674")
            assertThat(state.scannedFormat).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun aScanTheStoresRuleDoesNotMatch_isFlagged_andCanStillBeSavedAnyway() = runTest {
        viewModel(CardForm("ruled", scannedContent = "ABC-123", scannedFormat = BarcodeFormat.QR_CODE)).state.test {
            val state = awaitLoaded()
            assertThat(state.number).isEqualTo("ABC-123")
            assertThat(state.check).isEqualTo(NumberCheck.ScanDoesNotMatchStore)
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun aGs1ScanOfARulesNumber_isNotFlaggedForItsFormat() = runTest { // #47
        viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.GS1_128)).state.test {
            assertThat(awaitLoaded().check).isEqualTo(NumberCheck.Valid)
        }
    }

    @Test
    fun aScanForAStoreWithoutARule_isValid() = runTest {
        viewModel(CardForm("plain", scannedContent = "anything", scannedFormat = BarcodeFormat.AZTEC)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.barcodeFormat).isEqualTo(BarcodeFormat.AZTEC)
        }
    }

    @Test
    fun editingAScannedNumber_makesItATypedNumber() = runTest {
        val viewModel = viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.QR_CODE))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("1234 567")

            val state = awaitItem()
            assertThat(state.number).isEqualTo("1234 567")
            assertThat(state.scannedFormat).isNull()
            assertThat(state.check).isEqualTo(NumberCheck.Invalid(setOf(Validation.Error.TOO_SHORT)))
            assertThat(state.barcodeFormat).isEqualTo(BarcodeFormat.CODE_128)
        }
    }

    @Test
    fun savingATypedNumber_storesANewCard_andReportsItsId() = runTest {
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("  Gold card ")
            viewModel.onNumberChange(" 1234 5674 ")
            viewModel.save()

            val saved = cards.cards.value.single()
            assertThat(saved).isEqualTo(
                LoyaltyCard.EMPTY.copy(
                    id = saved.id,
                    store = store,
                    label = "Gold card",
                    number = "1234 5674",
                    createdAt = NOW,
                    updatedAt = NOW,
                )
            )
            assertThat(expectMostRecentItem().savedCardId).isEqualTo(saved.id)
        }
    }

    @Test
    fun savingAnUntouchedScan_storesTheScannedContentVerbatim_withItsFormat() = runTest {
        val viewModel = viewModel(CardForm("plain", scannedContent = "10ABC\u001D97", scannedFormat = BarcodeFormat.GS1_128))

        viewModel.state.test {
            awaitLoaded()
            viewModel.save()

            val saved = cards.cards.value.single()
            assertThat(saved.number).isEqualTo("10ABC\u001D97")
            assertThat(saved.scannedFormat).isEqualTo(BarcodeFormat.GS1_128)
            assertThat(saved.label).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingTwice_storesOneCard() = runTest {
        val viewModel = viewModel(CardForm("plain", scannedContent = "123", scannedFormat = BarcodeFormat.QR_CODE))

        viewModel.state.test {
            awaitLoaded()
            viewModel.save()
            viewModel.save()

            assertThat(cards.cards.value).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnknownStore_cantBeSaved() = runTest {
        val viewModel = viewModel(CardForm("missing", scannedContent = "123", scannedFormat = BarcodeFormat.QR_CODE))

        viewModel.state.test {
            assertThat(awaitItem().canSave).isFalse()
            viewModel.save()

            assertThat(cards.cards.value).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(route: CardForm) =
        CardFormViewModel(SavedStateHandle(route = route), stores, cards, Clock.fixed(NOW, ZoneOffset.UTC))

    /** The first state with the store loaded. */
    private suspend fun ReceiveTurbine<CardFormState>.awaitLoaded(): CardFormState {
        var state = awaitItem()
        while (state.store == null) state = awaitItem()
        return state
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-05T05:05:05Z")
    }
}
