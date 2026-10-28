package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.CardSort
import kotlinx.coroutines.flow.Flow

/** The user's choices that outlive the process (#7). */
interface UserPreferencesRepository {

    /** How the card list is sorted; [CardSort.NAME] until the user chooses. */
    val cardSort: Flow<CardSort>

    suspend fun setCardSort(sort: CardSort)
}
