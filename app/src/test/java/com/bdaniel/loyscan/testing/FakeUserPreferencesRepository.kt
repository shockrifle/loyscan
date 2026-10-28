package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import com.bdaniel.loyscan.domain.CardSort
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [UserPreferencesRepository]. Set [cardSort] to drive what observers see. */
class FakeUserPreferencesRepository(cardSort: CardSort = CardSort.NAME) : UserPreferencesRepository {

    override val cardSort = MutableStateFlow(cardSort)

    override suspend fun setCardSort(sort: CardSort) {
        cardSort.value = sort
    }
}
