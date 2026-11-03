package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.device.UserCountry
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.domain.frequentlyAddedIn
import com.bdaniel.loyscan.domain.matchingScan
import com.bdaniel.loyscan.domain.search
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The stores a card can be added for, narrowed by a search over names and aliases, and the frequently added ones of
 * the user's country, offered first while nothing is searched (#6). For a shared picture's barcode ([route]), the
 * stores whose rule reads it are suggested above those (#77).
 */
@HiltViewModel
class StorePickerViewModel(
    storeRepository: StoreRepository,
    route: StorePicker = StorePicker(),
    userCountry: UserCountry,
) : ViewModel() {

    @Inject
    constructor(storeRepository: StoreRepository, savedStateHandle: SavedStateHandle, userCountry: UserCountry) :
        this(storeRepository, savedStateHandle.toRoute<StorePicker>(), userCountry)

    private val country = userCountry.code()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val stores = combine(storeRepository.getAll(), _query) { stores, query -> stores.search(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * The stores whose card-number rule reads the shared picture's barcode, ranked as the card form ranks them
     * ([matchingScan]); none while searching, or without a barcode. Nothing is picked for the user.
     */
    val suggested: StateFlow<List<Store>> =
        combine(storeRepository.getAll(), _query) { stores, query ->
            val content = route.scannedContent
            val format = route.scannedFormat
            if (query.isBlank() && content != null && format != null) stores.matchingScan(content, format) else emptyList()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val frequentlyAdded: StateFlow<List<Store>> =
        combine(storeRepository.getAll(), _query) { stores, query ->
            if (query.isBlank()) stores.frequentlyAddedIn(country) else emptyList()
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
