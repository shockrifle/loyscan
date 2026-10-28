package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeUserPreferencesRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Example of a plain-JVM ViewModel test: fake repositories, Turbine on the exposed StateFlow. */
class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeLoyaltyCardRepository()
    private val preferences = FakeUserPreferencesRepository()

    private val apple = aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple"), useCount = 1)
    private val berry = aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "Berry"), useCount = 5)
    private val cherry = aLoyaltyCard(id = cardId(3), store = aStore(id = "cherry", name = "Cherry"), label = "Apple pie", useCount = 3)

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ListViewModel(repository, preferences, savedStateHandle)

    @Test
    fun cards_followTheRepository() = runTest {
        repository.loadGate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.cards.test {
            assertThat(awaitItem()).isNull() // not loaded yet
            repository.loadGate!!.complete(Unit)
            assertThat(awaitItem()).isEmpty()

            val card = aLoyaltyCard()
            repository.cards.value = listOf(card)
            assertThat(awaitItem()).containsExactly(card)
        }
    }

    @Test
    fun cards_reflectAdditionsAndRemovals() = runTest {
        repository.cards.value = listOf(apple)
        val viewModel = viewModel()

        viewModel.cards.test {
            assertThat(awaitItem()).containsExactly(apple)
            repository.cards.value = listOf(apple, berry)
            assertThat(awaitItem()).containsExactly(apple, berry).inOrder()
            repository.cards.value = listOf(berry)
            assertThat(awaitItem()).containsExactly(berry)
        }
    }

    @Test
    fun cards_areInTheStoredSortOrder() = runTest {
        repository.cards.value = listOf(berry, cherry, apple)
        preferences.cardSort.value = CardSort.MOST_USED

        viewModel().cards.test {
            assertThat(awaitItem()).containsExactly(berry, cherry, apple).inOrder()
        }
    }

    @Test
    fun withNoSortChosen_cardsAreByName() = runTest {
        repository.cards.value = listOf(cherry, berry, apple)

        viewModel().cards.test {
            assertThat(awaitItem()).containsExactly(apple, berry, cherry).inOrder()
        }
    }

    @Test
    fun choosingASort_storesIt_andReordersTheCards() = runTest {
        repository.cards.value = listOf(apple, berry, cherry)
        val viewModel = viewModel()

        viewModel.cards.test {
            assertThat(awaitItem()).containsExactly(apple, berry, cherry).inOrder()

            viewModel.onSortChange(CardSort.MOST_USED)

            assertThat(awaitItem()).containsExactly(berry, cherry, apple).inOrder()
        }
        assertThat(preferences.cardSort.value).isEqualTo(CardSort.MOST_USED)
    }

    @Test
    fun aSortThatFailsToSave_leavesTheSortAsItWas() = runTest {
        preferences.setFailure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onSortChange(CardSort.MOST_USED)

        assertThat(preferences.cardSort.value).isEqualTo(CardSort.NAME)
    }

    @Test
    fun sort_isTheStoredOne() = runTest {
        preferences.cardSort.value = CardSort.RECENTLY_USED

        viewModel().sort.test {
            assertThat(expectMostRecentItem()).isEqualTo(CardSort.RECENTLY_USED)
        }
    }

    @Test
    fun aQuery_narrowsTheCards_keepingTheSortOrder() = runTest {
        repository.cards.value = listOf(apple, berry, cherry)
        preferences.cardSort.value = CardSort.MOST_USED
        val viewModel = viewModel()

        viewModel.cards.test {
            assertThat(awaitItem()).containsExactly(berry, cherry, apple).inOrder()

            viewModel.onQueryChange("apple")

            assertThat(awaitItem()).containsExactly(cherry, apple).inOrder()
        }
        assertThat(viewModel.query.value).isEqualTo("apple")
    }

    @Test
    fun theQuery_isRestoredAfterProcessDeath() = runTest {
        repository.cards.value = listOf(apple, berry, cherry)
        val first = SavedStateHandle()
        viewModel(first).onQueryChange("berry")

        val restored = viewModel(SavedStateHandle(first.keys().associateWith { first.get<Any>(it) }))

        assertThat(restored.query.value).isEqualTo("berry")
        restored.cards.test {
            assertThat(awaitItem()).containsExactly(berry)
        }
    }

    @Test
    fun deletingTheLastCard_clearsTheQuery() = runTest { // review of #7
        repository.cards.value = listOf(berry)
        val viewModel = viewModel()
        viewModel.onQueryChange("berry")

        viewModel.cards.test {
            assertThat(awaitItem()).containsExactly(berry)

            repository.cards.value = emptyList()

            // An old search would hide the empty state, and the next card added.
            assertThat(expectMostRecentItem()).isEmpty()
        }
        assertThat(viewModel.query.value).isEmpty()
    }
}
