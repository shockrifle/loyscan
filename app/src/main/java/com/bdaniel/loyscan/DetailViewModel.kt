package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    loyaltyCardRepository: LoyaltyCardRepository
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
}