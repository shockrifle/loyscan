package com.bdaniel.loyscan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where a widget tap's `openCard` leads (#11): it opens the card without discarding a screen the user was on. */
@RunWith(AndroidJUnit4::class)
class WidgetOpenCardDirectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private fun setUpAt(vararg route: Any) {
        composeRule.setContent {
            navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            NavHost(navController, startDestination = CardList) {
                composable<CardList> {}
                composable<StorePicker> {}
                composable<CardForm> {}
                composable<Detail> {}
            }
        }
        route.forEach { r -> composeRule.runOnIdle { navController.navigate(r) } }
    }

    @Test
    fun openCard_fromTheList_opensTheCardOverTheList() {
        setUpAt()

        composeRule.runOnIdle { navController.openCard("card-7", "#112233") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.destination.hasRoute(Detail::class)).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun openCard_whileACardFormIsOpen_doesNotDiscardTheForm() { // #11, #83: a half-filled form survives a widget tap
        setUpAt(StorePicker(), CardForm(storeId = "s"))

        composeRule.runOnIdle { navController.openCard("card-7", "#112233") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.destination.hasRoute(Detail::class)).isTrue()
            // The form is still under the card, so Back returns to it with its input intact.
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardForm::class)).isTrue()
        }
    }
}
