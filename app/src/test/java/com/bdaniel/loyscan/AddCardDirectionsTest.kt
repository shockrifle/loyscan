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
                composable<CustomStore> {}
                composable<Scanner> {}
                composable<CardForm> {}
                composable<Detail> {}
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
    fun otherCard_opensTheCustomStoreForm() {
        composeRule.runOnIdle { navController.storePickerDirections().toCustomStore() }

        composeRule.runOnIdle {
            assertThat(currentIs<CustomStore>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun creatingACustomStore_replacesItsFormWithTheScannerForIt() {
        composeRule.runOnIdle { navController.storePickerDirections().toCustomStore() }
        composeRule.runOnIdle { navController.customStoreDirections().toScanner("new-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Scanner>()).isEqualTo(Scanner("new-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun editingACustomStore_opensItsFormOverThePicker() { // #53
        composeRule.runOnIdle { navController.storePickerDirections().toEditStore("custom-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CustomStore>()).isEqualTo(CustomStore("custom-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun backFromTheCustomStoreForm_returnsToThePicker() {
        composeRule.runOnIdle { navController.storePickerDirections().toCustomStore() }
        composeRule.runOnIdle { navController.customStoreDirections().toBack() }

        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun aScan_replacesTheScannerWithTheFormForTheStore_withTheScannedContentAndFormat() {
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections("some-store").onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("some-store", scannedContent = "5901234123457", scannedFormat = BarcodeFormat.EAN_13))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aGs1Scan_reachesTheFormWithItsGs1FormatAndItsSeparatorsIntact() { // #41
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections("some-store")
                .onScanned(ScannedBarcode("10ABC\u001D9799123456", BarcodeFormat.GS1_128, gs1 = true))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("some-store", scannedContent = "10ABC\u001D9799123456", scannedFormat = BarcodeFormat.GS1_128))
        }
    }

    @Test
    fun enterManually_replacesTheScannerWithAnEmptyFormForTheStore() {
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store").toManualEntry() }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun savingTheForm_opensTheNewCardOverTheList_withoutCountingAUse() {
        composeRule.runOnIdle { navController.storePickerDirections().toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store").toManualEntry() }
        composeRule.runOnIdle { navController.cardFormDirections().toCard("new-card", "#112233") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>())
                .isEqualTo(Detail("new-card", "#112233", countUse = false))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(List::class)).isTrue()
        }
    }

    @Test
    fun editOnACard_opensItsFormOverIt() { // #8
        composeRule.runOnIdle { navController.storePickerDirections().toBack() }
        composeRule.runOnIdle { navController.listDirections().toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections().toEdit("a-card", "some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store", cardId = "a-card"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(Detail::class)).isTrue()
        }
    }

    @Test
    fun savingAnEdit_replacesTheCardAndItsForm_withTheCardInItsCurrentColour() { // #8: the store may have changed
        composeRule.runOnIdle { navController.storePickerDirections().toBack() }
        composeRule.runOnIdle { navController.listDirections().toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections().toEdit("a-card", "some-store") }
        composeRule.runOnIdle { navController.cardFormDirections().toCard("a-card", "#445566") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#445566", countUse = false))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(List::class)).isTrue()
        }
    }

    @Test
    fun backFromThePicker_returnsToTheList() {
        composeRule.runOnIdle { navController.storePickerDirections().toBack() }

        composeRule.runOnIdle { assertThat(currentIs<List>()).isTrue() }
    }

    private inline fun <reified T : Any> currentIs() = navController.currentBackStackEntry?.destination?.hasRoute(T::class) == true
}
