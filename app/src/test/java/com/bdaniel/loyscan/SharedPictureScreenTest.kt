package com.bdaniel.loyscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SharedPictureScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val reader = FakePictureReader()
    private val scanned = mutableListOf<ScannedBarcode>()
    private var manualEntries = 0
    private var backs = 0
    private val directions = SharedPictureDirections(
        onScanned = { scanned += it },
        toManualEntry = { manualEntries++ },
        toBack = { backs++ },
    )

    @Test
    fun whileThePictureIsRead_saysSo_andHandsOnNothing() {
        reader.gate = CompletableDeferred()

        setScreen()

        composeRule.onNodeWithText("Reading the picture…").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(scanned).isEmpty() }
    }

    @Test
    fun aPictureWithABarcode_handsItOnOnce() { // #77
        val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
        reader.result = PictureScan.Found(barcode)

        setScreen()

        composeRule.runOnIdle { assertThat(scanned).containsExactly(barcode) }
    }

    @Test
    fun aPictureWithoutABarcode_saysSo_andOffersToEnterTheNumberByHand() { // #77
        reader.result = PictureScan.NoBarcode
        setScreen()

        composeRule.onNodeWithText("No barcode was found in that picture.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter the number by hand").performClick()

        composeRule.runOnIdle {
            assertThat(manualEntries).isEqualTo(1)
            assertThat(scanned).isEmpty()
        }
    }

    @Test
    fun aPictureThatCannotBeOpened_saysSo_andOffersToEnterTheNumberByHand() { // #77
        reader.result = PictureScan.Unreadable
        setScreen()

        composeRule.onNodeWithText("That picture couldn't be opened.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter the number by hand").performClick()

        composeRule.runOnIdle { assertThat(manualEntries).isEqualTo(1) }
    }

    @Test
    fun back_goesBack() {
        reader.gate = CompletableDeferred()
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle { assertThat(backs).isEqualTo(1) }
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_whileThePictureIsRead_saysSo() { // #72
        reader.gate = CompletableDeferred() // never completed: the picture stays "being read"
        setScreen()
        val shown = ShownTexts(composeRule)

        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Kártya hozzáadása", "Vissza", "A kép beolvasása…")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aPictureWithoutABarcode_saysSo() { // #72
        reader.result = PictureScan.NoBarcode
        setScreen()
        val shown = ShownTexts(composeRule)

        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen(
            "Ezen a képen nem található vonalkód. A kártyán lévő számot kézzel is beírhatod.",
            "A szám beírása kézzel",
        )
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aPictureThatCannotBeOpened_saysSo() { // #72
        reader.result = PictureScan.Unreadable
        setScreen()
        val shown = ShownTexts(composeRule)

        shown.collect()

        shown.assertEveryTextIsTranslated()
        shown.assertSeen("Nem sikerült megnyitni a képet. A kártyán lévő számot kézzel is beírhatod.")
    }

    private fun setScreen() {
        val viewModel = SharedPictureViewModel(SavedStateHandle(route = SharedPicture("content://media/picker/0/42")), reader)
        composeRule.setContent { SharedPictureScreen(directions = directions, viewModel = viewModel) }
    }
}
