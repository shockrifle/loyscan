package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loyaltyCardRepository: LoyaltyCardRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Detail>()
    private val cardId = route.cardId

    init {
        // Once per opened card: the ViewModel outlives rotation, so turning the phone doesn't count again.
        if (route.countUse) viewModelScope.launch { loyaltyCardRepository.recordUse(cardId) }
    }

    val card: StateFlow<LoyaltyCard> =
        loyaltyCardRepository.getByIdWithStore(cardId)
            .filterNotNull()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LoyaltyCard.EMPTY
            )

    private val _deletion = MutableStateFlow(Deletion.NONE)

    /** Where deleting the card stands; [Deletion.DELETED] once it is gone. */
    val deletion: StateFlow<Deletion> = _deletion.asStateFlow()

    /** Deletes the card (#8). If that fails, [deletion] says so and it can be tried again. */
    fun delete() {
        if (_deletion.value == Deletion.DELETING || _deletion.value == Deletion.DELETED) return
        _deletion.value = Deletion.DELETING
        viewModelScope.launch {
            _deletion.value = try {
                loyaltyCardRepository.delete(cardId)
                Deletion.DELETED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // a failed delete mustn't crash the screen, as a failed save mustn't (#49)
                Timber.e(e, "Couldn't delete the card")
                Deletion.FAILED
            }
        }
    }

    /** The delete dialog was closed: a failure is forgotten, so the dialog opens afresh next time. */
    fun dismissDeletion() {
        if (_deletion.value == Deletion.FAILED) _deletion.value = Deletion.NONE
    }
}