package com.bdaniel.loyscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdaniel.loyscan.data.repository.StoreRepository
import com.bdaniel.loyscan.domain.search
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The stores a card can be added for, narrowed by a search over names and aliases. */
@HiltViewModel
class StorePickerViewModel @Inject constructor(
    storeRepository: StoreRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val stores = combine(storeRepository.getAll(), _query) { stores, query -> stores.search(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
