package com.bdaniel.loyscan.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bdaniel.loyscan.ui.scanner.CameraPermission
import com.bdaniel.loyscan.ui.scanner.cameraPermissionStatus
import com.bdaniel.loyscan.ui.scanner.openAppSettings
import timber.log.Timber

/** Takes a card photo with the system camera app (#10). A fake in tests. */
@Stable
interface PhotoCamera {
    /** Whether there is a camera app to take one. */
    val available: Boolean

    /** Opens the camera app to write a photo into [target], asking for the camera permission first if needed. */
    fun take(target: Uri)

    /** Opens the app's system settings, the only way back once the camera is denied for good. */
    fun openSettings()

    enum class Result { TAKEN, NOT_TAKEN, DENIED, DENIED_PERMANENTLY }
}

/**
 * The system camera app, through `ACTION_IMAGE_CAPTURE`. The app declares the camera permission for the scanner, so
 * Android refuses this intent until it is granted: [PhotoCamera.take] asks for it first. A denial is told apart from
 * one for good as the scanner does it ([cameraPermissionStatus]). The target waiting on the dialog is saved, so a
 * rotation under the dialog still opens the camera. On Android 11+ the camera app is seen only through the manifest's
 * `<queries>` entry for `IMAGE_CAPTURE`.
 */
@Composable
fun rememberPhotoCamera(onResult: (PhotoCamera.Result) -> Unit): PhotoCamera {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    var waiting by rememberSaveable { mutableStateOf<Uri?>(null) }
    var denials by rememberSaveable { mutableIntStateOf(0) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        onResult(if (taken) PhotoCamera.Result.TAKEN else PhotoCamera.Result.NOT_TAKEN)
    }
    fun open(target: Uri) = try {
        camera.launch(target)
    } catch (e: ActivityNotFoundException) { // the camera app went since `available` was read
        Timber.w(e, "No camera app to take a card photo")
        currentOnResult(PhotoCamera.Result.NOT_TAKEN)
    }
    val permission: ActivityResultLauncher<String> =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val target = waiting ?: return@rememberLauncherForActivityResult
            waiting = null
            if (granted) return@rememberLauncherForActivityResult open(target)
            denials++
            val status = cameraPermissionStatus(context, activity, denials)
            onResult(
                if (status == CameraPermission.Status.DENIED_PERMANENTLY) PhotoCamera.Result.DENIED_PERMANENTLY
                else PhotoCamera.Result.DENIED,
            )
        }
    val available = remember(context) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }
    return remember(available, camera, permission) {
        object : PhotoCamera {
            override val available = available

            override fun take(target: Uri) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) return open(target)
                waiting = target
                permission.launch(Manifest.permission.CAMERA)
            }

            override fun openSettings() = openAppSettings(context)
        }
    }
}
