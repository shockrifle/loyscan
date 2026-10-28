package com.bdaniel.loyscan.ui

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ScreenEffectsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val brightness get() = composeRule.activity.window.attributes.screenBrightness

    private val keepsScreenOn get() = composeRule.activity.window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0

    private var shown by mutableStateOf(true)

    private fun setContent() = composeRule.setContent { if (shown) FullBrightness() }

    private fun setKeepScreenOn() = composeRule.setContent { if (shown) KeepScreenOn() }

    @Test
    fun fullBrightness_whileInComposition() {
        setContent()

        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_FULL)
    }

    @Test
    fun fullBrightness_leavingComposition_restoresTheWindowsBrightness() {
        setContent()

        shown = false
        composeRule.waitForIdle()

        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_NONE)
    }

    @Test
    fun fullBrightness_restoresTheWindowsOwnValue_notTheDefault() {
        composeRule.runOnUiThread {
            composeRule.activity.window.attributes = composeRule.activity.window.attributes.apply { screenBrightness = 0.3f }
        }
        setContent()

        shown = false
        composeRule.waitForIdle()

        assertThat(brightness).isEqualTo(0.3f)
    }

    @Test
    fun fullBrightness_isRestoredWhileTheScreenIsPaused_andComesBackOnResume() {
        setContent()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_NONE)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_FULL)
    }

    @Test
    fun keepScreenOn_whileInComposition() {
        setKeepScreenOn()

        assertThat(keepsScreenOn).isTrue()
    }

    @Test
    fun keepScreenOn_leavingComposition_clearsTheFlag() {
        setKeepScreenOn()

        shown = false
        composeRule.waitForIdle()

        assertThat(keepsScreenOn).isFalse()
    }

    @Test
    fun keepScreenOn_leavesAFlagSomeoneElseSet() {
        composeRule.runOnUiThread { composeRule.activity.window.addFlags(FLAG_KEEP_SCREEN_ON) }
        setKeepScreenOn()

        shown = false
        composeRule.waitForIdle()

        assertThat(keepsScreenOn).isTrue()
    }

    @Test
    fun keepScreenOn_isClearedWhileTheScreenIsPaused_andComesBackOnResume() {
        setKeepScreenOn()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertThat(keepsScreenOn).isFalse()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertThat(keepsScreenOn).isTrue()
    }

    private val requestedOrientation get() = composeRule.activity.requestedOrientation

    @Test
    fun screenOrientation_onAPhone_holdsPortrait() { // #63
        composeRule.setContent { ScreenOrientation(rotates = false) }

        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun screenOrientation_onAPhone_aScreenThatRotates_followsTheDevice() { // #63
        var rotates by mutableStateOf(false)
        composeRule.setContent { ScreenOrientation(rotates = rotates) }

        rotates = true
        composeRule.waitForIdle()

        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    @Config(qualifiers = "sw600dp-w600dp-h960dp")
    fun screenOrientation_onALargeScreen_followsTheDevice() { // #63
        composeRule.setContent { ScreenOrientation(rotates = false) }

        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun screenOrientation_beforeTheScreenIsKnown_leavesTheOrientationAlone() { // #63
        // After a recreation, the back stack is restored a frame after the first composition.
        composeRule.runOnUiThread { composeRule.activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE }

        composeRule.setContent { ScreenOrientation(rotates = null) }

        assertThat(requestedOrientation).isEqualTo(SCREEN_ORIENTATION_LANDSCAPE)
    }
}
