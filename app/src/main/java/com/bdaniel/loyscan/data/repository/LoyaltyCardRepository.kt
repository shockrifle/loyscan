package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.Flow

/** Cards with their brand, as domain models. ViewModels depend on this; tests swap in a fake. */
interface LoyaltyCardRepository {

    fun getAllWithBrand(): Flow<List<LoyaltyCard>>

    fun getByIdWithBrand(id: Int): Flow<LoyaltyCard>
}
