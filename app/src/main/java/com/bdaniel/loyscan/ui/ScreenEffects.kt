package com.bdaniel.loyscan.ui

import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Turns the window to full brightness while the caller is in composition and its lifecycle (the navigation
 * destination, inside a NavHost) is resumed. The window's own value comes back on pause (home, another app, the
 * next destination) and when the caller leaves composition (back).
 */
@Composable
fun FullBrightness() {
    val window = LocalActivity.current?.window ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle) {
        val original = window.attributes.screenBrightness
        fun setBrightness(value: Float) {
            window.attributes = window.attributes.apply { screenBrightness = value }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> setBrightness(BRIGHTNESS_OVERRIDE_FULL)
                Lifecycle.Event.ON_PAUSE -> setBrightness(original)
                else -> Unit
            }
        }
        // Replays the events up to the current state, so an already resumed screen brightens at once.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            setBrightness(original)
        }
    }
}
