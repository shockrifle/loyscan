package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.Store
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

/** What the custom-store form shows. */
data class CustomStoreState(
    val name: String = "",
    /** One of [CustomStoreViewModel.COLORS], as `#RRGGBB`. */
    val color: String = CustomStoreViewModel.COLORS.first().hex,
    /** The store's barcode format for typed numbers; a scan keeps its own. */
    val format: BarcodeFormat = BarcodeFormat.CODE_128,
    val creating: Boolean = false,
    /** The last create failed; the form stays open to try again. */
    val createFailed: Boolean = false,
    /** The id of the created store, once it is saved. */
    val createdStoreId: String? = null,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !creating && createdStoreId == null
}

/**
 * "Other card" in the store picker (#6): a store that isn't in the catalogue, with a name, a colour and the barcode
 * format its typed numbers are drawn in. Creating it saves it, so the card is then added for it like for any store.
 */
@HiltViewModel
class CustomStoreViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val storeRepository: StoreRepository,
    private val clock: Clock,
) : ViewModel() {

    private val name = savedStateHandle.getStateFlow(NAME, "")
    private val color = savedStateHandle.getStateFlow(COLOR, COLORS.first().hex)
    private val format = savedStateHandle.getStateFlow(FORMAT, BarcodeFormat.CODE_128)
    private val progress = MutableStateFlow(Progress())

    val state: StateFlow<CustomStoreState> =
        combine(name, color, format, progress) { name, color, format, progress ->
            CustomStoreState(
                name = name,
                color = color,
                format = format,
                creating = progress.creating,
                createFailed = progress.createFailed,
                createdStoreId = progress.createdStoreId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomStoreState()
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
     * Saves the store once; [CustomStoreState.createdStoreId] then names it. If saving fails,
     * [CustomStoreState.createFailed] says so and it can be created again.
     */
    fun create() {
        // Reads the inputs, not `state`, which stops updating without a subscriber.
        val current = progress.value
        val name = name.value.trim()
        if (name.isEmpty() || current.creating || current.createdStoreId != null) return
        progress.value = Progress(creating = true)
        val store = Store.EMPTY.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            color = color.value,
            defaultBarcodeFormat = format.value,
            isCustom = true,
            updatedAt = clock.instant(),
        )
        viewModelScope.launch {
            progress.value = try {
                storeRepository.add(store)
                Progress(createdStoreId = store.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // as a failed card save (#49): the form stays open
                Timber.e(e, "Couldn't save the store")
                Progress(createFailed = true)
            }
        }
    }

    private data class Progress(
        val creating: Boolean = false,
        val createFailed: Boolean = false,
        val createdStoreId: String? = null,
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
