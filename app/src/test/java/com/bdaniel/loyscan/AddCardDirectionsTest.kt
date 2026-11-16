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
            NavHost(navController, startDestination = CardList) {
                composable<CardList> {}
                composable<StorePicker> {}
                composable<CustomStore> {}
                composable<Scanner> {}
                composable<CardForm> {}
                composable<Detail> {}
                composable<SharedPicture> {}
                composable<StoreLogo> {}
                composable<SharedLogo> {}
            }
        }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toAddCard() }
    }

    @Test
    fun addCard_opensTheStorePicker() {
        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun pickingAStore_opensTheScannerForIt() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Scanner>()).isEqualTo(Scanner("some-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun otherCard_opensTheCustomStoreForm() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }

        composeRule.runOnIdle {
            assertThat(currentIs<CustomStore>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun creatingACustomStore_replacesItsFormWithTheScannerForIt() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }
        composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!).onCreated("new-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Scanner>()).isEqualTo(Scanner("new-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun editingACustomStore_opensItsFormOverThePicker() { // #53
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toEditStore("custom-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CustomStore>()).isEqualTo(CustomStore("custom-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aStoresLogoButton_opensItsLogoScreenOverThePicker_andBackReturnsToIt() { // #151
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStoreLogo("hu-tesco") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StoreLogo>()).isEqualTo(StoreLogo("hu-tesco"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
        composeRule.runOnIdle { navController.storeLogoDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun aCustomStoresLogoButton_opensItsLogoScreenOverItsForm_andBackReturnsToIt() { // #151
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toEditStore("custom-store") }
        composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!).toStoreLogo("custom-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StoreLogo>()).isEqualTo(StoreLogo("custom-store"))
            assertThat(navController.previousBackStackEntry!!.toRoute<CustomStore>()).isEqualTo(CustomStore("custom-store"))
        }
        composeRule.runOnIdle { navController.storeLogoDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(navController.currentBackStackEntry!!.toRoute<CustomStore>()).isEqualTo(CustomStore("custom-store")) }
    }

    @Test
    fun aSecondTapOnTheFormsLogoButton_opensOneLogoScreen() { // #151, as #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toEditStore("custom-store") }
        val form = composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { form.toStoreLogo("custom-store") }

        composeRule.runOnIdle { form.toStoreLogo("custom-store") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CustomStore::class)).isTrue() }
    }

    @Test
    fun aStoreTappedInLogoMode_opensItsLogoScreenWithThePicture_inPlaceOfThePicker() { // #151
        composeRule.runOnIdle { navController.navigateUp(); navController.navigate(StorePicker(logoPicture = "staged-1")) }
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("hu-tesco") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StoreLogo>()).isEqualTo(StoreLogo("hu-tesco", picture = "staged-1"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSecondTapOnAStoreInLogoMode_opensOneLogoScreen() { // #151, as #69
        composeRule.runOnIdle { navController.navigateUp(); navController.navigate(StorePicker(logoPicture = "staged-1")) }
        val picker = composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { picker.toStore("hu-tesco") }

        composeRule.runOnIdle { picker.toStore("hu-tesco") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue() }
    }

    @Test
    fun aSharedLogo_opensOverTheList_andItsCopyGoesToThePickerInLogoMode_inItsPlace() { // #151
        composeRule.runOnIdle { navController.openSharedLogo("content://media/picker/0/7") }
        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<SharedLogo>()).isEqualTo(SharedLogo("content://media/picker/0/7"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }

        composeRule.runOnIdle { navController.sharedLogoDirections(navController.currentBackStackEntry!!).onStaged("staged-1") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StorePicker>()).isEqualTo(StorePicker(logoPicture = "staged-1"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSharedLogoCopiedAfterBack_leavesTheUserOnTheList() { // #151, as #68
        composeRule.runOnIdle { navController.openSharedLogo("content://media/picker/0/7") }
        val shared = composeRule.runOnIdle { navController.sharedLogoDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { shared.toBack() }

        composeRule.runOnIdle { shared.onStaged("staged-1") }

        composeRule.runOnIdle { assertThat(currentIs<CardList>()).isTrue() }
    }

    @Test
    fun backFromTheCustomStoreForm_returnsToThePicker() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }
        composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun aScan_replacesTheScannerWithTheFormForTheStore_withTheScannedContentAndFormat() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections("some-store", navController.currentBackStackEntry!!).onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("some-store", scannedContent = "5901234123457", scannedFormat = BarcodeFormat.EAN_13))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aGs1Scan_reachesTheFormWithItsGs1FormatAndItsSeparatorsIntact() { // #41
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle {
            navController.scannerDirections("some-store", navController.currentBackStackEntry!!)
                .onScanned(ScannedBarcode("10ABC\u001D9799123456", BarcodeFormat.GS1_128, gs1 = true))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("some-store", scannedContent = "10ABC\u001D9799123456", scannedFormat = BarcodeFormat.GS1_128))
        }
    }

    @Test
    fun enterManually_replacesTheScannerWithAnEmptyFormForTheStore() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!).toManualEntry() }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun savingTheForm_opensTheNewCardOverTheList_withoutCountingAUse_offeringToAddAnother() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!).toManualEntry() }
        composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!).toCard("new-card", "#112233") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>())
                .isEqualTo(Detail("new-card", "#112233", countUse = false, added = true))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun addAnotherOnTheNewCard_opensThePickerOverIt() { // #6
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!).toManualEntry() }
        composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!).toCard("new-card", "#112233") }

        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toAddCard() }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry!!.toRoute<Detail>().cardId).isEqualTo("new-card")
        }
    }

    @Test
    fun aSecondTapOnAddAnother_opensOnePicker() { // as #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!).toManualEntry() }
        composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!).toCard("new-card", "#112233") }
        val card = composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { card.toAddCard() }

        composeRule.runOnIdle { card.toAddCard() }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(Detail::class)).isTrue() }
    }

    @Test
    fun editOnACard_opensItsFormOverIt() { // #8
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store", cardId = "a-card"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(Detail::class)).isTrue()
        }
    }

    @Test
    fun savingAnEdit_replacesTheCardAndItsForm_withTheCardInItsCurrentColour() { // #8: the store may have changed
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }
        composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!).toCard("a-card", "#445566") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#445566", countUse = false))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun editOnTheList_opensTheCardsFormOverIt_andSavingOpensTheCardOverTheList() { // #7: one card selected
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toEditCard("a-card", "some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store", cardId = "a-card"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
        composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!).toCard("a-card", "#445566") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#445566", countUse = false))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSecondTapOnEditOnTheList_opensOneForm() { // as #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        val list = composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!) }

        composeRule.runOnIdle { list.toEditCard("a-card", "some-store") }
        composeRule.runOnIdle { list.toEditCard("a-card", "some-store") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue() }
    }

    @Test
    fun aSaveThatEndsAfterBackFromTheForm_leavesTheUserOnThePicker() { // #64
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!).toManualEntry() }
        val directions = composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { directions.toBack() }

        composeRule.runOnIdle { directions.toCard("new-card", "#112233") }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun anEditThatEndsAfterBackFromTheForm_leavesTheUserOnTheCard() { // #64
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }
        val directions = composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { directions.toBack() }

        composeRule.runOnIdle { directions.toCard("a-card", "#445566") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#112233"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSaveThatEndsAfterBack_leavesAFormOpenedSinceAsItIs() { // review of #64: Edit again while the first form leaves
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }
        val first = composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { first.toBack() }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }

        composeRule.runOnIdle { first.toCard("a-card", "#445566") }

        composeRule.runOnIdle {
            assertThat(currentIs<CardForm>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(Detail::class)).isTrue()
        }
    }

    @Test
    fun aNewStoreSavedAfterBackFromItsForm_leavesTheUserOnThePicker() { // #65
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }
        val form = composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { form.toBack() }

        composeRule.runOnIdle { form.onCreated("new-store") }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aStoreSavedOrDeletedAfterBackFromItsForm_leavesTheUserOnThePicker() { // #65: both end in toBack
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toEditStore("custom-store") }
        val form = composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { form.toBack() }

        composeRule.runOnIdle { form.toBack() }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aCardDeletedAfterBack_leavesACardOpenedSinceAsItIs() { // #65
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        val deleted = composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { deleted.toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("another-card", "#445566") }

        composeRule.runOnIdle { deleted.toBack() }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("another-card", "#445566"))
        }
    }

    @Test
    fun aSecondBackFromThePicker_staysOnTheList() { // #66: a double tap on the Back arrow
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.navigate(StorePicker()) }
        val picker = composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { picker.toBack() }

        composeRule.runOnIdle { picker.toBack() }

        composeRule.runOnIdle { assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#112233")) }
    }

    @Test
    fun aSecondBackFromTheScanner_staysOnThePicker() { // #66
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        val scanner = composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { scanner.toBack() }

        composeRule.runOnIdle { scanner.toBack() }

        composeRule.runOnIdle { assertThat(currentIs<StorePicker>()).isTrue() }
    }

    @Test
    fun aSecondBackFromTheCardForm_staysOnTheCard() { // #66: the form is over the card when editing
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!).toEdit("a-card", "some-store") }
        val form = composeRule.runOnIdle { navController.cardFormDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { form.toBack() }

        composeRule.runOnIdle { form.toBack() }

        composeRule.runOnIdle { assertThat(navController.currentBackStackEntry!!.toRoute<Detail>()).isEqualTo(Detail("a-card", "#112233")) }
    }

    @Test
    fun aScanDecodedAfterBackFromTheScanner_leavesTheUserOnThePicker() { // #68: frames arrive while the scanner leaves
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        val scanner = composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { scanner.toBack() }

        composeRule.runOnIdle { scanner.onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)) }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun enterManuallyAfterBackFromTheScanner_leavesTheUserOnThePicker() { // #68
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }
        val scanner = composeRule.runOnIdle { navController.scannerDirections("some-store", navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { scanner.toBack() }

        composeRule.runOnIdle { scanner.toManualEntry() }

        composeRule.runOnIdle {
            assertThat(currentIs<StorePicker>()).isTrue()
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSecondTapOnAStore_opensOneScanner() { // #69: the picker takes taps until the scanner covers it
        val picker = composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { picker.toStore("some-store") }

        composeRule.runOnIdle { picker.toStore("some-store") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue() }
    }

    @Test
    fun aSecondTapOnOtherCard_opensOneForm() { // #69
        val picker = composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { picker.toCustomStore() }

        composeRule.runOnIdle { picker.toCustomStore() }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue() }
    }

    @Test
    fun aSecondTapOnACustomStoresEditButton_opensOneForm() { // #69
        val picker = composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { picker.toEditStore("custom-store") }

        composeRule.runOnIdle { picker.toEditStore("custom-store") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue() }
    }

    @Test
    fun aSecondTapOnACard_opensOneCardScreen() { // #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        val list = composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!) }
        val first = composeRule.runOnIdle { list.toCard("a-card", "#112233") }

        val second = composeRule.runOnIdle { list.toCard("a-card", "#112233") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue() }
        assertThat(first).isTrue() // the list records the opened tile only when the card opened
        assertThat(second).isFalse()
    }

    @Test
    fun aSecondTapOnAdd_opensOnePicker() { // #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        val list = composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { list.toAddCard() }

        composeRule.runOnIdle { list.toAddCard() }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue() }
    }

    @Test
    fun aSecondTapOnEdit_opensOneForm() { // #69
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }
        composeRule.runOnIdle { navController.listDirections(navController.currentBackStackEntry!!).toCard("a-card", "#112233") }
        val card = composeRule.runOnIdle { navController.detailDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { card.toEdit("a-card", "some-store") }

        composeRule.runOnIdle { card.toEdit("a-card", "some-store") }

        composeRule.runOnIdle { assertThat(navController.previousBackStackEntry?.destination?.hasRoute(Detail::class)).isTrue() }
    }

    @Test
    fun backFromThePicker_returnsToTheList() {
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(currentIs<CardList>()).isTrue() }
    }

    @Test
    fun aSharedPicture_opensOverTheList_whateverWasOpen() { // #77
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }

        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<SharedPicture>()).isEqualTo(SharedPicture(SHARED_URI))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSharedPicturesScan_replacesItWithThePickerHoldingTheScan() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }

        composeRule.runOnIdle {
            navController.sharedPictureDirections(navController.currentBackStackEntry!!)
                .onScanned(ScannedBarcode("10ABC\u001D9799123456", BarcodeFormat.GS1_128, gs1 = true))
        }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StorePicker>())
                .isEqualTo(StorePicker(scannedContent = "10ABC\u001D9799123456", scannedFormat = BarcodeFormat.GS1_128))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun aSharedPictureWithoutAScan_offersThePickerForANumberTypedByHand() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }

        composeRule.runOnIdle { navController.sharedPictureDirections(navController.currentBackStackEntry!!).toManualEntry() }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<StorePicker>()).isEqualTo(StorePicker(manualEntry = true))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(CardList::class)).isTrue()
        }
    }

    @Test
    fun backFromASharedPicture_returnsToTheList() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }

        composeRule.runOnIdle { navController.sharedPictureDirections(navController.currentBackStackEntry!!).toBack() }

        composeRule.runOnIdle { assertThat(currentIs<CardList>()).isTrue() }
    }

    @Test
    fun aSharedPicturesScanReadAfterBack_leavesTheUserOnTheList() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        val shared = composeRule.runOnIdle { navController.sharedPictureDirections(navController.currentBackStackEntry!!) }
        composeRule.runOnIdle { shared.toBack() }

        composeRule.runOnIdle { shared.onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)) }

        composeRule.runOnIdle { assertThat(currentIs<CardList>()).isTrue() }
    }

    @Test
    fun pickingAStore_forASharedScan_opensTheFormWithTheScan_overThePicker() { // #77: no scanner
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        composeRule.runOnIdle {
            navController.sharedPictureDirections(navController.currentBackStackEntry!!)
                .onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("some-store", scannedContent = "5901234123457", scannedFormat = BarcodeFormat.EAN_13))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun pickingAStore_forANumberTypedByHand_opensAnEmptyForm_overThePicker() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        composeRule.runOnIdle { navController.sharedPictureDirections(navController.currentBackStackEntry!!).toManualEntry() }

        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toStore("some-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("some-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aNewStoreForASharedScan_replacesItsFormWithTheCardFormHoldingTheScan() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        composeRule.runOnIdle {
            navController.sharedPictureDirections(navController.currentBackStackEntry!!)
                .onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }
        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CustomStore>())
                .isEqualTo(CustomStore(scannedContent = "5901234123457", scannedFormat = BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!).onCreated("new-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>())
                .isEqualTo(CardForm("new-store", scannedContent = "5901234123457", scannedFormat = BarcodeFormat.EAN_13))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun aNewStoreForANumberTypedByHand_replacesItsFormWithAnEmptyCardForm() { // #77
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        composeRule.runOnIdle { navController.sharedPictureDirections(navController.currentBackStackEntry!!).toManualEntry() }
        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toCustomStore() }

        composeRule.runOnIdle { navController.customStoreDirections(navController.currentBackStackEntry!!).onCreated("new-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CardForm>()).isEqualTo(CardForm("new-store"))
            assertThat(navController.previousBackStackEntry?.destination?.hasRoute(StorePicker::class)).isTrue()
        }
    }

    @Test
    fun editingACustomStore_fromThePickerOfASharedScan_doesNotCarryTheScan() { // #77: the edit form only edits
        composeRule.runOnIdle { navController.openSharedPicture(SHARED_URI) }
        composeRule.runOnIdle {
            navController.sharedPictureDirections(navController.currentBackStackEntry!!)
                .onScanned(ScannedBarcode("5901234123457", BarcodeFormat.EAN_13))
        }

        composeRule.runOnIdle { navController.storePickerDirections(navController.currentBackStackEntry!!).toEditStore("custom-store") }

        composeRule.runOnIdle {
            assertThat(navController.currentBackStackEntry!!.toRoute<CustomStore>()).isEqualTo(CustomStore("custom-store"))
        }
    }

    private inline fun <reified T : Any> currentIs() = navController.currentBackStackEntry?.destination?.hasRoute(T::class) == true
}

private const val SHARED_URI = "content://media/picker/0/42"
