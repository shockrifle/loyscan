package com.bdaniel.loyscan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.Store
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorePickerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<String>()
    private var backs = 0
    private var customStores = 0
    private val edited = mutableListOf<String>()

    @Test
    fun showsEveryStore() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store"))

        composeRule.onNodeWithText("First Store").assertIsDisplayed()
        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
    }

    @Test
    fun typingInTheSearchField_showsOnlyTheMatchingStores() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store", aliases = listOf("Other")))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("other")

        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
        composeRule.onNodeWithText("First Store").assertDoesNotExist()
    }

    @Test
    fun aSearchThatMatchesNothing_saysSo() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("No store matches “xyz”").assertIsDisplayed()
    }

    @Test
    fun tappingAStore_picksIt() {
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "second", name = "Second Store"))

        composeRule.onNodeWithText("Second Store").performClick()

        assertThat(picked).containsExactly("second")
    }

    @Test
    fun otherCard_opensTheCustomStoreForm() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithText("Other card").performClick()

        assertThat(customStores).isEqualTo(1)
    }

    @Test
    fun otherCard_isOfferedWhenTheSearchMatchesNothing() {
        setScreen(aStore(id = "first", name = "First Store"))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("Other card").assertIsDisplayed()
    }

    @Test
    fun aCustomStoresEditButton_opensItsForm_andACatalogueStoreHasNone() { // #53
        setScreen(aStore(id = "first", name = "First Store"), aStore(id = "custom", name = "My Shop", isCustom = true))

        composeRule.onNodeWithContentDescription("Edit My Shop").performClick()

        assertThat(edited).containsExactly("custom")
        assertThat(picked).isEmpty()
        composeRule.onNodeWithContentDescription("Edit First Store").assertDoesNotExist()
    }

    @Test
    fun frequentlyAddedStores_comeFirst_underTheirOwnHeading_andCanBePicked() { // #6
        setScreen(aStore(id = "hu-zeta", name = "Zeta"), aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true))

        composeRule.onNodeWithText("Frequently added").assertIsDisplayed()
        composeRule.onNodeWithText("All stores").assertIsDisplayed()
        val tescoRows = composeRule.onAllNodesWithText("Tesco")
        tescoRows.assertCountEquals(2) // in both sections
        assertThat(tescoRows[0].fetchSemanticsNode().boundsInRoot.top)
            .isLessThan(composeRule.onNodeWithText("All stores").fetchSemanticsNode().boundsInRoot.top)
        tescoRows[0].performClick()

        assertThat(picked).containsExactly("hu-tesco")
    }

    @Test
    fun withoutAFrequentlyAddedStore_thereAreNoHeadings() { // #6
        setScreen(aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true), country = "AT")

        composeRule.onNodeWithText("Frequently added").assertDoesNotExist()
        composeRule.onNodeWithText("All stores").assertDoesNotExist()
        composeRule.onAllNodesWithText("Tesco").assertCountEquals(1)
    }

    @Test
    fun searching_hidesTheFrequentlyAddedSection() { // #6
        setScreen(aStore(id = "hu-tesco", name = "Tesco", frequentlyAdded = true))

        composeRule.onNodeWithTag(STORE_SEARCH_TAG).performTextInput("tes")

        composeRule.onNodeWithText("Frequently added").assertDoesNotExist()
        composeRule.onAllNodesWithText("Tesco").assertCountEquals(1)
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    private fun setScreen(vararg stores: Store, country: String? = "HU") {
        val viewModel = StorePickerViewModel(FakeStoreRepository(stores.toList())) { country }
        val directions = StorePickerDirections(
            toStore = { picked += it },
            toCustomStore = { customStores++ },
            toEditStore = { edited += it },
            toBack = { backs++ },
        )
        composeRule.setContent { StorePickerScreen(directions = directions, viewModel = viewModel) }
    }
}
