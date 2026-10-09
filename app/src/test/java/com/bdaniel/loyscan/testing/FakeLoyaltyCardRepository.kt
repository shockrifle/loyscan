package com.bdaniel.loyscan.testing

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [LoyaltyCardRepository]. Set [cards] to drive what observers see. */
class FakeLoyaltyCardRepository(cards: List<LoyaltyCard> = emptyList()) : LoyaltyCardRepository {

    val cards = MutableStateFlow(cards)

    override fun getAllWithBrand(): Flow<List<LoyaltyCard>> = cards

    override fun getByIdWithBrand(id: Int): Flow<LoyaltyCard?> =
        cards.map { all -> all.firstOrNull { it.id == id } }
}
