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
    // The same rule, but the number must start with 9; for GS1-128; and for QR codes.
    private val nineStore = aStore(id = "nine", name = "Nine Store", cardNumberRule = rule.copy(coreStartsWith = listOf("9")))
    private val gs1Store = aStore(id = "gs1", name = "Gs1 Store", cardNumberRule = rule.copy(format = BarcodeFormat.GS1_128))
    private val qrStore = aStore(id = "qr", name = "A QR Store", cardNumberRule = rule.copy(format = BarcodeFormat.QR_CODE))
    private val eanStore = aStore(id = "ean", name = "Ean Store", defaultBarcodeFormat = BarcodeFormat.EAN_13)
    private val stores = FakeStoreRepository(listOf(store, plainStore, nineStore, gs1Store, qrStore, eanStore))
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
    fun aTypedNumberTheFormatOfAStoreWithoutARuleCantHold_saysSo_andCanStillBeSavedAnyway() = runTest { // #47 review
        val viewModel = viewModel(CardForm("ean"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("5901234123458") // wrong EAN-13 check digit

            val state = awaitItem()
            assertThat(state.check).isEqualTo(NumberCheck.DoesNotFitFormat(BarcodeFormat.EAN_13))
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun aTypedNumberTheFormatOfAStoreWithoutARuleHolds_isValid() = runTest {
        val viewModel = viewModel(CardForm("ean"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("5901234123457")

            assertThat(awaitItem().check).isEqualTo(NumberCheck.Valid)
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
    fun savingAnEditedScan_storesTheTypedNumber_withoutTheScannedFormat() = runTest {
        val viewModel = viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.QR_CODE))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("1234 5682")
            viewModel.save()

            val saved = cards.cards.value.single()
            assertThat(saved.number).isEqualTo("1234 5682")
            assertThat(saved.scannedFormat).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aScanShownDifferentlyFromItsContent_cantBeEdited_soItsRenderingNeverBecomesTheNumber() = runTest {
        // A GS1 scan for a store without a rule is shown as printed, (01)…(10)…; that text isn't the barcode content.
        val viewModel = viewModel(CardForm("plain", scannedContent = "0109501101530003\u001D10ABC", scannedFormat = BarcodeFormat.GS1_128))

        viewModel.state.test {
            val state = awaitLoaded()
            assertThat(state.number).isEqualTo("(01)09501101530003(10)ABC")
            assertThat(state.numberEditable).isFalse()

            viewModel.onNumberChange("(01)09501101530003(10)ABD")
            viewModel.save()

            assertThat(cards.cards.value.single().number).isEqualTo("0109501101530003\u001D10ABC")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun typingInsteadOfAScan_clearsTheNumber_andMakesItTyped() = runTest {
        val viewModel = viewModel(CardForm("plain", scannedContent = "0109501101530003\u001D10ABC", scannedFormat = BarcodeFormat.GS1_128))

        viewModel.state.test {
            awaitLoaded()
            viewModel.typeNumberInstead()

            val state = expectMostRecentItem()
            assertThat(state.number).isEmpty()
            assertThat(state.numberEditable).isTrue()
            assertThat(state.scannedFormat).isNull()
            assertThat(state.check).isEqualTo(NumberCheck.Empty)
        }
    }

    @Test
    fun aScanTheRuleReadsBack_orShownAsScanned_canBeEdited() = runTest {
        viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            assertThat(awaitLoaded().numberEditable).isTrue()
        }
        viewModel(CardForm("plain", scannedContent = "ABC-123", scannedFormat = BarcodeFormat.QR_CODE)).state.test {
            assertThat(awaitLoaded().numberEditable).isTrue()
        }
    }

    @Test
    fun theLabelAndAnEditedScan_areRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle(route = CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.QR_CODE))
        viewModel(handle).run {
            onLabelChange("Gold card")
            onNumberChange("1234")
        }
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        viewModel(restored).state.test {
            val state = awaitLoaded()
            assertThat(state.label).isEqualTo("Gold card")
            assertThat(state.number).isEqualTo("1234")
            assertThat(state.scannedFormat).isNull()
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
    fun aFailedSave_keepsTheFormOpen_saysSo_andCanBeSavedAgain() = runTest { // #49
        val viewModel = viewModel(CardForm("plain", scannedContent = "123", scannedFormat = BarcodeFormat.QR_CODE))
        cards.addFailure = IllegalStateException("insert failed")

        viewModel.state.test {
            awaitLoaded()
            viewModel.save()

            val failed = expectMostRecentItem()
            assertThat(failed.saveFailed).isTrue()
            assertThat(failed.savedCardId).isNull()
            assertThat(failed.canSave).isTrue()

            cards.addFailure = null
            viewModel.save()

            val saved = expectMostRecentItem()
            assertThat(saved.saveFailed).isFalse()
            assertThat(saved.savedCardId).isEqualTo(cards.cards.value.single().id)
        }
    }

    @Test
    fun aScanThePickedStoresRuleRejects_suggestsEveryStoreWhoseRuleReadsIt_exactFormatFirst() = runTest { // #6
        viewModel(CardForm("nine", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.ScanDoesNotMatchStore)
            assertThat(state.suggestedStores).containsExactly(store, gs1Store, qrStore).inOrder()
        }
    }

    @Test
    fun aScanThePickedStoreReadsInItsExactFormat_suggestsNothing() = runTest {
        viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            assertThat(awaitLoaded().suggestedStores).isEmpty()
        }
    }

    @Test
    fun aScanThePickedStoreReadsOnlyByItsSymbology_suggestsTheStoresWhoseRuleNamesTheExactFormat() = runTest {
        // A GS1-128 scan: the picked Code 128 rule reads it, but the GS1-128 store's rule names its exact format.
        viewModel(CardForm("ruled", scannedContent = "12345674", scannedFormat = BarcodeFormat.GS1_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.suggestedStores).containsExactly(gs1Store)
        }
    }

    @Test
    fun aScanWithoutTheGs1MarkThePickedGs1RuleReads_suggestsNothing() = runTest { // review of #6, #47
        // A plain Code 128 read of a GS1-128 store's card: another store's Code 128 rule reading it isn't a better match.
        viewModel(CardForm("gs1", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.suggestedStores).isEmpty()
        }
    }

    @Test
    fun aScanThePickedRuleReadsInAnotherSymbology_suggestsNothing() = runTest { // review of #6
        viewModel(CardForm("qr", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.suggestedStores).isEmpty()
        }
    }

    @Test
    fun aScanForAStoreWithoutARule_suggestsEveryStoreWhoseRuleReadsIt() = runTest {
        viewModel(CardForm("plain", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128)).state.test {
            val state = awaitLoaded()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.suggestedStores).containsExactly(store, gs1Store, qrStore).inOrder()
        }
    }

    @Test
    fun aTypedNumber_suggestsNothing() = runTest {
        val viewModel = viewModel(CardForm("nine"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")

            val state = awaitItem()
            assertThat(state.check).isEqualTo(NumberCheck.Invalid(setOf(Validation.Error.WRONG_START)))
            assertThat(state.suggestedStores).isEmpty()
        }
    }

    @Test
    fun switchingToASuggestedStore_keepsTheScanAndTheLabel_andSavesTheCardForThatStore() = runTest {
        val viewModel = viewModel(CardForm("nine", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("Mine")
            viewModel.switchStore("ruled")

            val state = expectMostRecentItem()
            assertThat(state.store).isEqualTo(store)
            assertThat(state.label).isEqualTo("Mine")
            assertThat(state.number).isEqualTo("1234 5674")
            assertThat(state.scannedFormat).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.suggestedStores).isEmpty()

            viewModel.save()
            expectMostRecentItem()
            val saved = cards.cards.value.single()
            assertThat(saved.store.id).isEqualTo("ruled")
            assertThat(saved.number).isEqualTo("12345674")
            assertThat(saved.scannedFormat).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(saved.label).isEqualTo("Mine")
        }
    }

    @Test
    fun aSwitchedStore_isRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle(route = CardForm("nine", scannedContent = "12345674", scannedFormat = BarcodeFormat.CODE_128))
        viewModel(handle).switchStore("ruled")

        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        viewModel(restored).state.test {
            assertThat(awaitLoaded().store).isEqualTo(store)
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

    private fun viewModel(route: CardForm) = viewModel(SavedStateHandle(route = route))

    private fun viewModel(handle: SavedStateHandle) =
        CardFormViewModel(handle, stores, cards, Clock.fixed(NOW, ZoneOffset.UTC))

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
