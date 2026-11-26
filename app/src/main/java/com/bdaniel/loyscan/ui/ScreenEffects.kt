package com.bdaniel.loyscan.ui

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.view.Window
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
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
 * Draws the status bar's icons [dark] (for a light colour behind them) or light, see [WhileResumed]; the undo puts back
 * the window's own appearance, the one the rest of the app uses (#233). A [StatusBarIcons] composed inside another one
 * (scan mode over the card) applies after it and is undone before it, so each restores what it found.
 */
@Composable
fun StatusBarIcons(dark: Boolean) = WhileResumed(key = dark) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    val original = controller.isAppearanceLightStatusBars
    controller.isAppearanceLightStatusBars = dark
    return@WhileResumed { controller.isAppearanceLightStatusBars = original }
}

/**
 * Holds a phone in portrait unless the screen [rotates] (#63): in landscape a phone is too short for the card list's
 * tiles, and only the card screen gains from the width. A screen 600dp or wider follows the device, as Android 16
 * makes it do anyway. With [rotates] null (the screen isn't known yet), nothing is requested: after a recreation the
 * back stack is restored a frame late, and asking for portrait then would turn a card held in landscape back.
 */
@Composable
fun ScreenOrientation(rotates: Boolean?) {
    val activity = LocalActivity.current ?: return
    if (rotates == null) return
    val phone = LocalConfiguration.current.smallestScreenWidthDp < LARGE_SCREEN_MIN_WIDTH_DP
    val orientation = if (phone && !rotates) SCREEN_ORIENTATION_PORTRAIT else SCREEN_ORIENTATION_UNSPECIFIED
    SideEffect { activity.requestedOrientation = orientation }
}

/** The smallest width from which Android 16 ignores an app's orientation request. */
private const val LARGE_SCREEN_MIN_WIDTH_DP = 600

/**
 * Applies [effect] to the activity's window while the caller is in composition and its lifecycle (the navigation
 * destination, inside a NavHost) is resumed, and runs the undo it returns on pause (home, another app, the next
 * destination) and when the caller leaves composition (back). A changed [key] undoes the effect and applies it again.
 */
@Composable
private fun WhileResumed(key: Any? = null, effect: Window.() -> (Window.() -> Unit)) {
    val window = LocalActivity.current?.window ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle, key) {
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
