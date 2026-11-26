package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.data.photos.withoutMissing
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.StoreRepository
import androidx.compose.ui.graphics.Color
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.toBrandColorOrNull
import com.bdaniel.loyscan.domain.Store
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** What a store's logo screen shows (#151). */
data class StoreLogoState(
    /** The store, its [Store.logo] `null` when it has none or its file is missing; `null` while loading or gone. */
    val store: Store? = null,
    /** The store has loaded. */
    val ready: Boolean = false,
    /** No store has the routed id (deleted while the screen was open). */
    val storeGone: Boolean = false,
    /** The staged picture the crop screen is open on, or `null`. */
    val cropping: String? = null,
    /** A picture is being copied in or stored, or the logo saved or removed: nothing else can start. */
    val working: Boolean = false,
    /** The last picture couldn't be used, or the logo couldn't be saved or removed. */
    val failed: Boolean = false,
    /** How the logo is drawn couldn't be saved (#240). */
    val styleFailed: Boolean = false,
    /** The front photos of the store's cards, a source for the logo; only those whose file is there. */
    val cardPhotos: List<CardPhotoChoice> = emptyList(),
    /** [cardPhotos] has loaded: an empty list before then says nothing about the store's cards. */
    val cardPhotosLoaded: Boolean = false,
) {
    val canChange: Boolean get() = ready && !working && cropping == null
}

/** A card's front photo offered as the logo's source (#151): [title] is its label, or its number when it has none. */
data class CardPhotoChoice(val cardId: String, val title: String, val photo: String)

/**
 * A store's logo (#151): the user picks a picture, frames it in a square crop, and it becomes the logo every card of
 * the store shows; or removes it. Like the card form's photos ([CardFormViewModel]), a pick is staged at once and the
 * staged name kept in the [SavedStateHandle], so the crop screen survives process death. The logo is stored and saved
 * in one non-cancellable step, so leaving the screen doesn't lose it, and a logo that can't be saved is deleted.
 */
