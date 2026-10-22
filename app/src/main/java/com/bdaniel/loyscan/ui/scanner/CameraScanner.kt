package com.bdaniel.loyscan.ui.scanner

import android.content.Context
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bdaniel.loyscan.domain.ScannedBarcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * What the camera reports to the scanner screen. Called on the main thread. [onAvailable] follows errors CameraX reports
 * after opening (another app has the camera, it was disconnected): `false` while there is one, `true` once it is open
 * again.
 */
class CameraEvents(
    val onStarted: (torchAvailable: Boolean) -> Unit,
    val onFailed: () -> Unit,
    val onAvailable: (available: Boolean) -> Unit,
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

    /** `false` when the camera reports an error, `true` when it is open again ([cameraAvailability]). */
    val available: Flow<Boolean>

    fun enableTorch(on: Boolean)

    /** Unbinds the preview and analysis. */
    fun close()
}

/**
 * Binds a preview (its surface requests go to [onSurfaceRequest]) and an analysis running [analyzer] on
 * [analyzerExecutor] to the back camera for [lifecycleOwner]; throws when there is none to open.
 */
fun interface CameraOpener {
    suspend fun open(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        analyzer: ImageAnalysis.Analyzer,
        analyzerExecutor: Executor,
    ): OpenCamera
}

/**
 * Whether a camera state CameraX reports after binding means the camera can be used: `false` with any error (some
 * clear up on their own, e.g. when another app lets the camera go, and CameraX opens it again), `true` once it is
 * open, `null` for the other states, which come and go on pause and resume.
 */
internal fun cameraAvailability(state: CameraState): Boolean? = when {
    state.error != null -> false
    state.type == CameraState.Type.OPEN -> true
    else -> null
}

/** A camera's states as [OpenCamera.available]: only the changes, so a pause and resume don't repeat `true`. */
internal fun LiveData<CameraState>.availability(): Flow<Boolean> =
    observeAsFlow().mapNotNull(::cameraAvailability).distinctUntilChanged()

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
    val currentTorchOn by rememberUpdatedState(torchOn)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<OpenCamera?>(null) }

    LaunchedEffect(opener, lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var opened: OpenCamera? = null
        try {
            val analyzer = ScanAnalyzer(newReader(), ContextCompat.getMainExecutor(context)) { currentEvents.onScanned(it) }
            opened = opener.open(lifecycleOwner, { surfaceRequest = it }, analyzer, analysisExecutor)
            camera = opened
            currentEvents.onStarted(opened.hasTorch)
            opened.available.collect { available ->
                // CameraX opens the camera again with the torch off, as after a pause.
                if (available) opened.enableTorch(currentTorchOn)
                currentEvents.onAvailable(available)
            }
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
            analysisExecutor.shutdown()
        }
    }

    // Also on every resume: CameraX turns the torch off when it closes the camera on pause, and reopens the same one.
    LifecycleResumeEffect(camera, torchOn) {
        camera?.enableTorch(torchOn)
        onPauseOrDispose {}
    }

    Box(modifier) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it) }
    }
}

/**
 * The scanner's two use cases: a preview, and an analysis that keeps only the latest frame (a slow read skips frames
 * instead of queueing them) at about 1280×720, enough pixels for the thin bars of a 1D barcode.
 */
internal fun scannerUseCases(onSurfaceRequest: (SurfaceRequest) -> Unit): Pair<Preview, ImageAnalysis> {
    val preview = Preview.Builder().build().apply { setSurfaceProvider(onSurfaceRequest) }
    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build(),
        )
        .build()
    return preview to analysis
}

/** CameraX's process-wide camera provider. Not unit-tested: there is no camera on Robolectric. */
private class ProcessCameraOpener(private val context: Context) : CameraOpener {
    override suspend fun open(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        analyzer: ImageAnalysis.Analyzer,
        analyzerExecutor: Executor,
    ): OpenCamera {
        val (preview, analysis) = scannerUseCases(onSurfaceRequest)
        analysis.setAnalyzer(analyzerExecutor, analyzer)
        val provider = ProcessCameraProvider.awaitInstance(context)
        val camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        return object : OpenCamera {
            override val hasTorch = camera.cameraInfo.hasFlashUnit()
            override val available = camera.cameraInfo.cameraState.availability()
            override fun enableTorch(on: Boolean) {
                camera.cameraControl.enableTorch(on)
            }
            override fun close() {
                provider.unbind(preview, analysis)
                analysis.clearAnalyzer()
            }
        }
    }
}

/**
 * A [LiveData]'s values as a flow, observed while collected. Collect it on the main thread, as LiveData asks. Own code,
 * not lifecycle-livedata's `asFlow`, which the app has only as a transitive dependency of CameraX.
 */
internal fun <T : Any> LiveData<T>.observeAsFlow(): Flow<T> = callbackFlow {
    val observer = Observer<T> { trySend(it) }
    observeForever(observer)
    awaitClose { removeObserver(observer) }
}
