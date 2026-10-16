package com.bdaniel.loyscan.ui.scanner

import android.content.Context
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bdaniel.loyscan.domain.ScannedBarcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** What the camera reports to the scanner screen. Called on the main thread. */
class CameraEvents(
    val onStarted: (torchAvailable: Boolean) -> Unit,
    val onFailed: () -> Unit,
    val onScanned: (ScannedBarcode) -> Unit,
)

/** A camera preview that reads barcodes; [CameraXScanner] in the app, a fake in tests. */
typealias ScannerCamera = @Composable (torchOn: Boolean, events: CameraEvents, modifier: Modifier) -> Unit

/** The back camera through CameraX, every frame read by zxing-cpp (wiki: integrations/zxing-cpp). */
val CameraXScanner: ScannerCamera = { torchOn, events, modifier ->
    val context = LocalContext.current
    val opener = remember(context) { ProcessCameraOpener(context) }
    CameraXScannerContent(torchOn, events, modifier, opener, newReader = ::ZxingCppFrameReader)
}

/** A camera bound to a lifecycle. */
interface OpenCamera {
    val hasTorch: Boolean
    fun enableTorch(on: Boolean)

    /** Unbinds the preview and analysis. */
    fun close()
}

/** Binds [preview] and [analysis] to the back camera for [lifecycleOwner]; throws when there is none to open. */
fun interface CameraOpener {
    suspend fun open(lifecycleOwner: LifecycleOwner, preview: Preview, analysis: ImageAnalysis): OpenCamera
}

/** Reads a barcode from one frame; `null` when there is none. Called on the analysis thread. */
fun interface FrameReader {
    fun read(image: ImageProxy): ScannedBarcode?
}

/** Hands every barcode [reader] finds to [onScanned] on [callbackExecutor], and closes each frame. */
class ScanAnalyzer(
    private val reader: FrameReader,
    private val callbackExecutor: Executor,
    private val onScanned: (ScannedBarcode) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val barcode = try {
            image.use(reader::read)
        } catch (e: RuntimeException) { // one unreadable frame mustn't crash the app; the next one may read
            Timber.w(e, "Couldn't read a camera frame")
            null
        }
        if (barcode != null) callbackExecutor.execute { onScanned(barcode) }
    }
}

/**
 * Opens the camera while in composition and the destination's lifecycle allows it (CameraX stops it on pause), and
 * reports each step to [events]. [newReader] is created before the camera opens, so a reader that can't load fails
 * the scanner without touching the camera.
 */
@Composable
internal fun CameraXScannerContent(
    torchOn: Boolean,
    events: CameraEvents,
    modifier: Modifier,
    opener: CameraOpener,
    newReader: () -> FrameReader,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEvents by rememberUpdatedState(events)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<OpenCamera?>(null) }

    LaunchedEffect(opener, lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
        var opened: OpenCamera? = null
        try {
            val analyzer = ScanAnalyzer(newReader(), ContextCompat.getMainExecutor(context)) { currentEvents.onScanned(it) }
            analysis.setAnalyzer(analysisExecutor, analyzer)
            opened = opener.open(lifecycleOwner, preview, analysis)
            camera = opened
            currentEvents.onStarted(opened.hasTorch)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // no back camera, or CameraX couldn't initialise or bind
            Timber.e(e, "Couldn't open the camera")
            currentEvents.onFailed()
        } catch (e: UnsatisfiedLinkError) { // zxing-cpp has no native library for this ABI
            Timber.e(e, "Couldn't load the barcode reader")
            currentEvents.onFailed()
        } finally {
            camera = null
            opened?.close()
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.enableTorch(torchOn)
    }

    Box(modifier) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it) }
    }
}

/** Analysis frames: enough pixels for the thin bars of a 1D barcode, few enough to keep up with the preview. */
private val ANALYSIS_SIZE = Size(1280, 720)

/** CameraX's process-wide camera provider. Not unit-tested: there is no camera on Robolectric. */
private class ProcessCameraOpener(private val context: Context) : CameraOpener {
    override suspend fun open(lifecycleOwner: LifecycleOwner, preview: Preview, analysis: ImageAnalysis): OpenCamera {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        return object : OpenCamera {
            override val hasTorch = camera.cameraInfo.hasFlashUnit()
            override fun enableTorch(on: Boolean) {
                camera.cameraControl.enableTorch(on)
            }
            override fun close() = provider.unbind(preview, analysis)
        }
    }
}
