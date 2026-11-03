package com.bdaniel.loyscan.testing

import android.net.Uri
import com.bdaniel.loyscan.ui.PhotoCamera

/**
 * A [PhotoCamera] that answers [take] at once with [answer]; `null` leaves the camera app open. [taken] lists the
 * targets it was given, [settingsOpened] counts trips to the settings.
 */
class FakePhotoCamera(override val available: Boolean = true) : PhotoCamera {
    var answer: PhotoCamera.Result? = PhotoCamera.Result.TAKEN
    var onResult: (PhotoCamera.Result) -> Unit = {}
    val taken = mutableListOf<Uri>()
    var settingsOpened = 0

    override fun take(target: Uri) {
        taken += target
        answer?.let(onResult)
    }

    override fun openSettings() {
        settingsOpened++
    }
}
