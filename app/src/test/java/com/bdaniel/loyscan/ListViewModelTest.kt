package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeUserPreferencesRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.keep
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
    private val photos = FakeCardPhotos()

    private val apple = aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple"), useCount = 1)
    private val berry = aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "Berry"), useCount = 5)
    private val cherry = aLoyaltyCard(id = cardId(3), store = aStore(id = "cherry", name = "Cherry"), label = "Apple pie", useCount = 3)

    private fun viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ListViewModel(repository, preferences, savedStateHandle, photos)

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

    @Test
    fun selecting_aCard_selectsIt_andSelectingItAgainDeselectsIt() = runTest { // #7: multi-select
        repository.cards.value = listOf(apple, berry, cherry)
        val viewModel = viewModel()

        viewModel.selection.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.toggleSelection(apple.id)
            assertThat(awaitItem()).containsExactly(apple.id)
            viewModel.toggleSelection(cherry.id)
            assertThat(awaitItem()).containsExactly(apple.id, cherry.id)
            viewModel.toggleSelection(apple.id)
            assertThat(awaitItem()).containsExactly(cherry.id)
        }
    }

    @Test
    fun clearingTheSelection_selectsNothing() = runTest {
        repository.cards.value = listOf(apple, berry)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)
        viewModel.toggleSelection(berry.id)

        viewModel.clearSelection()

        viewModel.selection.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun aSelectedCardDeletedElsewhere_leavesTheSelection() = runTest {
        repository.cards.value = listOf(apple, berry)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)
        viewModel.toggleSelection(berry.id)

        viewModel.selection.test {
            assertThat(awaitItem()).containsExactly(apple.id, berry.id)

            repository.cards.value = listOf(berry)

            assertThat(awaitItem()).containsExactly(berry.id)
        }
    }

    @Test
    fun changingTheQuery_clearsTheSelection() = runTest { // a card the search hides mustn't stay selected, nor be deleted
        repository.cards.value = listOf(apple, berry, cherry)
        val viewModel = viewModel()
        viewModel.toggleSelection(berry.id)

        viewModel.onQueryChange("apple")

        viewModel.selection.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun theSelection_isRestoredAfterProcessDeath() = runTest {
        repository.cards.value = listOf(apple, berry, cherry)
        val first = SavedStateHandle()
        viewModel(first).toggleSelection(berry.id)

        val restored = viewModel(SavedStateHandle(first.keys().associateWith { first.get<Any>(it) }))

        restored.selection.test {
            assertThat(expectMostRecentItem()).containsExactly(berry.id)
        }
    }

    @Test
    fun deletingTheSelection_deletesThoseCardsInOneWrite_andEndsTheSelection() = runTest {
        repository.cards.value = listOf(apple, berry, cherry)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)
        viewModel.toggleSelection(cherry.id)

        viewModel.deleteSelection()

        assertThat(repository.cards.value).containsExactly(berry)
        assertThat(repository.deleteCalls).isEqualTo(1)
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.NONE)
        viewModel.selection.test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun aSecondDeleteWhileTheFirstRuns_deletesOnce() = runTest { // a double tap on the dialog's Delete
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        repository.cards.value = listOf(apple, berry)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)

        viewModel.deleteSelection()
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.DELETING)
        viewModel.deleteSelection()
        gate.complete(Unit)

        assertThat(repository.deleteCalls).isEqualTo(1)
        assertThat(repository.cards.value).containsExactly(berry)
    }

    @Test
    fun aDeleteStillRunningWhenTheListIsCleared_isStillDone() = runTest { // as on the card screen (#65)
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        repository.cards.value = listOf(apple, berry)
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel() }
        viewModel.toggleSelection(apple.id)
        viewModel.deleteSelection()

        owner.clear()
        gate.complete(Unit)

        assertThat(repository.cards.value).containsExactly(berry)
    }

    @Test
    fun aFailedDelete_saysSo_keepsTheSelection_andCanBeTriedAgain() = runTest { // a failed write mustn't crash (#49)
        repository.deleteFailure = IllegalStateException("disk I/O error")
        repository.cards.value = listOf(apple, berry)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)

        viewModel.deleteSelection()

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.FAILED)
        assertThat(repository.cards.value).containsExactly(apple, berry)
        viewModel.selection.test {
            assertThat(awaitItem()).containsExactly(apple.id)
        }

        repository.deleteFailure = null
        viewModel.deleteSelection()

        assertThat(repository.cards.value).containsExactly(berry)
        assertThat(viewModel.deletion.value).isEqualTo(Deletion.NONE)
    }

    @Test
    fun aFrontPhotoWhoseFileIsMissing_isLeftOffTheTile() = runTest { // #89: a cloud restore brings the row, not the file
        photos.stored += "front"
        repository.cards.value = listOf(apple.copy(frontPhotoPath = "front"), berry.copy(frontPhotoPath = "lost"))

        viewModel().cards.test {
            assertThat(awaitItem()!!.map { it.frontPhotoPath }).containsExactly("front", null).inOrder()
        }
    }

    @Test
    fun aStoresLogo_isLookedUpOnce_forAllItsCards() = runTest { // #151 review: not one file check per card
        photos.stored += "logo"
        val store = aStore(id = "shop", logo = "logo")
        repository.cards.value = (1..3).map { aLoyaltyCard(id = cardId(it), store = store) }

        viewModel().cards.test {
            assertThat(awaitItem()!!.map { it.store.logo }).containsExactly("logo", "logo", "logo")
        }

        assertThat(photos.existsCalls.count { it == "logo" }).isEqualTo(1)
    }

    @Test
    fun aStoresLogoWhoseFileIsMissing_isLeftOffItsTiles() = runTest { // #151
        repository.cards.value = listOf(apple.copy(store = apple.store.copy(logo = "lost")))

        viewModel().cards.test {
            assertThat(awaitItem()!!.single().store.logo).isNull()
        }
    }

    @Test
    fun dismissingAFailedDelete_forgetsTheFailure() = runTest {
        repository.deleteFailure = IllegalStateException("disk I/O error")
        repository.cards.value = listOf(apple)
        val viewModel = viewModel()
        viewModel.toggleSelection(apple.id)
        viewModel.deleteSelection()

        viewModel.dismissDeletion()

        assertThat(viewModel.deletion.value).isEqualTo(Deletion.NONE)
    }
}
