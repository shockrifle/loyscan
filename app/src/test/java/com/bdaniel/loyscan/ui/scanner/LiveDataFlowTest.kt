package com.bdaniel.loyscan.ui.scanner

import androidx.camera.core.CameraState
import androidx.camera.core.CameraState.StateError
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** The bridge that turns CameraX's camera state (a LiveData) into the scanner's availability flow (#40). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LiveDataFlowTest {

    @Test
    fun values_areEmittedWhileCollected_andObservingStopsWithTheCollector() = runTest(UnconfinedTestDispatcher()) {
        val liveData = MutableLiveData(1)
        val values = mutableListOf<Int>()

        val collector = launch { liveData.observeAsFlow().collect { values += it } }
        liveData.value = 2

        assertThat(values).containsExactly(1, 2).inOrder()
        assertThat(liveData.hasObservers()).isTrue()

        collector.cancel()

        assertThat(liveData.hasObservers()).isFalse()
    }

    @Test
    fun cameraStates_becomeAvailabilityChangesOnly() = runTest(UnconfinedTestDispatcher()) { // #40
        val states = MutableLiveData(CameraState.create(CameraState.Type.OPEN))
        val availability = mutableListOf<Boolean>()
        val collector = launch { states.availability().collect { availability += it } }

        listOf(
            CameraState.create(CameraState.Type.CLOSED), // pause
            CameraState.create(CameraState.Type.OPEN), // resume
            CameraState.create(CameraState.Type.PENDING_OPEN, StateError.create(CameraState.ERROR_CAMERA_IN_USE)),
            CameraState.create(CameraState.Type.PENDING_OPEN, StateError.create(CameraState.ERROR_CAMERA_IN_USE)),
            CameraState.create(CameraState.Type.OPENING),
            CameraState.create(CameraState.Type.OPEN),
        ).forEach { states.value = it }
        collector.cancel()

        assertThat(availability).containsExactly(true, false, true).inOrder()
    }
}
