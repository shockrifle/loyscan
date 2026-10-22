package com.bdaniel.loyscan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where the add-card flow's directions lead, on a navigation graph with the app's routes and empty screens. */
@RunWith(AndroidJUnit4::class)
class AddCardDirectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            NavHost(navController, startDestination = List) {
                composable<List> {}
                composable<StorePicker> {}
                composable<Scanner> {}
                dialog<ScanResult> {}
            }
        }
        composeRule.runOnIdle { navController.listDirections().toAddCard() }
    }

    @Test
    fun addCard_opensTheStorePicker() {
        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun pickingAStore_opensTheScannerForIt() {
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Scanner>()).isEqualTo(Scanner("some-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aScan_showsItsResultOverTheList() {
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections().onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle {
            val entry = navController.currentBackStackEntry!!
            assertThat(entry.toRoute<ScanResult>()).isEqualTo(ScanResult("5901234123457", BarcodeFormat.EAN_13))
        }
    }

    @Test
    fun aGs1Scan_keepsItsGs1MarkInTheResult() { // #41
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections().onScanned(ScannedBarcode("9799123456789012", BarcodeFormat.GS1_128, gs1 = true))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<ScanResult>())
                .isEqualTo(ScanResult("9799123456789012", BarcodeFormat.GS1_128, gs1 = true))
        }
    }

    @Test
    fun backFromThePicker_returnsToTheList() {
        composeRule.runOnIdle { navController.storePickerDirections().toBack() }

        composeRule.runOnIdle { assertThat(currentIs<List>()).isTrue() }
    }

    private inline fun <reified T : Any> currentIs() = navController.currentBackStackEntry?.destination?.hasRoute(T::class) == true
}
