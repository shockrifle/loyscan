package com.bdaniel.loyscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    loyaltyCardRepository: LoyaltyCardRepository
) : ViewModel() {

    val cards = loyaltyCardRepository.getAllWithStore().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}