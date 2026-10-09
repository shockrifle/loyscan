package com.bdaniel.loyscan

import app.cash.turbine.test
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Example of a plain-JVM ViewModel test: fake repository, Turbine on the exposed StateFlow. */
class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoyaltyCardRepository()

    @Test
    fun cards_followTheRepository() = runTest {
        val viewModel = ListViewModel(repository)

        viewModel.cards.test {
            assertThat(awaitItem()).isEmpty()

            val card = aLoyaltyCard()
            repository.cards.value = listOf(card)
            assertThat(awaitItem()).containsExactly(card)
        }
    }
}
