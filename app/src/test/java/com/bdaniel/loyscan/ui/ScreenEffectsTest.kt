package com.bdaniel.loyscan.ui

import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
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

@RunWith(AndroidJUnit4::class)
class ScreenEffectsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val brightness get() = composeRule.activity.window.attributes.screenBrightness

    private var shown by mutableStateOf(true)

    private fun setContent() = composeRule.setContent { if (shown) FullBrightness() }

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
    fun fullBrightness_isRestoredWhileTheScreenIsPaused_andComesBackOnResume() {
        setContent()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_NONE)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertThat(brightness).isEqualTo(BRIGHTNESS_OVERRIDE_FULL)
    }
}
