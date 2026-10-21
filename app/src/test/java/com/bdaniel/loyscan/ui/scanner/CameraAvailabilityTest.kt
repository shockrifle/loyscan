package com.bdaniel.loyscan.ui.scanner

import androidx.camera.core.CameraState
import androidx.camera.core.CameraState.StateError
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** #40: which camera states CameraX reports after binding make the scanner say the camera isn't available. */
class CameraAvailabilityTest {

    @Test
    fun anError_makesTheCameraUnavailable_whateverTheState() {
        listOf(
            CameraState.create(CameraState.Type.PENDING_OPEN, StateError.create(CameraState.ERROR_CAMERA_IN_USE)),
            CameraState.create(CameraState.Type.OPENING, StateError.create(CameraState.ERROR_MAX_CAMERAS_IN_USE)),
            CameraState.create(CameraState.Type.CLOSING, StateError.create(CameraState.ERROR_CAMERA_DISABLED)),
            CameraState.create(CameraState.Type.CLOSED, StateError.create(CameraState.ERROR_CAMERA_REMOVED)),
        ).forEach { state ->
            assertThat(cameraAvailability(state)).isFalse()
        }
    }

    @Test
    fun anOpenCamera_isAvailable() {
        assertThat(cameraAvailability(CameraState.create(CameraState.Type.OPEN))).isTrue()
    }

    @Test
    fun otherStatesWithoutAnError_changeNothing() { // e.g. closed on pause, opening again on resume
        listOf(CameraState.Type.PENDING_OPEN, CameraState.Type.OPENING, CameraState.Type.CLOSING, CameraState.Type.CLOSED)
            .forEach { type -> assertThat(cameraAvailability(CameraState.create(type))).isNull() }
    }
}
