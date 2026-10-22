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
import com.bdaniel.loyscan.domain.cardnumber.Validation
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
) {
    /** Anything but an empty number can be saved, an invalid one "anyway". */
    val canSave: Boolean get() = store != null && check != NumberCheck.Empty && !saving && savedCardId == null

    sealed interface NumberCheck {
        data object Empty : NumberCheck
        data object Valid : NumberCheck
        data class Invalid(val errors: Set<Validation.Error>) : NumberCheck

        /** The store's rule can't have produced the scanned content (the barcode format isn't compared, #47). */
        data object ScanDoesNotMatchStore : NumberCheck
    }
}

/** Last step of adding a card: the label and number for the routed store, from a scan or typed. */
@HiltViewModel
class CardFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    storeRepository: StoreRepository,
    private val cardRepository: LoyaltyCardRepository,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CardForm>()
    private val scan = route.scannedContent?.let { content -> route.scannedFormat?.let { content to it } }

    private val label = savedStateHandle.getStateFlow(LABEL, "")

    /** `null` while the number is the untouched scan. */
    private val typedNumber = savedStateHandle.getStateFlow<String?>(TYPED_NUMBER, if (scan == null) "" else null)

    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<CardFormState> =
        combine(storeRepository.getById(route.storeId), label, typedNumber, progress) { store, label, typed, progress ->
            formState(store, label, typed, progress)
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
            } catch (e: Exception) { // e.g. the store was removed while the form was open (#49)
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

    private fun formState(store: Store?, label: String, typed: String?, progress: Progress): CardFormState {
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
                is Validation.Valid -> NumberCheck.Valid
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
        )
    }

    private data class Progress(
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val savedCardId: String? = null,
    )

    private companion object {
        const val LABEL = "label"
        const val TYPED_NUMBER = "typedNumber"
    }
}
