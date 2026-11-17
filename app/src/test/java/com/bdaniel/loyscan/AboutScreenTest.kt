package com.bdaniel.loyscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.WithFontScale
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The About screen (#150): the app's name and version, and that LoyScan isn't affiliated with the retailers. */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backs = 0

    @Test
    fun showsTheName_theVersion_andTheNonAffiliationNote() {
        setScreen()

        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithText("LoyScan").assertIsDisplayed()
        composeRule.onNodeWithText("Version 9.8.7").assertIsDisplayed()
        composeRule.onNodeWithText(
            "LoyScan is not affiliated with or endorsed by the retailers whose cards it stores. " +
                "Their names and marks belong to their owners.",
        ).assertIsDisplayed()
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // Robolectric's default screen is 320dp wide
        setScreen(fontScale = 2f)

        composeRule.assertTextFitsAndDoesNotOverlap()
    }

    @Test
    @Config(qualifiers = "w320dp-h240dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onAShortScreen_theWholeNoteCanBeScrolledIntoView() {
        setScreen(fontScale = 2f)
        val note = composeRule.onNodeWithText("Their names and marks belong to their owners.", substring = true)

        note.performScrollTo()

        val bottom = note.fetchSemanticsNode().boundsInRoot.bottom
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertThat(bottom).isAtMost(screen)
    }

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextIsTranslated() {
        setScreen()
        val shown = ShownTexts(composeRule)
        shown.collect()

        shown.assertSeen(
            "Névjegy", "Vissza", "Verzió: 9.8.7",
            "A LoyScan nem áll kapcsolatban az általa tárolt kártyák kereskedőivel, és azok nem támogatják az alkalmazást. " +
                "A nevük és a védjegyeik a tulajdonosaikat illetik.",
        )
        shown.assertEveryTextIsTranslated("LoyScan", "9.8.7")
    }

    private fun setScreen(fontScale: Float = 1f) {
        composeRule.setContent {
            WithFontScale(fontScale) {
                AboutScreen(directions = AboutDirections(toBack = { backs++ }), version = "9.8.7")
            }
        }
    }
}
