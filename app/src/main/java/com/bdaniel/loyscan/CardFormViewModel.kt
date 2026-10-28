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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val suggestedStores: List<Store> = emptyList(),
    /** The form edits an existing card (#8) rather than adding one. */
    val editing: Boolean = false,
    /** The stores have loaded and none has the form's id: it was deleted while the form was open (#51). */
    val storeGone: Boolean = false,
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
 * to one whose rule reads the scan; the scan and label stay. With [CardForm.cardId] it edits that card (#8): the form
 * starts as the card, a scanned card as its untouched scan.
 */
@HiltViewModel
class CardFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    storeRepository: StoreRepository,
    private val cardRepository: LoyaltyCardRepository,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CardForm>()
    private val routedScan = route.scannedContent?.let { content -> route.scannedFormat?.let { content to it } }

    /** The card being edited, `null` until it has loaded and while adding one. */
    private val original: StateFlow<LoyaltyCard?> =
        (route.cardId?.let { cardRepository.getByIdWithStore(it) } ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The routed store, until [switchStore] picks another. */
    private val storeId = savedStateHandle.getStateFlow(STORE_ID, route.storeId)

    /** `null` until the label is changed: the edited card's label, or none. */
    private val label = savedStateHandle.getStateFlow<String?>(LABEL, null)

    /** `null` until the number is changed: the untouched scan, the edited card's typed number, or nothing typed. */
    private val typedNumber = savedStateHandle.getStateFlow<String?>(TYPED_NUMBER, null)

    private val progress = MutableStateFlow(Progress())

    /**
     * Started eagerly, not while subscribed: [onNumberChange] and [save] guard on `state.value`, which must stay current
     * with nothing observing it (#50). Eagerly still starts at the initial value, so the guards also refuse until the
     * store has loaded.
     */
    val state: StateFlow<CardFormState> =
        combine(storeId, storeRepository.getAll(), original, combine(label, typedNumber, ::Pair), progress) {
                id, stores, original, (label, typed), progress ->
            formState(id, stores, original, label, typed, progress)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CardFormState()
        )

    fun onLabelChange(label: String) {
        savedStateHandle[LABEL] = label
    }

    fun onNumberChange(number: String) {
        // A scan shown as something other than its content can't be edited, or the rendering would become the number.
        // Before the form has loaded, editability isn't known yet: the field isn't shown, so nothing is lost (#50).
        val current = state.value
        if (current.store == null || !current.numberEditable) return
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
     * so and the card can be saved again. A save still running when the form closes (Back) is finished (#64).
     */
    fun save() {
        val current = state.value
        val store = current.store
        if (!current.canSave || store == null) return
        val original = original.value
        if (route.cardId != null && original == null) return
        progress.value = Progress(saving = true)
        val now = clock.instant()
        val draft = draft(store, original, typedNumber.value).copy(
            label = current.label.trim().ifEmpty { null },
            updatedAt = now,
        )
        val card = original?.let { draft } ?: draft.copy(id = UUID.randomUUID().toString(), createdAt = now)
        viewModelScope.launch {
            progress.value = try {
                withContext(NonCancellable) {
                    if (original == null) cardRepository.add(card) else cardRepository.update(card)
                }
                Progress(savedCardId = card.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // a failed insert mustn't crash the form (#49)
                Timber.e(e, "Couldn't save the card")
                Progress(saveFailed = true)
            }
        }
    }

    /** The scan the form starts from: the routed one, or the edited card's when it was scanned. */
    private fun scan(original: LoyaltyCard?): Pair<String, BarcodeFormat>? =
        routedScan ?: original?.scannedFormat?.let { original.number to it }

    /** The number typed so far, or `null` while it is the untouched scan; [typed] is `null` until the number changes. */
    private fun typedOrNull(original: LoyaltyCard?, typed: String?): String? =
        typed ?: if (scan(original) != null) null else original?.number.orEmpty()

    /**
     * The card as it would be saved: the untouched scan verbatim with its format, else the typed number (trimmed). An
     * edit starts from the [original] card, so its id, creation, uses and note stay.
     */
    private fun draft(store: Store, original: LoyaltyCard?, typed: String?): LoyaltyCard {
        val typedNumber = typedOrNull(original, typed)
        val untouchedScan = scan(original)?.takeIf { typedNumber == null }
        return (original ?: LoyaltyCard.EMPTY).copy(
            store = store,
            number = untouchedScan?.first ?: typedNumber.orEmpty().trim(),
            scannedFormat = untouchedScan?.second,
        )
    }

    private fun formState(
        storeId: String,
        stores: List<Store>,
        original: LoyaltyCard?,
        changedLabel: String?,
        changedNumber: String?,
        progress: Progress,
    ): CardFormState {
        val editing = route.cardId != null
        val label = changedLabel ?: original?.label.orEmpty()
        val typed = typedOrNull(original, changedNumber)
        val store = stores.firstOrNull { it.id == storeId }
        // Nothing to show until the store has loaded, and the edited card (which may be gone). The stores have loaded
        // here, so a missing store is gone, e.g. a custom store deleted (#51).
        if (store == null || editing && original == null) {
            return CardFormState(label = label, number = typed.orEmpty(), editing = editing, storeGone = store == null)
        }
        val draft = draft(store, original, changedNumber)
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
            editing = editing,
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
        stores: List<Store>,
        draft: LoyaltyCard,
        ruleReadsScan: Boolean,
    ): List<Store> {
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
