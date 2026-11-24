package com.bdaniel.loyscan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a widget tap's `openCard` leads (#11): the tapped card over the list, whatever was open before, so Back returns
 * to the list (#213); a tap for the card already on top leaves it as it is.
 */
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
    fun openCard_whileACardFormIsOpen_opensTheCardOverTheList() { // #213: Back returns to the list, not to the form
        setUpAt(StorePicker(), CardForm(storeId = "s"))

        composeRule.runOnIdle { navController.openCard("card-7", "#112233") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>().cardId).isEqualTo("card-7")
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
            navController.popBackStack()
            assertThat(navController.currentBackStackEntry!!.destination.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun openCard_overAnotherCard_opensTheTappedCardOverTheList() { // #213
        setUpAt(Detail("card-1", "#aa0000"))
        lateinit var firstCardsEntryId: String
        composeRule.runOnIdle { firstCardsEntryId = navController.currentBackStackEntry!!.id }

        composeRule.runOnIdle { navController.openCard("card-2", "#0000bb") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>().cardId).isEqualTo("card-2")
            // A new entry: reusing card 1's would keep its view model, which still shows card 1.
            assertThat(navController.currentBackStackEntry!!.id).isNotEqualTo(firstCardsEntryId)
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun openCard_forTheCardOnTop_leavesItsEntryAsItIs() { // #213: no second entry, no reload
        setUpAt(Detail("card-1", "#aa0000"))
        lateinit var entryId: String
        var stackSize = 0
        composeRule.runOnIdle {
            entryId = navController.currentBackStackEntry!!.id
            stackSize = navController.currentBackStack.value.size
        }

        composeRule.runOnIdle { navController.openCard("card-1", "#aa0000") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.id).isEqualTo(entryId)
            assertThat(navController.currentBackStack.value.size).isEqualTo(stackSize)
        }
    }
}
