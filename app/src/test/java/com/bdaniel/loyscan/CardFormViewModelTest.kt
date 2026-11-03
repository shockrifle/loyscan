package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.domain.cardnumber.CheckDigitAlgorithm
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.keep
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
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
    private val photos = FakeCardPhotos()

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
    fun aTypedNumberOneDigitShortOfAnEan13_isFlagged_notCompletedWithACheckDigit() = runTest { // #47 review, round 2
        val viewModel = viewModel(CardForm("ean"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("590123412345")

            assertThat(awaitItem().check).isEqualTo(NumberCheck.DoesNotFitFormat(BarcodeFormat.EAN_13))
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
    fun aScanShownDifferentlyFromItsContent_cantBeEdited_beforeTheStoresHaveLoaded() = runTest { // #50 review
        val lateStores = object : StoreRepository by stores {
            override fun getAll() = flow { delay(1); emitAll(stores.getAll()) } // as Room's, not on construction
        }
        val viewModel = CardFormViewModel(
            SavedStateHandle(route = CardForm("plain", scannedContent = "0109501101530003\u001D10ABC", scannedFormat = BarcodeFormat.GS1_128)),
            lateStores, cards, Clock.fixed(NOW, ZoneOffset.UTC), photos,
        )

        viewModel.onNumberChange("(01)09501101530003(10)ABD")

        viewModel.state.test {
            awaitLoaded()
            viewModel.save()

            assertThat(cards.cards.value.single().number).isEqualTo("0109501101530003\u001D10ABC")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aNumberTypedInsteadOfAScan_canBeEdited_afterTheScreenStoppedObservingTheForm() = runTest { // #50 review
        val viewModel = viewModel(CardForm("plain", scannedContent = "0109501101530003\u001D10ABC", scannedFormat = BarcodeFormat.GS1_128))
        viewModel.state.test { awaitLoaded(); cancelAndIgnoreRemainingEvents() }
        advanceTimeBy(10_000)

        viewModel.typeNumberInstead()
        viewModel.onNumberChange("12345")

        assertThat(viewModel.state.value.number).isEqualTo("12345")
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

    @Test
    fun aStoreRemovedWhileTheFormIsOpen_saysSo_andCantBeSaved() = runTest { // #51
        val viewModel = viewModel(CardForm("plain"))

        viewModel.state.test {
            assertThat(awaitItem().storeGone).isFalse() // still loading
            viewModel.onNumberChange("4242")
            assertThat(expectMostRecentItem().canSave).isTrue()

            stores.stores.value = stores.stores.value.filterNot { it.id == "plain" }
            val state = expectMostRecentItem()
            assertThat(state.storeGone).isTrue()
            assertThat(state.canSave).isFalse()
            viewModel.save()

            assertThat(cards.cards.value).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingATypedCard_startsWithItsStoreLabelNumberAndNote() = runTest { // #8, note: #9
        cards.cards.value = listOf(
            aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674", note = "Expires in May")
        )

        viewModel(CardForm("ruled", cardId = cardId(1))).state.test {
            val state = awaitLoaded()
            assertThat(state.editing).isTrue()
            assertThat(state.store).isEqualTo(store)
            assertThat(state.label).isEqualTo("Gold card")
            assertThat(state.note).isEqualTo("Expires in May")
            assertThat(state.number).isEqualTo("12345674")
            assertThat(state.scannedFormat).isNull()
            assertThat(state.check).isEqualTo(NumberCheck.Valid)
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun editingAScannedCard_startsAsItsUntouchedScan() = runTest { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", scannedFormat = BarcodeFormat.QR_CODE))

        viewModel(CardForm("ruled", cardId = cardId(1))).state.test {
            val state = awaitLoaded()
            assertThat(state.number).isEqualTo("1234 5674")
            assertThat(state.scannedFormat).isEqualTo(BarcodeFormat.QR_CODE)
            assertThat(state.label).isEmpty()
        }
    }

    @Test
    fun savingAnEdit_updatesTheCard_keepingItsIdCreationUseAndNote() = runTest { // #8
        val original = aLoyaltyCard(
            id = cardId(1),
            store = store,
            label = "Gold card",
            number = "12345674",
            createdAt = SOME_INSTANT,
            lastUsedAt = SOME_INSTANT,
            useCount = 3,
            note = "Expires in May",
            updatedAt = SOME_INSTANT,
        )
        cards.cards.value = listOf(original)
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("  ")
            viewModel.onNumberChange("1234 5682")
            viewModel.save()

            assertThat(cards.cards.value).containsExactly(original.copy(label = null, number = "1234 5682", updatedAt = NOW))
            assertThat(expectMostRecentItem().savedCardId).isEqualTo(cardId(1))
        }
    }

    @Test
    fun aNote_isSavedWithANewCard_trimmed() = runTest { // #9
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onNoteChange("  PIN hint: birthday\nGold tier \n")
            viewModel.save()

            assertThat(cards.cards.value.single().note).isEqualTo("PIN hint: birthday\nGold tier")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingTheNote_savesIt() = runTest { // #9
        val original = aLoyaltyCard(id = cardId(1), store = store, number = "12345674", note = "Expires in May")
        cards.cards.value = listOf(original)
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNoteChange("Expires in June")
            assertThat(awaitItem().note).isEqualTo("Expires in June")
            viewModel.save()

            assertThat(cards.cards.value).containsExactly(original.copy(note = "Expires in June", updatedAt = NOW))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aBlankNote_isSavedAsNull() = runTest { // #9
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", note = "Expires in May"))
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNoteChange(" \n ")
            viewModel.save()

            assertThat(cards.cards.value.single().note).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingAScannedCardsNumberByHand_savesItAsTyped_withoutItsScannedFormat() = runTest { // #8, data-model-v2
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", scannedFormat = BarcodeFormat.QR_CODE))
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

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
    fun savingAScannedCardWithOnlyItsLabelEdited_keepsItAsScanned() = runTest { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", scannedFormat = BarcodeFormat.QR_CODE))
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("Gold card")
            viewModel.save()

            val saved = cards.cards.value.single()
            assertThat(saved.label).isEqualTo("Gold card")
            assertThat(saved.number).isEqualTo("12345674")
            assertThat(saved.scannedFormat).isEqualTo(BarcodeFormat.QR_CODE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingACard_offersEveryOtherStore_byName() = runTest { // #8
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674"))

        viewModel(CardForm("ruled", cardId = cardId(1))).state.test {
            assertThat(awaitLoaded().otherStores.map { it.id }).containsExactly("qr", "ean", "gs1", "nine", "plain").inOrder()
        }
    }

    @Test
    fun addingACard_offersNoOtherStore() = runTest { // #8: back returns to the picker
        viewModel(CardForm("ruled")).state.test {
            assertThat(awaitLoaded().otherStores).isEmpty()
        }
    }

    @Test
    fun movingATypedCardToAnotherStore_checksItsNumberAgainstThatStoresRule_andSavesItThere() = runTest { // #8
        val original = aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674", useCount = 3)
        cards.cards.value = listOf(original)
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.switchStore("nine")

            val state = expectMostRecentItem()
            assertThat(state.store).isEqualTo(nineStore)
            assertThat(state.label).isEqualTo("Gold card")
            assertThat(state.number).isEqualTo("12345674")
            assertThat(state.check).isEqualTo(NumberCheck.Invalid(setOf(Validation.Error.WRONG_START)))
            assertThat(state.otherStores.map { it.id }).containsExactly("qr", "ean", "gs1", "plain", "ruled").inOrder()

            viewModel.save()
            assertThat(expectMostRecentItem().savedCardId).isEqualTo(cardId(1))
            assertThat(cards.cards.value).containsExactly(original.copy(store = nineStore, updatedAt = NOW))
        }
    }

    @Test
    fun movingAScannedCardToAStoreWhoseRuleRejectsIt_saysSo_offersItsStoreBack_andSavesTheScanAsItWas() = runTest { // #8 review
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", scannedFormat = BarcodeFormat.CODE_128))
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.switchStore("nine")

            val state = expectMostRecentItem()
            assertThat(state.check).isEqualTo(NumberCheck.ScanDoesNotMatchStore)
            assertThat(state.scannedFormat).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(state.suggestedStores).contains(store)

            viewModel.save()
            expectMostRecentItem()
            val saved = cards.cards.value.single()
            assertThat(saved.store.id).isEqualTo("nine")
            assertThat(saved.number).isEqualTo("12345674")
            assertThat(saved.scannedFormat).isEqualTo(BarcodeFormat.CODE_128)
        }
    }

    @Test
    fun theStoreCantBeSwitchedWhileTheCardIsSaving() = runTest { // #8 review: the card would open in the other store's colour
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674"))
        val gate = CompletableDeferred<Unit>()
        cards.updateGate = gate
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.save()
            viewModel.switchStore("nine")
            gate.complete(Unit)

            val state = expectMostRecentItem()
            assertThat(state.savedCardId).isEqualTo(cardId(1))
            assertThat(state.store).isEqualTo(store)
            viewModel.switchStore("nine") // nor once it is saved, while the card opens
            assertThat(viewModel.state.value.store).isEqualTo(store)
            assertThat(cards.cards.value.single().store).isEqualTo(store)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEdit_isRestoredAfterProcessDeath() = runTest { // #8, note: #9
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, label = "Gold card", number = "12345674"))
        val handle = SavedStateHandle(route = CardForm("ruled", cardId = cardId(1)))
        viewModel(handle).run {
            state.test {
                awaitLoaded()
                onLabelChange("")
                onNumberChange("1234")
                onNoteChange("Expires in June")
                cancelAndIgnoreRemainingEvents()
            }
        }
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

        viewModel(restored).state.test {
            val state = awaitLoaded()
            assertThat(state.label).isEmpty()
            assertThat(state.number).isEqualTo("1234")
            assertThat(state.note).isEqualTo("Expires in June")
        }
    }

    @Test
    fun editingACardThatIsGone_cantBeSaved() = runTest { // #8: deleted, or a stale route
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(99)))

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.store).isNull()
            assertThat(state.canSave).isFalse()
            viewModel.save()

            assertThat(cards.cards.value).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedEdit_keepsTheFormOpen_andSaysSo() = runTest { // #8, as a failed add (#49)
        val original = aLoyaltyCard(id = cardId(1), store = store, number = "12345674")
        cards.cards.value = listOf(original)
        cards.updateFailure = IllegalStateException("update failed")
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("Gold card")
            viewModel.save()

            val failed = expectMostRecentItem()
            assertThat(failed.saveFailed).isTrue()
            assertThat(failed.savedCardId).isNull()
            assertThat(cards.cards.value).containsExactly(original)
        }
    }

    @Test
    fun aSaveStillRunningWhenTheFormCloses_isStillSaved() = runTest { // #64: Back pressed during the save
        val gate = CompletableDeferred<Unit>()
        cards.addGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled")) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(cards.cards.value.single().number).isEqualTo("12345674")
    }

    @Test
    fun anEditStillSavingWhenTheFormCloses_isStillSaved() = runTest { // #64
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674"))
        val gate = CompletableDeferred<Unit>()
        cards.updateGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled", cardId = cardId(1))) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onLabelChange("Gold card")
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(cards.cards.value.single().label).isEqualTo("Gold card")
    }

    @Test
    fun aPickedFrontPhoto_isShown_andSavedWithTheNewCard() = runTest { // #10
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            assertThat(expectMostRecentItem().frontPhoto).isEqualTo("photo-1")

            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(photos.imported).containsExactly(PICKED)
        assertThat(cards.cards.value.single().frontPhotoPath).isEqualTo("photo-1")
        assertThat(cards.cards.value.single().backPhotoPath).isNull()
    }

    @Test
    fun editingACard_startsWithItsPhotos_andAPickedBackPhotoReplacesItsOwnOnSave() = runTest { // #10
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", frontPhotoPath = "front", backPhotoPath = "back"))
        photos.stored += setOf("front", "back")
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            val state = awaitLoaded()
            assertThat(state.frontPhoto).isEqualTo("front")
            assertThat(state.backPhoto).isEqualTo("back")

            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(cards.cards.value.single().frontPhotoPath).isEqualTo("front")
        assertThat(cards.cards.value.single().backPhotoPath).isEqualTo("photo-1")
        // The card's own back photo is the repository's to delete, once the edit is saved.
        assertThat(photos.stored).containsExactly("front", "back", "photo-1")
    }

    @Test
    fun removingACardsPhoto_savesTheCardWithoutIt() = runTest { // #10
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", frontPhotoPath = "front"))
        val viewModel = viewModel(CardForm("ruled", cardId = cardId(1)))

        viewModel.state.test {
            awaitLoaded()
            viewModel.removePhoto(CardPhotoSide.FRONT)
            assertThat(expectMostRecentItem().frontPhoto).isNull()
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(cards.cards.value.single().frontPhotoPath).isNull()
    }

    @Test
    fun aPhotoPickedInTheForm_isDeleted_whenAnotherReplacesIt_orItIsRemoved() = runTest { // #10
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            viewModel.removePhoto(CardPhotoSide.BACK)
            assertThat(expectMostRecentItem().frontPhoto).isEqualTo("photo-2")
        }

        assertThat(photos.stored).containsExactly("photo-2")
    }

    @Test
    fun aFormClosedWithoutSaving_deletesThePhotosPickedInIt_butNotTheCardsOwn() = runTest { // #10
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store, number = "12345674", frontPhotoPath = "front"))
        photos.stored += "front"
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled", cardId = cardId(1))) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()

        assertThat(photos.stored).containsExactly("front")
    }

    @Test
    fun aSavedForm_keepsItsPhotos_whenItCloses() = runTest { // #10
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled")) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()

        assertThat(photos.stored).containsExactly("photo-1")
    }

    @Test
    fun aSaveStillRunningWhenTheFormCloses_keepsItsPhoto() = runTest { // #10, #64
        val gate = CompletableDeferred<Unit>()
        cards.addGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled")) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(photos.stored).containsExactly("photo-1")
    }

    @Test
    fun aSaveThatFailsAfterTheFormClosed_deletesItsPhoto() = runTest { // #10, #64
        val gate = CompletableDeferred<Unit>()
        cards.addGate = gate
        cards.addFailure = IllegalStateException("disk full")
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel(CardForm("ruled")) }
        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(photos.stored).isEmpty()
    }

    @Test
    fun aFailedSave_keepsThePhotos_soTheFormCanTryAgain() = runTest { // #10, #49
        cards.addFailure = IllegalStateException("disk full")
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.save()
            val failed = expectMostRecentItem()
            assertThat(failed.saveFailed).isTrue()
            assertThat(failed.frontPhoto).isEqualTo("photo-1")
        }
        assertThat(photos.stored).containsExactly("photo-1")
    }

    @Test
    fun whileAPhotoIsCopiedIn_theCardCantBeSaved_norAnotherPhotoPicked() = runTest { // #10
        val gate = CompletableDeferred<Unit>()
        photos.importGate = gate
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            val importing = expectMostRecentItem()
            assertThat(importing.importingPhoto).isEqualTo(CardPhotoSide.FRONT)
            assertThat(importing.canSave).isFalse()

            gate.complete(Unit)
            val done = expectMostRecentItem()
            assertThat(done.importingPhoto).isNull()
            assertThat(done.canSave).isTrue()
            assertThat(done.backPhoto).isNull()
        }
        assertThat(photos.imported).hasSize(1)
    }

    @Test
    fun aPictureThatCantBeCopiedIn_saysSo_andTheNextPickClearsIt() = runTest { // #10
        photos.importFailure = FileNotFoundException("gone")
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            val failed = expectMostRecentItem()
            assertThat(failed.photoFailed).isTrue()
            assertThat(failed.frontPhoto).isNull()

            photos.importFailure = null
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            val picked = expectMostRecentItem()
            assertThat(picked.photoFailed).isFalse()
            assertThat(picked.frontPhoto).isEqualTo("photo-1")
        }
    }

    @Test
    fun aCancelledPick_changesNothing() = runTest { // #10
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, null)
            expectNoEvents()
        }
        assertThat(photos.imported).isEmpty()
    }

    @Test
    fun noPhotoCanBePickedOrRemoved_onceSavingHasStarted() = runTest { // #10: the saved card would lose it
        cards.addGate = CompletableDeferred()
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.save()
            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            viewModel.removePhoto(CardPhotoSide.FRONT)
            assertThat(expectMostRecentItem().frontPhoto).isEqualTo("photo-1")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(photos.imported).hasSize(1)
        assertThat(photos.stored).containsExactly("photo-1")
    }

    @Test
    fun aPickedPhotoWhoseFileIsGone_isNotSaved() = runTest { // #10: swept while the form waited after process death
        val viewModel = viewModel(CardForm("ruled"))

        viewModel.state.test {
            awaitLoaded()
            viewModel.onNumberChange("12345674")
            viewModel.onPhotoPicked(CardPhotoSide.FRONT, PICKED)
            viewModel.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            photos.stored -= "photo-1"
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(cards.cards.value.single().frontPhotoPath).isNull()
        assertThat(cards.cards.value.single().backPhotoPath).isEqualTo("photo-2")
    }

    @Test
    fun aPickedPhoto_isRestoredAfterProcessDeath() = runTest { // #10
        val handle = SavedStateHandle(route = CardForm("ruled"))
        val first = viewModel(handle)
        first.state.test {
            awaitLoaded()
            first.onPhotoPicked(CardPhotoSide.BACK, PICKED)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel(handle).state.test {
            assertThat(awaitLoaded().backPhoto).isEqualTo("photo-1")
        }
    }

    @Test
    fun addingACard_isNotEditing() = runTest {
        viewModel(CardForm("ruled")).state.test {
            assertThat(awaitLoaded().editing).isFalse()
        }
    }

    private fun viewModel(route: CardForm) = viewModel(SavedStateHandle(route = route))

    private fun viewModel(handle: SavedStateHandle) =
        CardFormViewModel(handle, stores, cards, Clock.fixed(NOW, ZoneOffset.UTC), photos)

    /** The first state with the store loaded. */
    private suspend fun ReceiveTurbine<CardFormState>.awaitLoaded(): CardFormState {
        var state = awaitItem()
        while (state.store == null) state = awaitItem()
        return state
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-05-05T05:05:05Z")
        val PICKED: Uri = Uri.parse("content://media/picker/0/1")
    }
}
