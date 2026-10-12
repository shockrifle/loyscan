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
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.cardnumber.CardNumberRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class DetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val card = aLoyaltyCard(label = "Gold card", store = aStore(name = "Test Store"), number = "0042")

    private fun setScreen(color: String = "#336699", card: LoyaltyCard = this.card, onBack: () -> Unit = {}) {
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
    fun showsTheCardLabelAndNumber() {
        setScreen()

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
        composeRule.onNodeWithText("0042").assertIsDisplayed()
    }

    @Test
    fun showsTheNumberFormattedByTheStoresRule() { // #1: the display number is derived, not stored
        val rule = CardNumberRule(
            id = "example",
            format = BarcodeFormat.CODE_128,
            minLength = 9,
            maxLength = 9,
            display = CardNumberRule.Display(pattern = "### ### ###"),
        )
        setScreen(card = aLoyaltyCard(store = aStore(cardNumberRule = rule), number = "123456789"))

        composeRule.onNodeWithText("123 456 789").assertIsDisplayed()
    }

    @Test
    fun backArrow_navigatesBack() {
        var wentBack = false
        setScreen(onBack = { wentBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(wentBack).isTrue()
    }

    @Test
    fun emptyStoreColor_doesNotCrash() { // #14
        setScreen(color = "")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }

    @Test
    fun malformedStoreColor_doesNotCrash() { // #14
        setScreen(color = "not a colour")

        composeRule.onNodeWithText("Gold card").assertIsDisplayed()
    }
}
