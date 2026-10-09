package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.aBrand
import com.bdaniel.loyscan.testing.aLoyaltyCard
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
    fun clickingACard_navigatesToItWithItsBrandColor() {
        val card = aLoyaltyCard(id = 5, brand = aBrand(name = "Test Store", color = "#AA0000"))
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(listOf(card)))
        var navigatedTo: Pair<Int, String>? = null
        val directions = ListDirections(toCard = { id, color -> navigatedTo = id to color }, toBack = {})

        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                }
            }
        }
        composeRule.onNodeWithText("Test Store").performClick()

        assertThat(navigatedTo).isEqualTo(5 to "#AA0000")
    }

    @Test
    fun showsATileForEachCard() {
        setScreen(aLoyaltyCard(id = 1, brand = aBrand(name = "First Store")), aLoyaltyCard(id = 2, brand = aBrand(name = "Second Store")))

        composeRule.onNodeWithText("First Store").assertIsDisplayed()
        composeRule.onNodeWithText("Second Store").assertIsDisplayed()
    }

    private fun setScreen(vararg cards: LoyaltyCard) {
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(cards.toList()))
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = ListDirections(toCard = { _, _ -> }, toBack = {}), viewModel = viewModel)
                }
            }
        }
    }
}
