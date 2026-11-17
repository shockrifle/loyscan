package com.bdaniel.loyscan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the About screen's directions lead (#150), on a navigation graph with empty screens. */
@RunWith(AndroidJUnit4::class)
class AboutDirectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(navController, startDestination = CardList) {
                composable<CardList> {}
                composable<About> {}
            }
        }
    }

    @Test
    fun aboutOnTheList_opensAboutOverIt_andBackReturnsToIt() {
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toAbout() }

        composeRule.runOnIdle {
            assertThat(currentIs<About>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
        composeRule.runOnIdle { navController.aboutDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(currentIs<CardList>()).isTrue() }
    }

    @Test
    fun aSecondTapOnAbout_opensOneAboutScreen() { // as #69
        val list = composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { list.toAbout() }

        composeRule.runOnIdle { list.toAbout() }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue() }
    }

    private inline fun <reified T : Any> currentIs() = navController.currentBackStackEntry?.destination?.hasRoute(T::class) == true
}
