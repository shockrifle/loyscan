package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example of a Compose screen test on Robolectric. The screen gets a ViewModel built on a fake repository and
 * recording directions; shared-transition scopes come from a real `SharedTransitionLayout`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingACard_navigatesToItWithItsStoreColor() {
        val card = aLoyaltyCard(id = cardId(5), store = aStore(name = "Test Store", color = "#AA0000"))
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(listOf(card)))
        var navigatedTo: Pair<String, String>? = null
        val directions = ListDirections(toCard = { id, color -> navigatedTo = id to color }, toAddCard = {}, toBack = {})

        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                }
            }
        }
        composeRule.onNodeWithText("Test Store").performClick()

        assertThat(navigatedTo).isEqualTo(cardId(5) to "#AA0000")
    }

    @Test
    fun showsATileForEachCard() {
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "First Store")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Second Store")))

        composeRule.onNodeWithText("First Store").assertIsDisplayed()
        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
    }

    @Test
    fun emptyOrMalformedStoreColor_doesNotCrash() { // #14
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "No Colour", color = "")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Bad Colour", color = "not a colour")))

        composeRule.onNodeWithText("No Colour").assertIsDisplayed()
        composeRule.onNodeWithText("Bad Colour").assertIsDisplayed()
    }

    @Test
    fun addCard_startsAddingACard() {
        var addCardOpened = 0
        setScreen(directions = ListDirections(toCard = { _, _ -> }, toAddCard = { addCardOpened++ }, toBack = {}))

        composeRule.onNodeWithContentDescription("Add card").performClick()

        assertThat(addCardOpened).isEqualTo(1)
    }

    @Test
    fun theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd() {
        val cards = (1..12).map { aLoyaltyCard(id = cardId(it), store = aStore(name = "Store $it")) }
        setScreen(*cards.toTypedArray())

        composeRule.onNode(hasScrollAction()).performScrollToIndex(cards.lastIndex)

        val lastTile = composeRule.onNodeWithText("Store 12").fetchSemanticsNode().boundsInRoot
        val addButton = composeRule.onNodeWithContentDescription("Add card").fetchSemanticsNode().boundsInRoot
        assertThat(lastTile.overlaps(addButton)).isFalse()
    }

    private fun setScreen(
        vararg cards: LoyaltyCard,
        directions: ListDirections = ListDirections(toCard = { _, _ -> }, toAddCard = {}, toBack = {}),
    ) {
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(cards.toList()))
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                }
            }
        }
    }
}
