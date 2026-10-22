package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example of a ViewModel that reads its navigation route. `SavedStateHandle(route = ...)` is backed by a Bundle,
 * so this runs on Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(id = cardId(1))))

    @Test
    fun card_isTheOneNamedByTheRoute() = runTest {
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(2), color = "#336699")), repository)

        viewModel.card.test {
            assertThat(awaitItem()).isEqualTo(LoyaltyCard.EMPTY)

            val card = aLoyaltyCard(id = cardId(2))
            repository.cards.value += card
            assertThat(awaitItem()).isEqualTo(card)
        }
    }

    @Test
    fun opening_countsOneUseOfTheRoutedCardOnly() = runTest {
        repository.cards.value += aLoyaltyCard(id = cardId(2), useCount = 4)

        DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(2), color = "#336699")), repository)

        val (other, opened) = repository.cards.value
        assertThat(opened.useCount).isEqualTo(5)
        assertThat(opened.lastUsedAt).isEqualTo(SOME_INSTANT)
        assertThat(other.useCount).isEqualTo(0)
    }

    @Test
    fun opening_aCardJustAdded_countsNoUse() {
        repository.cards.value += aLoyaltyCard(id = cardId(2))

        DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(2), color = "#336699", countUse = false)), repository)

        assertThat(repository.cards.value.last().useCount).isEqualTo(0)
    }

    @Test
    fun card_staysEmpty_whileNoCardHasTheId() = runTest {
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(99), color = "#336699")), repository)

        viewModel.card.test {
            assertThat(awaitItem()).isEqualTo(LoyaltyCard.EMPTY)
            repository.cards.value += aLoyaltyCard(id = cardId(2))
            expectNoEvents()
        }
    }

    @Test
    fun card_keepsTheLastCard_whenItIsDeleted() = runTest {
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(1), color = "#336699")), repository)

        viewModel.card.test {
            assertThat(awaitItem().id).isEqualTo(cardId(1))
            repository.cards.value = emptyList()
            expectNoEvents()
        }
    }

    @Test
    fun delete_deletesTheRoutedCard_andSaysSo() = runTest {
        repository.cards.value += aLoyaltyCard(id = cardId(2))
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(2), color = "#336699")), repository)

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.NONE)

        viewModel.delete()

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.DELETED)
        assertThat(repository.cards.value.map { it.id }).containsExactly(cardId(1))
    }

    @Test
    fun delete_whileDeleting_orOnceDeleted_isIgnored() = runTest { // review of #8: a double tap deletes once
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(1), color = "#336699")), repository)

        viewModel.delete()
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.DELETING)
        viewModel.delete()
        gate.complete(Unit)
        viewModel.delete()

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.DELETED)
        assertThat(repository.deleteCalls).isEqualTo(1)
    }

    @Test
    fun dismissingAFailedDelete_forgetsTheFailure() = runTest { // review of #8
        repository.deleteFailure = IllegalStateException("disk I/O error")
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(1), color = "#336699")), repository)
        viewModel.delete()

        viewModel.dismissDeletion()

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.NONE)
    }

    @Test
    fun delete_thatFails_keepsTheCard_saysSo_andCanBeTriedAgain() = runTest { // the delete mustn't crash, as a save (#49)
        repository.deleteFailure = IllegalStateException("disk I/O error")
        val viewModel = DetailViewModel(SavedStateHandle(route = Detail(cardId = cardId(1), color = "#336699")), repository)

        viewModel.delete()
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.FAILED)
        assertThat(repository.cards.value).hasSize(1)

        repository.deleteFailure = null
        viewModel.delete()
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.DELETED)
        assertThat(repository.cards.value).isEmpty()
    }
}
