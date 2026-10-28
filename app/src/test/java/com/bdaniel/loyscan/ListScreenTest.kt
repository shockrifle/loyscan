package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeUserPreferencesRepository
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
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
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(listOf(card)), FakeUserPreferencesRepository(), SavedStateHandle())
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
    fun aTile_showsTheStoresInitials_andTheCardsLabel() {
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = "Work car"))

        composeRule.onNodeWithText("SC").assertIsDisplayed()
        composeRule.onNodeWithText("Work car").assertIsDisplayed()
        composeRule.onNodeWithText("Shell ClubSmart").assertDoesNotExist()
    }

    @Test
    fun aTile_isReadOutAsTheStoreName_andTheLabel() { // review of #7
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Shell ClubSmart"), label = "Work car"), aLoyaltyCard(id = cardId(2), store = aStore(name = "Penny"), label = null))

        // TalkBack reads a merged node's content description in place of its text.
        composeRule.onNodeWithText("Work car").assertContentDescriptionEquals("Shell ClubSmart, Work car")
        composeRule.onNodeWithText("Penny").assertContentDescriptionEquals("Penny")
    }

    @Test
    fun aTilesInitials_andLabel_doNotOverlap_atALargeFontScale() { // review of #7
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = "Work car"), fontScale = 2f)

        val initials = composeRule.onNodeWithText("SC", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("Work car", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tile = composeRule.onNodeWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        // Robolectric's text widths are near zero, so only the vertical layout can be checked.
        assertThat(initials.bottom).isAtMost(label.top)
        assertThat(tile.contains(label.bottomRight - Offset(1f, 1f))).isTrue()
    }

    @Test
    fun aTileWithoutALabel_showsTheStoreName() {
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = null))

        composeRule.onNodeWithText("SC").assertIsDisplayed()
        composeRule.onNodeWithText("Shell ClubSmart").assertIsDisplayed()
    }

    @Test
    fun aTile_hasBankCardProportions() {
        setScreen(aLoyaltyCard())

        val tile = composeRule.onNodeWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        // ID-1 cards are 85.60 × 53.98 mm; a pixel of rounding either way.
        assertThat(tile.width / tile.height).isWithin(0.02f).of(85.60f / 53.98f)
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
    fun noCards_offersToAddTheFirstOne_withoutASearchField() {
        var addCardOpened = 0
        setScreen(directions = ListDirections(toCard = { _, _ -> }, toAddCard = { addCardOpened++ }, toBack = {}))

        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Add your first card").performClick()

        assertThat(addCardOpened).isEqualTo(1)
    }

    @Test
    fun theEmptyState_waitsForTheCardsToLoad() {
        val repository = FakeLoyaltyCardRepository(emptyList())
        repository.loadGate = CompletableDeferred()
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle()))

        composeRule.onNodeWithText("No cards yet").assertDoesNotExist()
        repository.loadGate!!.complete(Unit)
        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
    }

    @Test
    fun aSearchThatMatchesNothing_isNotTheEmptyState() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("No card matches “xyz”").assertIsDisplayed()
        composeRule.onNodeWithText("No cards yet").assertDoesNotExist()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).assertIsDisplayed()
    }

    @Test
    fun theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd() {
        // Zero-padded, so name order is this order.
        val cards = (1..12).map { aLoyaltyCard(id = cardId(it), store = aStore(name = "Store %02d".format(it))) }
        setScreen(*cards.toTypedArray())

        composeRule.onNode(hasScrollAction()).performScrollToIndex(cards.lastIndex)

        val lastTile = composeRule.onNodeWithText("Store 12").fetchSemanticsNode().boundsInRoot
        val addButton = composeRule.onNodeWithContentDescription("Add card").fetchSemanticsNode().boundsInRoot
        assertThat(lastTile.overlaps(addButton)).isFalse()
    }

    @Test
    fun searching_showsOnlyTheMatchingCards() {
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("berr")

        composeRule.onNodeWithText("Berry").assertIsDisplayed()
        composeRule.onNodeWithText("Apple").assertDoesNotExist()
    }

    @Test
    fun aSearchThatMatchesNothing_saysSo() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput(" xyz ")

        composeRule.onNodeWithText("No card matches “xyz”").assertIsDisplayed()
    }

    @Test
    fun aRestoredSearch_doesNotSayNothingMatches_beforeTheCardsHaveLoaded() {
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Berry"))))
        repository.loadGate = CompletableDeferred()
        // The query comes back with the saved state at once; the cards come from Room later.
        val viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(mapOf("query" to "berry")))
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = ListDirections({ _, _ -> }, {}, {}), viewModel = viewModel)
                }
            }
        }

        composeRule.onNodeWithText("No card matches “berry”").assertDoesNotExist()
        repository.loadGate!!.complete(Unit)
        composeRule.onNodeWithText("Berry").assertIsDisplayed()
    }

    @Test
    fun theSortMenu_marksTheStoredSort() {
        setScreen(aLoyaltyCard(), preferences = FakeUserPreferencesRepository(CardSort.RECENTLY_USED))

        composeRule.onNodeWithContentDescription("Sort").performClick()

        composeRule.onNode(hasText("Recently used") and isSelected()).assertIsDisplayed()
        composeRule.onNode(hasText("Name") and isNotSelected()).assertIsDisplayed()
        composeRule.onNode(hasText("Most used") and isNotSelected()).assertIsDisplayed()
    }

    @Test
    fun choosingASort_storesIt_andReordersTheTiles() {
        val preferences = FakeUserPreferencesRepository()
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), useCount = 1),
            aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"), useCount = 9),
            preferences = preferences,
        )
        assertThat(left("Apple")).isLessThan(left("Berry"))

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("Most used").performClick()

        assertThat(preferences.cardSort.value).isEqualTo(CardSort.MOST_USED)
        composeRule.onNodeWithText("Most used").assertDoesNotExist() // the menu closed
        assertThat(left("Berry")).isLessThan(left("Apple"))
    }

    private fun setScreen(
        vararg cards: LoyaltyCard,
        directions: ListDirections = ListDirections(toCard = { _, _ -> }, toAddCard = {}, toBack = {}),
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        viewModel: ListViewModel = ListViewModel(FakeLoyaltyCardRepository(cards.toList()), preferences, SavedStateHandle()),
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                    }
                }
            }
        }
    }

    /** Two tiles share a row of the grid, so the first one is the one on the left. */
    private fun left(text: String) = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left
}
