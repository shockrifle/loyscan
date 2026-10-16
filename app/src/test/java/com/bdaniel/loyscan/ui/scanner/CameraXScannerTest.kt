package com.bdaniel.loyscan.ui.scanner

import androidx.camera.core.ImageAnalysis
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.Lifecycle
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakeImageProxy
import java.util.concurrent.Executor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The camera lifecycle around a fake [CameraOpener]: neither a camera nor zxing-cpp's native code runs here. */
@RunWith(AndroidJUnit4::class)
class CameraXScannerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeCamera(override val hasTorch: Boolean) : OpenCamera {
        val torch = mutableListOf<Boolean>()
        var closed = false

        override fun enableTorch(on: Boolean) {
            torch += on
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeOpener(private val open: () -> OpenCamera) : CameraOpener {
        var opens = 0
        var analyzer: ImageAnalysis.Analyzer? = null

        override suspend fun open(
            lifecycleOwner: LifecycleOwner,
            onSurfaceRequest: (SurfaceRequest) -> Unit,
            analyzer: ImageAnalysis.Analyzer,
            analyzerExecutor: Executor,
        ): OpenCamera {
            opens++
            this.analyzer = analyzer
            return open()
        }
    }

    private val started = mutableListOf<Boolean>()
    private var failures = 0
    private val scanned = mutableListOf<ScannedBarcode>()
    private val events = CameraEvents(onStarted = { started += it }, onFailed = { failures++ }, onScanned = { scanned += it })

    private var torchOn by mutableStateOf(false)
    private var shown by mutableStateOf(true)

    private fun setContent(opener: CameraOpener, newReader: () -> FrameReader = { FrameReader { null } }) {
        composeRule.setContent {
            if (shown) CameraXScannerContent(torchOn, events, Modifier, opener, newReader)
        }
    }

    @Test
    fun opening_reportsWhetherTheCameraHasATorch() {
        setContent(FakeOpener { FakeCamera(hasTorch = true) })

        composeRule.runOnIdle { assertThat(started).containsExactly(true) }
    }

    @Test
    fun theTorch_followsTheSwitch() {
        val camera = FakeCamera(hasTorch = true)
        setContent(FakeOpener { camera })
        composeRule.runOnIdle { assertThat(camera.torch.last()).isFalse() }

        composeRule.runOnIdle { torchOn = true }

        composeRule.runOnIdle { assertThat(camera.torch.last()).isTrue() }
    }

    @Test
    fun aBarcodeInAFrame_isReportedAsScanned() {
        val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
        val opener = FakeOpener { FakeCamera(hasTorch = false) }
        setContent(opener, newReader = { FrameReader { barcode } })

        composeRule.runOnIdle { opener.analyzer!!.analyze(FakeImageProxy()) }

        composeRule.runOnIdle { assertThat(scanned).containsExactly(barcode) }
    }

    @Test
    fun theTorch_isSwitchedOnAgainAfterAPause() { // CameraX turns it off when the camera closes on pause
        val camera = FakeCamera(hasTorch = true)
        torchOn = true
        setContent(FakeOpener { camera })
        composeRule.runOnIdle { camera.torch.clear() }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.runOnIdle { assertThat(camera.torch).containsExactly(true) }
    }

    @Test
    fun analysis_keepsOnlyTheLatestFrameAtAboutHd() {
        val (_, analysis) = scannerUseCases(onSurfaceRequest = {})

        assertThat(analysis.backpressureStrategy).isEqualTo(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        assertThat(analysis.resolutionSelector!!.resolutionStrategy!!.boundSize).isEqualTo(Size(1280, 720))
    }

    @Test
    fun leaving_closesTheCamera() {
        val camera = FakeCamera(hasTorch = false)
        setContent(FakeOpener { camera })
        composeRule.runOnIdle { assertThat(camera.closed).isFalse() }

        composeRule.runOnIdle { shown = false }

        composeRule.runOnIdle { assertThat(camera.closed).isTrue() }
    }

    @Test
    fun aCameraThatCannotBeOpened_isReportedAsFailed() {
        setContent(FakeOpener { throw IllegalArgumentException("no back camera") })

        composeRule.runOnIdle {
            assertThat(failures).isEqualTo(1)
            assertThat(started).isEmpty()
        }
    }

    @Test
    fun aReaderWhoseNativeLibraryIsMissing_failsWithoutOpeningTheCamera() {
        val opener = FakeOpener { FakeCamera(hasTorch = false) }

        setContent(opener, newReader = { throw UnsatisfiedLinkError("no zxingcpp_android for this ABI") })

        composeRule.runOnIdle {
            assertThat(failures).isEqualTo(1)
            assertThat(opener.opens).isEqualTo(0)
        }
    }
}
