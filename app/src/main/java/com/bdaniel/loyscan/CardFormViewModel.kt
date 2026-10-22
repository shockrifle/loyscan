package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import com.bdaniel.loyscan.domain.matchingScan
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.ui.barcode.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** What the card form shows, derived from its inputs. */
data class CardFormState(
    /** `null` until the store has loaded, or if no store has the route's id. */
    val store: Store? = null,
    val label: String = "",
    /** The number field's text. An untouched scan shows the number the store's rule recovers from it. */
    val number: String = "",
    /** Set while the number is the untouched scan; editing the number makes it a typed number. */
    val scannedFormat: BarcodeFormat? = null,
    /**
     * False for a scan shown differently from its content (GS1 data as printed, control characters as spaces) that the
     * rule can't read back: an edit would make that rendering the number. [CardFormViewModel.typeNumberInstead] clears it.
     */
    val numberEditable: Boolean = true,
    val check: NumberCheck = NumberCheck.Empty,
    /** The number as the card will show it, when that differs from the field's text. */
    val shownAs: String? = null,
    val barcodeFormat: BarcodeFormat? = null,
    val saving: Boolean = false,
    /** The last save failed; the form stays open to try again. */
    val saveFailed: Boolean = false,
    /** The id of the saved card, once it is saved. */
    val savedCardId: String? = null,
    /**
     * Other stores whose rule reads the scan better than this store's (#6): any that reads it when this store has no
     * rule or its rule can't have produced it, and those naming the scan's GS1 format when this rule names the same
     * symbology without the mark. Ranked as [com.bdaniel.loyscan.domain.matchingScan] does. [CardFormViewModel.switchStore] picks one.
     */
    val suggestedStores: kotlin.collections.List<Store> = emptyList(),
) {
    /** Anything but an empty number can be saved, an invalid one "anyway". */
    val canSave: Boolean get() = store != null && check != NumberCheck.Empty && !saving && savedCardId == null

    sealed interface NumberCheck {
        data object Empty : NumberCheck
        data object Valid : NumberCheck
        data class Invalid(val errors: Set<Validation.Error>) : NumberCheck

        /**
         * A typed number for a store without a rule that the store's barcode format can't hold (e.g. not an EAN-13 for
         * an EAN-13 store, or one digit short, which ZXing would complete with a check digit): the card would be drawn in a
         * fallback format or as another number (#47 review).
         */
        data class DoesNotFitFormat(val format: BarcodeFormat) : NumberCheck

        /** The store's rule can't have produced the scanned content (the barcode format isn't compared, #47). */
        data object ScanDoesNotMatchStore : NumberCheck
    }
}

/**
 * Last step of adding a card: the label and number for the routed store, from a scan or typed. The store can be switched
 * to one whose rule reads the scan; the scan and label stay.
 */