@HiltViewModel
class StoreLogoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val storeRepository: StoreRepository,
    cardRepository: LoyaltyCardRepository,
    private val photos: CardPhotos,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<StoreLogo>()
    private val storeId = route.storeId

    private val found: StateFlow<Found> = storeRepository.getAll()
        .map { stores ->
            stores.firstOrNull { it.id == storeId }
                ?.let { store -> Found.Store(store.copy(logo = store.logo?.takeIf { photos.exists(it) })) }
                ?: Found.Gone
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Found.Loading)

    private val cardPhotos: StateFlow<List<CardPhotoChoice>?> = cardRepository.getAllWithStore()
        .map { cards ->
            cards.filter { it.store.id == storeId && it.frontPhotoPath != null }
                .let { photos.withoutMissing(it) }
                .mapNotNull { card ->
                    card.frontPhotoPath?.let { CardPhotoChoice(card.id, card.label ?: card.displayNumber, it) }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val cropping = savedStateHandle.getStateFlow<String?>(CROP_LOGO, null)
    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<StoreLogoState> = combine(found, cardPhotos, cropping, progress) { found, cardPhotos, cropping, progress ->
        StoreLogoState(
            store = (found as? Found.Store)?.store,
            ready = found is Found.Store,
            storeGone = found is Found.Gone,
            cropping = cropping,
            working = progress.working,
            failed = progress.failed,
            styleFailed = progress.styleFailed,
            cardPhotos = cardPhotos.orEmpty(),
            cardPhotosLoaded = cardPhotos != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StoreLogoState())

    init {
        route.picture?.let(::openCropOnce)
    }

    /**
     * Opens the crop on the staged [picture] the route brought (#151: shared to LoyScan as a store logo), once: a
     * Cancel, a Crop or process death doesn't bring it back. For a store that is gone it is discarded; a file that is
     * no longer there (swept meanwhile) opens nothing.
     */
    private fun openCropOnce(picture: String) {
        if (savedStateHandle.get<Boolean>(ROUTE_PICTURE_TAKEN) == true) return
        viewModelScope.launch {
            val found = found.first { it !is Found.Loading }
            savedStateHandle[ROUTE_PICTURE_TAKEN] = true
            when {
                found is Found.Gone -> photos.discard(listOf(picture))
                !photos.exists(picture) -> Unit
                // A picture picked while the file was checked is in the crop already: the user's latest choice stays.
                savedStateHandle.get<String>(CROP_LOGO) != null -> photos.discard(listOf(picture))
                else -> savedStateHandle[CROP_LOGO] = picture
            }
        }
    }

    /**
     * A picture picked for the logo, `null` when the picker was cancelled: copied in, an SVG drawn as a PNG (#211), then
     * the crop screen opens on it.
     */
    fun onPicked(picture: Uri?) {
        if (picture == null) return
        stageForCrop { photos.stageLogo(picture) }
    }

    /**
     * The front photo [photo] of one of the store's cards chosen for the logo: a copy is staged, so the card keeps its
     * photo, and the crop screen opens on it. A photo that isn't one of [StoreLogoState.cardPhotos] (the list changed
     * meanwhile) is refused, and says so.
     */
    fun onCardPhoto(photo: String) {
        if (cardPhotos.value.orEmpty().none { it.photo == photo }) {
            if (canChange()) progress.value = Progress(failed = true)
            return
        }
        stageForCrop { photos.stagePhoto(photo) }
    }

    /** Copies a picture in with [stage], then opens the crop screen on it; one at a time. */
    private fun stageForCrop(stage: suspend () -> String) {
        if (!canChange()) return
        progress.value = Progress(working = true)
        viewModelScope.launch {
            val staged = try {
                stage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't copy the logo picture in")
                progress.value = Progress(failed = true)
                return@launch
            }
            savedStateHandle[CROP_LOGO] = staged
            progress.value = Progress()
        }
    }

    /** Stores the staged picture framed by [crop] as the store's logo; the logo it replaces is deleted by the repository. */
    fun confirmCrop(crop: PhotoCrop) {
        val staged = savedStateHandle.get<String>(CROP_LOGO) ?: return
        savedStateHandle[CROP_LOGO] = null
        progress.value = Progress(working = true)
        viewModelScope.launch {
            progress.value = try {
                withContext(NonCancellable) { // finished even if the screen closes meanwhile (#65)
                    val logo = photos.cropLogo(staged, crop)
                    try {
                        storeRepository.setLogo(storeId, logo)
                    } catch (e: Exception) {
                        photos.discard(listOf(logo)) // nothing names it
                        throw e
                    }
                }
                Progress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't store the logo")
                Progress(failed = true)
            }
        }
    }

    /** Closes the crop screen without keeping the picture: the staged file is deleted. */
    fun cancelCrop() {
        val staged = savedStateHandle.get<String>(CROP_LOGO) ?: return
        savedStateHandle[CROP_LOGO] = null
        photos.discard(listOf(staged))
    }

    /** Removes the store's logo, which brings back the name on the brand colour; the repository deletes its file. */
    fun removeLogo() {
        if (!canChange() || (found.value as? Found.Store)?.store?.logo == null) return
        progress.value = Progress(working = true)
        viewModelScope.launch {
            progress.value = try {
                withContext(NonCancellable) { storeRepository.setLogo(storeId, null) }
                Progress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't remove the logo")
                Progress(failed = true)
            }
        }
    }

    /**
     * Draws the store's logo in [style] (#240), `null` for As is, saved at once. Single colour starts in white or black,
     * whichever contrasts more with the brand colour, unless a colour is already saved; As is clears the colour and
     * keeps the outline, which goes with any style. Only while the store has a logo.
     */
    fun onLogoStyle(style: LogoStyle?) {
        val store = (found.value as? Found.Store)?.store ?: return
        val current = store.logoTreatment
        saveLogoTreatment(
            if (style == null) current.copy(style = null, tint = null)
            else current.copy(style = style, tint = current.tint ?: defaultTint(store.color)),
        )
    }

    /** Draws the store's logo in [tint] (`#RRGGBB`, #240), saved at once; only while a style other than As is is chosen. */
    fun onLogoTint(tint: String) {
        val current = (found.value as? Found.Store)?.store?.logoTreatment ?: return
        if (current.style == null) return
        saveLogoTreatment(current.copy(tint = tint))
    }

    private fun saveLogoTreatment(treatment: LogoTreatment) {
        if (!canChange() || (found.value as? Found.Store)?.store?.logo == null) return
        // One quick write per tap: no progress bar flashing on every swatch, only an earlier failure cleared. Only
        // styleFailed is touched: a picture copied in meanwhile keeps the screen working (review of PR #244).
        progress.update { it.copy(styleFailed = false) }
        viewModelScope.launch {
            val failed = try {
                withContext(NonCancellable) { storeRepository.setLogoTreatment(storeId, treatment) }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Couldn't save how the logo is drawn")
                true
            }
            progress.update { it.copy(styleFailed = failed) }
        }
    }

    /** White or black, whichever contrasts more with the brand [color] (#240); white on a colour that can't be read. */
    private fun defaultTint(color: String): String =
        if (color.toBrandColorOrNull()?.contrastingTextColor() == Color.Black) BLACK else WHITE

    // Reads the inputs, not `state`, so a guard holds whatever the screen observes.
    private fun canChange() =
        found.value is Found.Store && !progress.value.working && savedStateHandle.get<String>(CROP_LOGO) == null

    private data class Progress(val working: Boolean = false, val failed: Boolean = false, val styleFailed: Boolean = false)

    private sealed interface Found {
        data object Loading : Found
        data object Gone : Found
        data class Store(val store: com.bdaniel.loyscan.domain.Store) : Found
    }

    private companion object {
        const val CROP_LOGO = "cropLogo"
        const val ROUTE_PICTURE_TAKEN = "routePictureTaken"
        const val WHITE = "#FFFFFF"
        const val BLACK = "#000000"
    }
}
