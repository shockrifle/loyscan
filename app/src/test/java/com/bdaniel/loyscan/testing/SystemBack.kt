package com.bdaniel.loyscan.testing

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.robolectric.shadows.ShadowDialog

/**
 * System Back and taps around a dialog, as a device sends them to the window in front (#209). Back on the screen goes
 * to the activity's `OnBackPressedDispatcher`, which the back gesture and the Back button both reach; a Compose dialog
 * is a window of its own with its own dispatcher, so Back while it shows goes to that one instead. Call [capture] in
 * `setContent`. Each press waits for idle first, as a device draws a frame between a keystroke and Back: a
 * `BackHandler` enabled by the keystroke is only enabled once that recomposition has run.
 */
class SystemBack(private val rule: ComposeContentTestRule) {

    private var dispatcher: OnBackPressedDispatcher? = null

    @Composable
    fun capture() {
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    }

    /** Back with no dialog showing. */
    fun onTheScreen() {
        rule.waitForIdle()
        rule.runOnUiThread { checkNotNull(dispatcher) { "capture() wasn't called in setContent" }.onBackPressed() }
        rule.waitForIdle()
    }

    /** Back while a dialog shows: the latest dialog's own dispatcher. */
    fun inTheDialog() {
        rule.waitForIdle()
        rule.runOnUiThread { dialog().onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** A tap outside the latest dialog's window, which the dialog reads as a tap outside it. */
    fun tapOutsideTheDialog() {
        rule.waitForIdle()
        rule.runOnUiThread {
            val dialog = dialog()
            val time = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(time, time, action, OUTSIDE, OUTSIDE, 0)
                dialog.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        rule.waitForIdle()
    }

    private fun dialog(): ComponentDialog = checkNotNull(ShadowDialog.getLatestDialog() as? ComponentDialog) { "No dialog is showing" }

    private companion object {
        /** Well outside the window, past the touch slop the platform allows at its edge. */
        const val OUTSIDE = -100f
    }
}
