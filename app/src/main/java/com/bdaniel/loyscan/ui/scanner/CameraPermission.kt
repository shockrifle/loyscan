package com.bdaniel.loyscan.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** The camera permission as the scanner screen sees it. */
@Stable
interface CameraPermission {
    val status: Status

    /** Shows the system's permission dialog. */
    fun request()

    /** Opens the app's system settings, the only way back once the user has denied the permission for good. */
    fun openSettings()

    enum class Status { GRANTED, NOT_ASKED, DENIED, DENIED_PERMANENTLY }
}

/**
 * The real camera permission. The status is read again on every resume, so granting it in the system settings and
 * coming back shows the camera.
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Denials answered on this visit, and whether the system dialog is up. Saved, so a dialog that is still open
    // over a rotation isn't requested a second time (Android would answer that one with an instant denial).
    var denials by rememberSaveable { mutableIntStateOf(0) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    // Bumped to re-read the status after the dialog and on resume.
    var checks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requesting = false
        if (!granted) denials++
        checks++
    }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val status = remember(denials, checks) { cameraPermissionStatus(context, activity, denials) }
    return remember(status) {
        object : CameraPermission {
            override val status = status
            override fun request() {
                if (requesting) return
                requesting = true
                launcher.launch(Manifest.permission.CAMERA)
            }
            override fun openSettings() = context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Without a rationale after a denial, the system won't show its dialog again. Except after the first one: on
 * Android 11+ closing the dialog unanswered also leaves no rationale, and the system still asks next time. So only a
 * repeated denial counts as for good; a user who denied for good on an earlier visit taps "Allow camera" once more.
 */
internal fun cameraPermissionStatus(context: Context, activity: Activity?, denials: Int): CameraPermission.Status = when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
        CameraPermission.Status.GRANTED
    denials == 0 -> CameraPermission.Status.NOT_ASKED
    denials == 1 || activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true ->
        CameraPermission.Status.DENIED
    else -> CameraPermission.Status.DENIED_PERMANENTLY
}