@HiltViewModel
class CardFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    storeRepository: StoreRepository,
    private val cardRepository: LoyaltyCardRepository,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CardForm>()
    private val scan = route.scannedContent?.let { content -> route.scannedFormat?.let { content to it } }

    /** The routed store, until [switchStore] picks another. */
    private val storeId = savedStateHandle.getStateFlow(STORE_ID, route.storeId)

    private val label = savedStateHandle.getStateFlow(LABEL, "")

    /** `null` while the number is the untouched scan. */
    private val typedNumber = savedStateHandle.getStateFlow<String?>(TYPED_NUMBER, if (scan == null) "" else null)

    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<CardFormState> =
        combine(storeId, storeRepository.getAll(), label, typedNumber, progress) { id, stores, label, typed, progress ->
            formState(id, stores, label, typed, progress)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CardFormState()
        )

    fun onLabelChange(label: String) {
        savedStateHandle[LABEL] = label
    }

    fun onNumberChange(number: String) {
        // A scan shown as something other than its content can't be edited, or the rendering would become the number.
        if (!state.value.numberEditable) return
        savedStateHandle[TYPED_NUMBER] = number
    }

    /** Makes the form [storeId]'s, keeping the scan and the label; for a store from [CardFormState.suggestedStores]. */
    fun switchStore(storeId: String) {
        savedStateHandle[STORE_ID] = storeId
    }

    /** Drops the scan, for a number typed from the card instead. */
    fun typeNumberInstead() {
        savedStateHandle[TYPED_NUMBER] = ""
    }

    /**
     * Saves the card once; [CardFormState.savedCardId] then names it. If saving fails, [CardFormState.saveFailed] says
     * so and the card can be saved again.
     */
    fun save() {
        val current = state.value
        val store = current.store
        if (!current.canSave || store == null) return
        progress.value = Progress(saving = true)
        val now = clock.instant()
        val card = draft(store, typedNumber.value).copy(
            id = UUID.randomUUID().toString(),
            label = current.label.trim().ifEmpty { null },
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            progress.value = try {
                cardRepository.add(card)
                Progress(savedCardId = card.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // a failed insert mustn't crash the form (#49)
                Timber.e(e, "Couldn't save the card")
                Progress(saveFailed = true)
            }
        }
    }

    /** The card as it would be saved: the untouched scan verbatim with its format, else the typed number (trimmed). */
    private fun draft(store: Store, typed: String?): LoyaltyCard {
        val untouchedScan = scan?.takeIf { typed == null }
        return LoyaltyCard.EMPTY.copy(
            store = store,
            number = untouchedScan?.first ?: typed.orEmpty().trim(),
            scannedFormat = untouchedScan?.second,
        )
    }

    private fun formState(
        storeId: String,
        stores: kotlin.collections.List<Store>,
        label: String,
        typed: String?,
        progress: Progress,
    ): CardFormState {
        val store = stores.firstOrNull { it.id == storeId }
        if (store == null) return CardFormState(label = label, number = typed.orEmpty())
        val draft = draft(store, typed)
        val scannedFormat = draft.scannedFormat
        val rule = store.cardNumberRule
        val ruleReadsScan = scannedFormat != null && rule != null &&
            CardNumberEngine.match(draft.number, scannedFormat, rule) != null
        val check = if (scannedFormat != null) {
            if (rule == null || ruleReadsScan) NumberCheck.Valid else NumberCheck.ScanDoesNotMatchStore
        } else {
            when (val validation = CardNumberEngine.validate(typed.orEmpty(), rule)) {
                is Validation.Valid ->
                    // Without a rule nothing else checks the number against the store's format.
                    if (rule == null && !BarcodeEncoder.holdsAsIs(draft.barcodeContent, draft.barcodeFormat)) {
                        NumberCheck.DoesNotFitFormat(draft.barcodeFormat)
                    } else {
                        NumberCheck.Valid
                    }
                is Validation.Invalid ->
                    if (Validation.Error.EMPTY in validation.errors) NumberCheck.Empty else NumberCheck.Invalid(validation.errors)
            }
        }
        val number = if (scannedFormat != null) draft.displayNumber else typed.orEmpty()
        return CardFormState(
            store = store,
            label = label,
            number = number,
            scannedFormat = scannedFormat,
            // Editable when the field holds the scan's core (read back by the rule) or its content as it is.
            numberEditable = scannedFormat == null || ruleReadsScan || number == draft.number,
            check = check,
            shownAs = draft.displayNumber.takeIf { check != NumberCheck.Empty && it != number.trim() },
            barcodeFormat = draft.barcodeFormat,
            saving = progress.saving,
            saveFailed = progress.saveFailed,
            savedCardId = progress.savedCardId,
            suggestedStores = suggestedStores(store, stores, draft, ruleReadsScan),
        )
    }

    /**
     * For an untouched scan (#6, decision 2026-10-22-store-suggestion-trigger): every other store whose rule reads it
     * when this store has no rule or its rule rejects it; the stores whose rule names its exact format when the scan has
     * the GS1 mark and this rule names the same symbology without it; otherwise none. A scan without the mark against a
     * GS1 rule offers nothing: a missing mark is weak evidence (a plain read of a Tesco card), a present one is strong.
     */
    private fun suggestedStores(
        store: Store,
        stores: kotlin.collections.List<Store>,
        draft: LoyaltyCard,
        ruleReadsScan: Boolean,
    ): kotlin.collections.List<Store> {
        val format = draft.scannedFormat ?: return emptyList()
        val matching = stores.filter { it.id != store.id }.matchingScan(draft.number, format)
        if (!ruleReadsScan) return matching
        val markOnlyOnTheScan = format != format.symbology && store.cardNumberRule?.format == format.symbology
        return if (markOnlyOnTheScan) matching.filter { it.cardNumberRule?.format == format } else emptyList()
    }

    private data class Progress(
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val savedCardId: String? = null,
    )

    private companion object {
        const val STORE_ID = "chosenStoreId"
        const val LABEL = "label"
        const val TYPED_NUMBER = "typedNumber"
    }
}
