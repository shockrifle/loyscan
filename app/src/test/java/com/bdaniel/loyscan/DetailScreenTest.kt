package com.bdaniel.loyscan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.aBrand
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val card = aLoyaltyCard(id = 3, name = "Gold card", brand = aBrand(name = "Test Store"), code = "0042")

    private fun setScreen(color: String = "#336699", onBack: () -> Unit = {}) {
        val viewModel = DetailViewModel(
            SavedStateHandle(route = Detail(cardId = card.id, color = color)),
            FakeLoyaltyCardRepository(listOf(card)),
        )
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    DetailScreen(
                        color = color,
                        animatedVisibilityScope = this,
                        directions = DetailDirections(toBack = onBack),
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    @Test
    fun showsTheCardNameAndCode() {
        setScreen()

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertIsDisplayed()
    }

    @Test
    fun backArrow_navigatesBack() {
        var wentBack = false
        setScreen(onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(wentBack).isTrue()
    }

    @Test
    fun emptyBrandColor_doesNotCrash() { // #14
        setScreen(color = "")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }

    @Test
    fun malformedBrandColor_doesNotCrash() { // #14
        setScreen(color = "not a colour")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }
}
