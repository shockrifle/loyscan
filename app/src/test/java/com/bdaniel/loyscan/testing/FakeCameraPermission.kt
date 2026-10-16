package com.bdaniel.loyscan.testing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bdaniel.loyscan.ui.scanner.CameraPermission

/** A [CameraPermission] whose [status] the test sets; it counts requests and trips to the settings. */
class FakeCameraPermission(status: CameraPermission.Status) : CameraPermission {
    override var status by mutableStateOf(status)
    var requests = 0
    var settingsOpened = 0

    override fun request() {
        requests++
    }

    override fun openSettings() {
        settingsOpened++
    }
}
