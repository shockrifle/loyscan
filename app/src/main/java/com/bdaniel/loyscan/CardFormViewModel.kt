package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.CardFormState.NumberCheck
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.data.photos.withoutMissing
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.cardnumber.CardNumberEngine
import com.bdaniel.loyscan.domain.matchingScan
import com.bdaniel.loyscan.domain.search
import com.bdaniel.loyscan.domain.cardnumber.Validation
import com.bdaniel.loyscan.ui.barcode.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** Which side of the physical card a photo shows (#10). */
enum class CardPhotoSide { FRONT, BACK }

/** What the card form shows, derived from its inputs. */
data class CardFormState(
    /** `null` until the store has loaded, or if no store has the route's id. */
    val store: Store? = null,
    val label: String = "",
    /** The note field's text: free text, e.g. a PIN hint or an expiry date (#9). */
    val note: String = "",
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
    /** While editing, every store but this one, by name: the card can be moved to any of them (#8). */
    val otherStores: List<Store> = emptyList(),
    /** The front photo's name (#10): the edited card's, or one picked in this form; `null` for none. */
    val frontPhoto: String? = null,
    val backPhoto: String? = null,
    /** The side whose picked picture is being copied in or cropped; the card can't be saved until it is. */
    val importingPhoto: CardPhotoSide? = null,
    /** The last picked or taken picture couldn't be copied in or cropped. */
    val photoFailed: Boolean = false,
    /** The photo being framed in the crop screen (#10), or `null` when no crop screen is open. */
    val cropping: Cropping? = null,
) {
    /** A photo staged for a side, waiting to be framed in the crop screen (#10). */
    data class Cropping(val photo: String, val side: CardPhotoSide)

    /** Anything but an empty number can be saved, an invalid one "anyway"; not while a photo is being copied in or cropped. */
    val canSave: Boolean
        get() = store != null && check != NumberCheck.Empty && !saving && savedCardId == null &&
            importingPhoto == null && cropping == null

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
 * Last step of adding a card: the label, number, note (#9) and photos (#10) for the routed store, the number from a scan or typed. The
 * store can be switched to one whose rule reads the scan; the scan, label and note stay. With [CardForm.cardId] it edits that card (#8): the form
 * starts as the card, a scanned card as its untouched scan, and the card can be moved to any other store.
 */
@HiltViewModel
class CardFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    storeRepository: StoreRepository,
    private val cardRepository: LoyaltyCardRepository,
    private val clock: Clock,
    private val photos: CardPhotos,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CardForm>()
    private val routedScan = route.scannedContent?.let { content -> route.scannedFormat?.let { content to it } }

    /**
     * The card being edited, `null` until it has loaded and while adding one. A photo whose file is missing is left off,
     * so that side offers "Add" and the save clears its name (#87).
     */
    private val original: StateFlow<LoyaltyCard?> =
        (route.cardId?.let { cardRepository.getByIdWithStore(it) } ?: flowOf(null))
            .map { card -> card?.let { photos.withoutMissing(it) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The routed store, until [switchStore] picks another. */
    private val storeId = savedStateHandle.getStateFlow(STORE_ID, route.storeId)

    /** `null` until the label is changed: the edited card's label, or none. */
    private val label = savedStateHandle.getStateFlow<String?>(LABEL, null)

    /** `null` until the note is changed: the edited card's note, or none. */
    private val note = savedStateHandle.getStateFlow<String?>(NOTE, null)

    /** `null` until the number is changed: the untouched scan, the edited card's typed number, or nothing typed. */
    private val typedNumber = savedStateHandle.getStateFlow<String?>(TYPED_NUMBER, null)

    /**
     * Per side, `null` until the photo is changed: the edited card's, or none. Then the name of a photo picked in this
     * form, which this form deletes if it isn't saved, or [REMOVED].
     */
    private val pickedPhotos = combine(
        savedStateHandle.getStateFlow<String?>(FRONT_PHOTO, null),
        savedStateHandle.getStateFlow<String?>(BACK_PHOTO, null),
        ::PickedPhotos,
    )

    /**
     * The photo staged for the crop screen and the side it is for (#10), kept in the saved state so the crop screen
     * survives process death (the staged file is on disk, as a capture is), `null` when no crop screen is open.
     */
    private val croppingPhoto = combine(
        savedStateHandle.getStateFlow<String?>(CROP_PHOTO, null),
        savedStateHandle.getStateFlow<String?>(CROP_SIDE, null),
    ) { photo, side ->
        // Both keys are written together, but each getStateFlow emits on its own: build a Cropping only once both are set.
        if (photo != null && side != null) CardFormState.Cropping(photo, CardPhotoSide.valueOf(side)) else null
    }

    private val progress = MutableStateFlow(Progress())

    /**
     * The id a new card is saved under, fixed when the add form is first created (#99). It goes into the saved state
     * at once, so every snapshot of it carries the id: a form restored after its activity was destroyed while a save
     * ran drafts the same card, and saving again updates it instead of adding a second one. A write made only when
     * saving would be too late, since the activity saves its state when it stops, before that save ends.
     */
    private val newCardId: String? = if (route.cardId != null) {
        null
    } else {
        savedStateHandle.get<String>(NEW_CARD_ID)
            ?: UUID.randomUUID().toString().also { savedStateHandle[NEW_CARD_ID] = it }
    }

    /**
     * Started eagerly, not while subscribed: [onNumberChange] and [save] guard on `state.value`, which must stay current
     * with nothing observing it (#50). Eagerly still starts at the initial value, so the guards also refuse until the
     * store has loaded.
     */
    val state: StateFlow<CardFormState> =
        combine(storeId, storeRepository.getAll(), original, combine(label, note, typedNumber, pickedPhotos, croppingPhoto, ::Inputs), progress) {
                id, stores, original, inputs, progress ->
            formState(id, stores, original, inputs, progress)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CardFormState()
        )

    fun onLabelChange(label: String) {
        savedStateHandle[LABEL] = label
    }

    fun onNoteChange(note: String) {
        savedStateHandle[NOTE] = note
    }

    fun onNumberChange(number: String) {
        // A scan shown as something other than its content can't be edited, or the rendering would become the number.
        // Before the form has loaded, editability isn't known yet: the field isn't shown, so nothing is lost (#50).
        val current = state.value
        if (current.store == null || !current.numberEditable) return
        savedStateHandle[TYPED_NUMBER] = number
    }

    /**
     * Makes the form [storeId]'s, keeping the scan or typed number, the label and the note; for a store from
     * [CardFormState.suggestedStores] or, while editing, [CardFormState.otherStores]. Not once saving has started: the
     * saved card opens in the form's store's colour (#8 review).
     */
    fun switchStore(storeId: String) {
        val progress = progress.value
        if (progress.saving || progress.savedCardId != null) return
        savedStateHandle[STORE_ID] = storeId
    }

    /**
     * Stages the picture picked for [side] and opens the crop screen on it (#10); `null` is a cancelled pick. One
     * picture at a time, and none while a photo is being copied in or cropped, or once saving has started.
     */
    fun onPhotoPicked(side: CardPhotoSide, picture: Uri?) {
        if (picture != null) stageForCrop(side, picture)
    }

    /**
     * A file for the camera app to write [side]'s photo into (#10), or `null` while no photo can be added: one is being
     * copied in, or saving has started. Kept in the saved state, since this process may die while the camera app is
     * in front.
     */
    fun startCapture(side: CardPhotoSide): Uri? {
        if (!canChangePhotos()) return null
        discardCapture()
        val capture = photos.newCapture()
        savedStateHandle[CAPTURE] = capture.name
        savedStateHandle[CAPTURE_URI] = capture.uri
        savedStateHandle[CAPTURE_SIDE] = side.name
        return capture.uri
    }

    /**
     * The camera app is done with [startCapture]'s file (#10): a photo [taken] is a staged file already (the camera app
     * wrote it), so the crop screen opens on it; otherwise its file is deleted.
     */
    fun onPhotoTaken(taken: Boolean) {
        val name = savedStateHandle.get<String>(CAPTURE) ?: return
        val side = savedStateHandle.get<String>(CAPTURE_SIDE)?.let(CardPhotoSide::valueOf)
        clearCapture()
        if (taken && side != null) openCrop(name, side) else photos.discard(listOf(name))
    }

    /**
     * Copies [picture] in as it is and opens the crop screen on the staged file (#10). Returns without staging while no
     * photo can be added. A copy that fails (e.g. a cloud picture that can't be downloaded) or isn't a picture says so.
     */
    private fun stageForCrop(side: CardPhotoSide, picture: Uri) {
        if (!canChangePhotos()) return
        progress.value = progress.value.copy(importing = side, photoFailed = false)
        viewModelScope.launch {
            val name = try {
                photos.stage(picture)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't copy the picked photo in")
                progress.update { it.copy(importing = null, photoFailed = true) }
                return@launch
            }
            progress.update { it.copy(importing = null) }
            openCrop(name, side)
        }
    }

    /** Opens the crop screen on the staged photo [name] for [side] (#10): kept in the saved state, so it survives process death. */
    private fun openCrop(name: String, side: CardPhotoSide) {
        savedStateHandle[CROP_SIDE] = side.name
        savedStateHandle[CROP_PHOTO] = name
    }

    /**
     * Stores the photo framed in the crop screen (#10): the staged picture re-encoded, cropped to [crop]
     * ([com.bdaniel.loyscan.data.photos.PhotoCrop.WHOLE] keeps it all). A photo picked earlier in this form for that
     * side is deleted; the edited card's own goes when the edit is saved. A crop that fails says so.
     */
    fun confirmCrop(crop: PhotoCrop) {
        val name = savedStateHandle.get<String>(CROP_PHOTO) ?: return
        val side = savedStateHandle.get<String>(CROP_SIDE)?.let(CardPhotoSide::valueOf) ?: return
        clearCropping()
        progress.value = progress.value.copy(importing = side, photoFailed = false)
        viewModelScope.launch {
            val cropped = try {
                photos.crop(name, crop)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't crop the photo")
                progress.update { it.copy(importing = null, photoFailed = true) }
                return@launch
            }
            val replaced = picked(side)
            savedStateHandle[side.key] = cropped
            progress.update { it.copy(importing = null) }
            replaced?.let { photos.discard(listOf(it)) }
        }
    }

    /** Closes the crop screen without keeping the photo (#10): the staged file is deleted. */
    fun cancelCrop() {
        val name = savedStateHandle.get<String>(CROP_PHOTO)
        clearCropping()
        name?.let { photos.discard(listOf(it)) }
    }

    // Set to null, not removed: a removed key doesn't notify its getStateFlow, so the crop screen would linger in state.
    private fun clearCropping() {
        savedStateHandle[CROP_PHOTO] = null
        savedStateHandle[CROP_SIDE] = null
    }

    /** One picture at a time, and none while one is being cropped or once saving has started. */
    private fun canChangePhotos(): Boolean {
        val current = progress.value
        return !current.saving && current.savedCardId == null && current.importing == null &&
            savedStateHandle.get<String>(CROP_PHOTO) == null
    }

    /** Deletes the file of a capture the camera app never answered, before a new one replaces it. */
    private fun discardCapture() {
        savedStateHandle.get<String>(CAPTURE)?.let { photos.discard(listOf(it)) }
        clearCapture()
    }

    private fun clearCapture() {
        savedStateHandle.remove<String>(CAPTURE)
        savedStateHandle.remove<Uri>(CAPTURE_URI)
        savedStateHandle.remove<String>(CAPTURE_SIDE)
    }

    /** Removes [side]'s photo (#10), as [onPhotoPicked] replaces it. Not once saving has started. */
    fun removePhoto(side: CardPhotoSide) {
        if (!canChangePhotos()) return
        val replaced = picked(side)
        savedStateHandle[side.key] = REMOVED
        replaced?.let { photos.discard(listOf(it)) }
    }

    /**
     * Deliberately deletes nothing (#95). onCleared also runs when the activity is destroyed with the form still on the
     * back stack and its process alive ("Don't keep activities", a background kill), and the form restored from the same
     * saved state still names the photos picked in it, so their files must survive. It can't tell that from a real close,
     * so an abandoned pick, like an unanswered camera capture or a photo staged for the crop screen but not yet framed,
     * is left to the orphan sweep — including one a save leaves when it fails after the form closed, which for the same
     * reason no longer deletes its picks either.
     */
    override fun onCleared() = Unit

    /** The name of the photo picked in this form for [side], if there is one. */
    private fun picked(side: CardPhotoSide): String? = savedStateHandle.get<String>(side.key)?.takeIf { it != REMOVED }

    private fun pickedNames(): List<String> = CardPhotoSide.entries.mapNotNull(::picked)

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
            note = current.note.trim().ifEmpty { null },
            frontPhotoPath = current.frontPhoto,
            backPhotoPath = current.backPhoto,
            updatedAt = now,
        )
        val drafted = original?.let { draft } ?: draft.copy(id = checkNotNull(newCardId), createdAt = now)
        val picked = pickedNames()
        viewModelScope.launch {
            progress.value = try {
                withContext(NonCancellable) {
                    // A new card this form already saved before it was restored (#99): saving again updates it.
                    val saved = original ?: cardRepository.getByIdWithStore(drafted.id).first()
                    // A pick restored after process death may have been swept since, if it waited over a day: that side
                    // stays as the card had it, so the edit doesn't delete the card's own photo.
                    val gone = picked.filterNot { photos.exists(it) }.toSet()
                    fun unlessGone(photo: String?, cards: String?) = if (photo in gone) cards else photo
                    val card = drafted.copy(
                        frontPhotoPath = unlessGone(drafted.frontPhotoPath, original?.frontPhotoPath),
                        backPhotoPath = unlessGone(drafted.backPhotoPath, original?.backPhotoPath),
                    )
                    if (saved == null) cardRepository.add(card) else cardRepository.update(card)
                }
                Progress(savedCardId = drafted.id)
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
     * edit starts from the [original] card, so its id, creation and uses stay.
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
        inputs: Inputs,
        progress: Progress,
    ): CardFormState {
        val editing = route.cardId != null
        val label = inputs.label ?: original?.label.orEmpty()
        val note = inputs.note ?: original?.note.orEmpty()
        val changedNumber = inputs.typedNumber
        val typed = typedOrNull(original, changedNumber)
        val store = stores.firstOrNull { it.id == storeId }
        // Nothing to show until the store has loaded, and the edited card (which may be gone). The stores have loaded
        // here, so a missing store is gone, e.g. a custom store deleted (#51).
        if (store == null || editing && original == null) {
            return CardFormState(
                label = label, note = note, number = typed.orEmpty(), editing = editing, storeGone = store == null,
                cropping = inputs.cropping,
            )
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
            note = note,
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
            // Adding a card offers none: back returns to the picker.
            otherStores = if (editing) stores.filter { it.id != store.id }.search("") else emptyList(),
            frontPhoto = inputs.photos.front.photo(original?.frontPhotoPath),
            backPhoto = inputs.photos.back.photo(original?.backPhotoPath),
            importingPhoto = progress.importing,
            photoFailed = progress.photoFailed,
            cropping = inputs.cropping,
        )
    }

    /** A side's photo: the card's own until it is changed, then the picked one or none. */
    private fun String?.photo(cards: String?): String? = when (this) {
        null -> cards
        REMOVED -> null
        else -> this
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
        val importing: CardPhotoSide? = null,
        val photoFailed: Boolean = false,
    )

    /** What the user changed, each `null` until changed. */
    private data class Inputs(
        val label: String?,
        val note: String?,
        val typedNumber: String?,
        val photos: PickedPhotos,
        val cropping: CardFormState.Cropping?,
    )

    private data class PickedPhotos(val front: String?, val back: String?)

    private val CardPhotoSide.key get() = if (this == CardPhotoSide.FRONT) FRONT_PHOTO else BACK_PHOTO

    private companion object {
        const val STORE_ID = "chosenStoreId"
        const val LABEL = "label"
        const val NOTE = "note"
        const val TYPED_NUMBER = "typedNumber"
        const val FRONT_PHOTO = "frontPhoto"
        const val BACK_PHOTO = "backPhoto"

        /** The id a new card is saved under (#99), kept from the add form's creation. */
        const val NEW_CARD_ID = "newCardId"

        /** The file handed to the camera app (#10), until it answers: its name, its Uri and the side it is for. */
        const val CAPTURE = "capture"
        const val CAPTURE_URI = "captureUri"
        const val CAPTURE_SIDE = "captureSide"

        /** The photo staged for the crop screen (#10), until it is framed or dropped: its name and the side it is for. */
        const val CROP_PHOTO = "cropPhoto"
        const val CROP_SIDE = "cropSide"

        /** A side's photo was removed in this form. Never a photo's name, which is a UUID. */
        const val REMOVED = ""
    }
}
