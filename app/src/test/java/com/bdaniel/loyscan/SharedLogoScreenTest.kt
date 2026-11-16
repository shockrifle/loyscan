package com.bdaniel.loyscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.ShownTexts
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** A picture shared to LoyScan as a store logo (#151): copied in, then handed to the store question. */
@RunWith(AndroidJUnit4::class)
class SharedLogoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val photos = FakeCardPhotos()
    private val staged = mutableListOf<String>()
    private var backs = 0

    @Test
    fun whileThePictureIsCopied_saysSo_andHandsOnNothing() {
        photos.importGate = CompletableDeferred()

        setScreen()

        composeRule.onNodeWithText("Store logo").assertIsDisplayed()
        composeRule.onNodeWithText("Copying the picture…").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(staged).isEmpty() }
    }

    @Test
    fun theCopy_isHandedOnOnce() {
        setScreen()

        composeRule.runOnIdle { assertThat(staged).containsExactly("staged-1") }
    }

    @Test
    fun aPictureThatCantBeCopied_saysSo() {
        photos.importFailure = IOException("no such picture")

        setScreen()

        composeRule.onNodeWithText("The picture couldn't be used.").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(staged).isEmpty() }
    }

    @Test
    fun back_goesBack() {
        photos.importGate = CompletableDeferred()
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextIsTranslated() {
        photos.importGate = CompletableDeferred()
        setScreen()
        val shown = ShownTexts(composeRule)
        shown.collect()
        composeRule.onNodeWithText("A kép másolása…").assertIsDisplayed()

        shown.assertSeen("Bolt logója", "Vissza", "A kép másolása…")
        shown.assertEveryTextIsTranslated()
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_aPictureThatCantBeCopied_saysSo() {
        photos.importFailure = IOException("no such picture")

        setScreen()

        composeRule.onNodeWithText("A képet nem sikerült használni.").assertIsDisplayed()
    }

    private fun setScreen() {
        val viewModel = SharedLogoViewModel(SavedStateHandle(route = SharedLogo("content://media/picker/0/7")), photos)
        val directions = SharedLogoDirections(onStaged = { staged += it }, toBack = { backs++ })
        composeRule.setContent { SharedLogoScreen(directions = directions, viewModel = viewModel) }
    }
}
