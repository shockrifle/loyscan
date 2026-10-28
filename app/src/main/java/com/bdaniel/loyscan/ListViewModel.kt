package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.search
import com.bdaniel.loyscan.domain.sortedBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's cards in the chosen sort order (#7), narrowed by a search. The sort is a stored preference; the query
 * lives in the saved state, so it survives process death but not a restart.
 */
@HiltViewModel
class ListViewModel @Inject constructor(
    loyaltyCardRepository: LoyaltyCardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY, "")

    val sort: StateFlow<CardSort> = userPreferencesRepository.cardSort.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardSort.NAME
    )

    val cards: StateFlow<List<LoyaltyCard>> =
        combine(loyaltyCardRepository.getAllWithStore(), userPreferencesRepository.cardSort, query) { cards, sort, query ->
            cards.sortedBy(sort).search(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onQueryChange(query: String) {
        savedStateHandle[QUERY] = query
    }

    fun onSortChange(sort: CardSort) {
        viewModelScope.launch { userPreferencesRepository.setCardSort(sort) }
    }

    private companion object {
        const val QUERY = "query"
    }
}
