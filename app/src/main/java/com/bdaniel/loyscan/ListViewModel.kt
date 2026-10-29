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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * The user's cards in the chosen sort order (#7), narrowed by a search, and the ones selected to be deleted or edited.
 * The sort is a stored preference; the query and the selection live in the saved state, so they survive process death
 * but not a restart.
 */
@HiltViewModel
class ListViewModel @Inject constructor(
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY, "")

    val sort: StateFlow<CardSort> = userPreferencesRepository.cardSort.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardSort.NAME
    )

    /** `null` until the cards and the sort have loaded, so an empty result is told apart from not knowing yet. */
    val cards: StateFlow<List<LoyaltyCard>?> =
        combine(
            // With no card left (the last one deleted), an old search would hide the empty state and the next card.
            loyaltyCardRepository.getAllWithStore().onEach { if (it.isEmpty()) savedStateHandle[QUERY] = "" },
            userPreferencesRepository.cardSort,
            query,
        ) { cards, sort, query ->
            cards.sortedBy(sort).search(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** A new search ends the selection, so a card the search hides is never selected, nor deleted. */
    fun onQueryChange(query: String) {
        if (query != this.query.value) clearSelection()
        savedStateHandle[QUERY] = query
    }

    /** A sort that can't be saved is not applied: the list keeps the stored one. */
    fun onSortChange(sort: CardSort) {
        viewModelScope.launch {
            try {
                userPreferencesRepository.setCardSort(sort)
            } catch (exception: IOException) {
                Timber.e(exception, "Couldn't save the card sort")
            }
        }
    }

    private val storedSelection: StateFlow<List<String>> = savedStateHandle.getStateFlow(SELECTION, emptyList())

    /**
     * The ids of the selected cards (long-press multi-select), in the saved state. A card deleted elsewhere drops out;
     * until the cards have loaded (after process death), the saved ids stand.
     */
    val selection: StateFlow<Set<String>> =
        combine(storedSelection, cards) { selected, cards ->
            if (cards == null) selected.toSet() else cards.map { it.id }.filter { it in selected }.toSet()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    private val _deletion = MutableStateFlow(Deletion.NONE)

    /** Where deleting the selected cards stands. Once they are gone, the selection ends and this is back to NONE. */
    val deletion: StateFlow<Deletion> = _deletion.asStateFlow()

    /** Selects the card, or deselects it if it is selected. */
    fun toggleSelection(id: String) {
        val selected = storedSelection.value
        savedStateHandle[SELECTION] = ArrayList(if (id in selected) selected - id else selected + id)
    }

    fun clearSelection() {
        savedStateHandle[SELECTION] = ArrayList<String>()
    }

    /** Deletes the selected cards in one write. If that fails, [deletion] says so and it can be tried again. */
    fun deleteSelection() {
        if (_deletion.value == Deletion.DELETING) return
        // From the saved state, not [selection], which only follows while the screen observes it.
        val ids = storedSelection.value.toSet()
        _deletion.value = Deletion.DELETING
        viewModelScope.launch {
            _deletion.value = try {
                // A delete still running when the list is cleared is finished, as on the card screen (#65).
                withContext(NonCancellable) { loyaltyCardRepository.deleteAll(ids) }
                clearSelection()
                Deletion.NONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // a failed delete mustn't crash the list, as a failed save mustn't (#49)
                Timber.e(e, "Couldn't delete the cards")
                Deletion.FAILED
            }
        }
    }

    /** The delete dialog was closed: a failure is forgotten, so the dialog opens afresh next time. */
    fun dismissDeletion() {
        if (_deletion.value == Deletion.FAILED) _deletion.value = Deletion.NONE
    }

    private companion object {
        const val QUERY = "query"
        const val SELECTION = "selection"
    }
}
