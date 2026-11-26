package com.bdaniel.loyscan.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** [assertTextFitsAndDoesNotOverlap] on small layouts, before it is trusted on the screens (#108). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textsThatFit_pass() {
        composeRule.setContent { Column { Text("One"); Text("Two", textAlign = TextAlign.Center) } }

        composeRule.assertTextFitsAndDoesNotOverlap()
    }

    @Test
    fun twoTextsOnTopOfEachOther_fail() {
        composeRule.setContent { Box { Text("One"); Text("Two") } }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertTextFitsAndDoesNotOverlap() }
        assertThat(error).hasMessageThat().contains("\"One\" overlaps \"Two\"")
    }

    @Test
    fun aTextCutOffAtItsLine_fails_unlessItIsMeantToEllipsize() {
        val long = "A text far too long for its box"
        composeRule.setContent { Box(Modifier.width(40.dp)) { Text(long, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertTextFitsAndDoesNotOverlap() }
        assertThat(error).hasMessageThat().contains("is clipped")
        composeRule.assertTextFitsAndDoesNotOverlap(fitsByDesign = setOf(long))
    }

    @Test
    fun aTextCutOffWithoutAnEllipsis_fails() {
        composeRule.setContent { Box(Modifier.width(40.dp)) { Text("A text far too long for its box", softWrap = false) } }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertTextFitsAndDoesNotOverlap() }
        assertThat(error).hasMessageThat().contains("is clipped")
    }

    @Test
    fun under_looksOnlyAtTheTextsUnderThatNode() {
        composeRule.setContent { Box { Text("Behind"); Box(Modifier.testTag("overlay")) { Text("Front") } } }

        composeRule.assertTextFitsAndDoesNotOverlap(under = hasTestTag("overlay"))
        assertThrows(AssertionError::class.java) { composeRule.assertTextFitsAndDoesNotOverlap() }
    }

    @Test
    fun under_aNodeInADialog_isMeasuredAgainstTheDialogsWindow() { // #209: a dialog is a second root
        composeRule.setContent {
            Text("Behind")
            Dialog(onDismissRequest = {}) { Box(Modifier.testTag("dialog")) { Text("Front") } }
        }

        composeRule.assertTextFitsAndDoesNotOverlap(under = hasTestTag("dialog"))
    }

    @Test
    fun aTextRunningOffADialog_fails() { // #209
        composeRule.setContent {
            Dialog(onDismissRequest = {}) {
                Box(Modifier.testTag("dialog").requiredWidth(2000.dp)) { Text("Gone", Modifier.align(Alignment.CenterEnd)) }
            }
        }

        val error = assertThrows(AssertionError::class.java) {
            composeRule.assertTextFitsAndDoesNotOverlap(under = hasTestTag("dialog"))
        }
        assertThat(error).hasMessageThat().contains("\"Gone\" runs off the screen")
    }

    @Test
    fun aTextRunningOffTheScreen_fails() {
        composeRule.setContent { Box(Modifier.requiredWidth(2000.dp)) { Text("Gone", Modifier.align(Alignment.CenterEnd)) } }

        val error = assertThrows(AssertionError::class.java) { composeRule.assertTextFitsAndDoesNotOverlap() }
        assertThat(error).hasMessageThat().contains("\"Gone\" runs off the screen")
    }
}
