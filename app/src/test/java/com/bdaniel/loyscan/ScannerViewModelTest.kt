package com.bdaniel.loyscan

import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = ScannerViewModel(hintDelay = 8.seconds)

    @Test
    fun hint_appearsOnceTheCameraHasRunForTheHintDelay() = runTest {
        viewModel.onCameraStarted(torchAvailable = false)

        advanceTimeBy(7.9.seconds)
        assertThat(viewModel.state.value.showHint).isFalse()
        advanceTimeBy(0.2.seconds)
        assertThat(viewModel.state.value.showHint).isTrue()
    }

    @Test
    fun hint_doesNotCountTimeBeforeTheCameraStarts() = runTest {
        advanceTimeBy(60.seconds)
        assertThat(viewModel.state.value.showHint).isFalse()

        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(7.9.seconds)

        assertThat(viewModel.state.value.showHint).isFalse()
    }

    @Test
    fun hint_isNotDelayedAgainWhenTheCameraRestarts() = runTest { // e.g. rotation rebinds the camera
        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(5.seconds)

        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(3.1.seconds)

        assertThat(viewModel.state.value.showHint).isTrue()
    }

    @Test
    fun torch_togglesOnlyWhenTheCameraHasOne() = runTest {
        viewModel.toggleTorch()
        assertThat(viewModel.state.value.torchOn).isFalse()

        viewModel.onCameraStarted(torchAvailable = true)
        viewModel.toggleTorch()
        assertThat(viewModel.state.value).isEqualTo(ScannerViewModel.State(torchAvailable = true, torchOn = true))

        viewModel.toggleTorch()
        assertThat(viewModel.state.value.torchOn).isFalse()
    }

    @Test
    fun onScanned_acceptsTheFirstScanOnly() {
        assertThat(viewModel.onScanned()).isTrue()
        assertThat(viewModel.onScanned()).isFalse()
        assertThat(viewModel.onScanned()).isFalse()
    }

    @Test
    fun cameraFailure_isReported() = runTest {
        viewModel.onCameraFailed()
        runCurrent()

        assertThat(viewModel.state.value.cameraFailed).isTrue()
    }
}
