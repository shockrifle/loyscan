package com.bdaniel.loyscan.ui

import android.view.Window
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Turns the window to full brightness, see [WhileResumed]. */
@Composable
fun FullBrightness() = WhileResumed {
    val original = attributes.screenBrightness
    attributes = attributes.apply { screenBrightness = BRIGHTNESS_OVERRIDE_FULL }
    return@WhileResumed { attributes = attributes.apply { screenBrightness = original } }
}

/** Keeps the screen from sleeping, see [WhileResumed]. */
@Composable
fun KeepScreenOn() = WhileResumed {
    if (attributes.flags and FLAG_KEEP_SCREEN_ON != 0) return@WhileResumed {}
    addFlags(FLAG_KEEP_SCREEN_ON)
    return@WhileResumed { clearFlags(FLAG_KEEP_SCREEN_ON) }
}

/**
 * Applies [effect] to the activity's window while the caller is in composition and its lifecycle (the navigation
 * destination, inside a NavHost) is resumed, and runs the undo it returns on pause (home, another app, the next
 * destination) and when the caller leaves composition (back).
 */
@Composable
private fun WhileResumed(effect: Window.() -> (Window.() -> Unit)) {
    val window = LocalActivity.current?.window ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle) {
        var undo: (Window.() -> Unit)? = null
        fun restore() {
            undo?.invoke(window)
            undo = null
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (undo == null) undo = window.effect()
                Lifecycle.Event.ON_PAUSE -> restore()
                else -> Unit
            }
        }
        // Replays the events up to the current state, so an already resumed screen applies it at once.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            restore()
        }
    }
}
