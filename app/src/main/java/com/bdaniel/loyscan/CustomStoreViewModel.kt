package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.Store
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** What the custom-store form shows. */
data class CustomStoreState(
    val name: String = "",
    /** One of [CustomStoreViewModel.COLORS], as `#RRGGBB`. */
    val color: String = CustomStoreViewModel.COLORS.first().hex,
    /** The store's barcode format for typed numbers; a scan keeps its own. */
    val format: BarcodeFormat = BarcodeFormat.CODE_128,
    val saving: Boolean = false,
    /** The last save failed; the form stays open to try again. */
    val saveFailed: Boolean = false,
    /** The id of the created store, once it is saved. */
    val createdStoreId: String? = null,
    /** The form edits an existing custom store (#53) rather than creating one. */
    val editing: Boolean = false,
    /** False while the edited store is loading, or when it is gone: the fields aren't shown. */
    val ready: Boolean = true,
    /** No custom store has the edited id (deleted, or a catalogue store's id). */
    val storeGone: Boolean = false,
    /** The edit is saved. */
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = ready && name.isNotBlank() && !saving && createdStoreId == null && !saved
}

/**
 * "Other card" in the store picker (#6): a store that isn't in the catalogue, with a name, a colour and the barcode
 * format its typed numbers are drawn in. Creating it saves it, so the card is then added for it like for any store.
 * With [CustomStore.storeId] it edits that custom store instead (#53): the form starts as the store.
 */
@HiltViewModel
class CustomStoreViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val storeRepository: StoreRepository,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CustomStore>()

    /** The store being edited, or [Edited.New] when creating one. */
    private val edited: StateFlow<Edited> =
        route.storeId?.let { id ->
            storeRepository.getAll()
                .map { stores -> stores.firstOrNull { it.id == id && it.isCustom }?.let(Edited::Found) ?: Edited.Gone }
                .stateIn(viewModelScope, SharingStarted.Eagerly, Edited.Loading)
        } ?: MutableStateFlow(Edited.New)

    // `null` until changed: the edited store's value, or a new store's default.
    private val name = savedStateHandle.getStateFlow<String?>(NAME, null)
    private val color = savedStateHandle.getStateFlow<String?>(COLOR, null)
    private val format = savedStateHandle.getStateFlow<BarcodeFormat?>(FORMAT, null)
    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<CustomStoreState> =
        combine(edited, name, color, format, progress) { edited, name, color, format, progress ->
            val inputs = inputs(edited, name, color, format)
            CustomStoreState(
                name = inputs.name,
                color = inputs.color,
                format = inputs.format,
                saving = progress.saving,
                saveFailed = progress.saveFailed,
                createdStoreId = progress.createdStoreId,
                editing = route.storeId != null,
                ready = edited is Edited.New || edited is Edited.Found,
                storeGone = edited is Edited.Gone,
                saved = progress.saved,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomStoreState(editing = route.storeId != null, ready = route.storeId == null)
        )

    fun onNameChange(name: String) {
        savedStateHandle[NAME] = name
    }

    fun onColorChange(color: String) {
        savedStateHandle[COLOR] = color
    }

    fun onFormatChange(format: BarcodeFormat) {
        savedStateHandle[FORMAT] = format
    }

    /**
     * Creates the store once; [CustomStoreState.createdStoreId] then names it. When editing, saves the edit once;
     * [CustomStoreState.saved] then says so. If saving fails, [CustomStoreState.saveFailed] says so and it can be
     * saved again.
     */
    fun save() {
        // Reads the inputs, not `state`, which stops updating without a subscriber.
        val current = progress.value
        val edited = edited.value
        val inputs = inputs(edited, name.value, color.value, format.value)
        val name = inputs.name.trim()
        if (name.isEmpty() || current.saving || current.createdStoreId != null || current.saved) return
        val store = when (edited) {
            Edited.New -> Store.EMPTY.copy(id = UUID.randomUUID().toString(), isCustom = true)
            is Edited.Found -> edited.store
            Edited.Loading, Edited.Gone -> return
        }.copy(name = name, color = inputs.color, defaultBarcodeFormat = inputs.format, updatedAt = clock.instant())
        progress.value = Progress(saving = true)
        viewModelScope.launch {
            progress.value = try {
                if (edited is Edited.Found) {
                    storeRepository.update(store)
                    Progress(saved = true)
                } else {
                    storeRepository.add(store)
                    Progress(createdStoreId = store.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // as a failed card save (#49): the form stays open
                Timber.e(e, "Couldn't save the store")
                Progress(saveFailed = true)
            }
        }
    }

    /** The fields as shown: what was changed, else the edited store's, else a new store's defaults. */
    private fun inputs(edited: Edited, name: String?, color: String?, format: BarcodeFormat?): Inputs {
        val store = (edited as? Edited.Found)?.store
        return Inputs(
            name = name ?: store?.name.orEmpty(),
            color = color ?: store?.color ?: COLORS.first().hex,
            format = format ?: store?.defaultBarcodeFormat ?: BarcodeFormat.CODE_128,
        )
    }

    private data class Inputs(val name: String, val color: String, val format: BarcodeFormat)

    private sealed interface Edited {
        data object New : Edited
        data object Loading : Edited
        data object Gone : Edited
        data class Found(val store: Store) : Edited
    }

    private data class Progress(
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val createdStoreId: String? = null,
        val saved: Boolean = false,
    )

    /** A colour a custom store can have; [name] is what a screen reader says. */
    data class Swatch(val name: String, val hex: String)

    companion object {
        val COLORS: kotlin.collections.List<Swatch> = listOf(
            Swatch("Blue", "#1E5AA8"),
            Swatch("Red", "#C62828"),
            Swatch("Green", "#2E7D32"),
            Swatch("Orange", "#EF6C00"),
            Swatch("Purple", "#6A1B9A"),
            Swatch("Teal", "#00796B"),
            Swatch("Brown", "#5D4037"),
            Swatch("Grey", "#455A64"),
        )

        /** Decided in #47: [BarcodeFormat.forCustomStores]. */
        val FORMATS: kotlin.collections.List<BarcodeFormat> = BarcodeFormat.forCustomStores

        private const val NAME = "name"
        private const val COLOR = "color"
        private const val FORMAT = "format"
    }
}
