package com.bdaniel.loyscan

import app.cash.turbine.test
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class StorePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val first = aStore(id = "first", name = "First Store")
    private val second = aStore(id = "second", name = "Second Store", aliases = listOf("Other"))
    private val repository = FakeStoreRepository(listOf(second, first))

    @Test
    fun stores_areEveryStoreByName_andFollowTheRepository() = runTest {
        val viewModel = StorePickerViewModel(repository)

        viewModel.stores.test {
            assertThat(awaitItem()).containsExactly(first, second).inOrder()

            val third = aStore(id = "third", name = "Third Store")
            repository.stores.value += third
            assertThat(awaitItem()).containsExactly(first, second, third).inOrder()
        }
    }

    @Test
    fun query_narrowsTheStores_andClearingItShowsThemAll() = runTest {
        val viewModel = StorePickerViewModel(repository)

        viewModel.stores.test {
            skipItems(1)

            viewModel.onQueryChange("other")
            assertThat(viewModel.query.value).isEqualTo("other")
            assertThat(awaitItem()).containsExactly(second)

            viewModel.onQueryChange("")
            assertThat(awaitItem()).containsExactly(first, second).inOrder()
        }
    }
}
