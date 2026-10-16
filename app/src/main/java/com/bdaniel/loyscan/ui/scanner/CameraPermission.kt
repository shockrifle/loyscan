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
    // Whether this screen has asked yet; after a denial, the rationale flag tells "ask again" from "denied for good".
    var asked by rememberSaveable { mutableStateOf(false) }
    // Bumped to re-read the status after the dialog and on resume.
    var checks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        checks++
    }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val status = remember(asked, checks) { cameraPermissionStatus(context, activity, asked) }
    return remember(status) {
        object : CameraPermission {
            override val status = status
            override fun request() = launcher.launch(Manifest.permission.CAMERA)
            override fun openSettings() = context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

internal fun cameraPermissionStatus(context: Context, activity: Activity?, asked: Boolean): CameraPermission.Status = when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
        CameraPermission.Status.GRANTED
    !asked -> CameraPermission.Status.NOT_ASKED
    activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true -> CameraPermission.Status.DENIED
    else -> CameraPermission.Status.DENIED_PERMANENTLY
}
