package com.bdaniel.loyscan.ui.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.CancellationSignal
import com.bdaniel.loyscan.domain.ScannedBarcode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import zxingcpp.BarcodeReader
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Callable
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What reading a picked picture found. */
sealed interface PictureScan {
    data class Found(val barcode: ScannedBarcode) : PictureScan
    data object NoBarcode : PictureScan

    /** The picture couldn't be opened or decoded, or the reader couldn't run. */
    data object Unreadable : PictureScan
}

/** Reads a barcode from a picture the user picked (#76); [ZxingCppPictureReader] in the app, a fake in tests. */
fun interface PictureReader {
    suspend fun read(uri: Uri): PictureScan
}

/** Reads a barcode from a software bitmap; `null` when there is none. */
fun interface BitmapReader {
    fun read(bitmap: Bitmap): ScannedBarcode?
}

/**
 * Decodes the picture behind a `content://` URI and reads it with zxing-cpp, with the camera's formats and options, so
 * a picture's scan is the same as a camera scan. Both run off the main thread. The picture is only held in memory
 * while it is read: never copied, stored or sent anywhere.
 */
class ZxingCppPictureReader(
    private val decode: suspend (Uri, CancellationSignal) -> Bitmap,
    private val newReader: () -> BitmapReader,
    private val decodeContext: CoroutineContext = Dispatchers.IO,
    private val readContext: CoroutineContext = Dispatchers.Default,
    private val decodeTimeout: Duration = PICTURE_DECODE_TIMEOUT,
) : PictureReader {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this({ uri, cancel -> decodePicture(context.contentResolver, uri, cancel) }, ::ZxingCppBitmapReader)

    override suspend fun read(uri: Uri): PictureScan {
        val bitmap = try {
            decodeInTime(uri) ?: run {
                Timber.w("Opening the picked picture took longer than %s", decodeTimeout)
                return PictureScan.Unreadable
            }
        } catch (e: IOException) { // missing, not an image, or a format the decoder doesn't know
            Timber.w(e, "Couldn't decode the picked picture")
            return PictureScan.Unreadable
        } catch (e: CancellationException) { // the screen was left: an IllegalStateException, but not a failure
            throw e
        } catch (e: RuntimeException) { // the grant is gone (SecurityException), or the provider failed some other way
            Timber.w(e, "Couldn't open the picked picture")
            return PictureScan.Unreadable
        }
        return withContext(readContext) {
            try {
                newReader().read(bitmap)?.let(PictureScan::Found) ?: PictureScan.NoBarcode
            } catch (e: RuntimeException) {
                Timber.w(e, "Couldn't read the picked picture")
                PictureScan.Unreadable
            } catch (e: UnsatisfiedLinkError) { // zxing-cpp has no native library for this ABI
                Timber.e(e, "Couldn't load the barcode reader")
                PictureScan.Unreadable
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * The decoded picture, or `null` when it takes longer than [decodeTimeout] (#79). Opening it can wait on the network:
     * the photo picker also lists cloud photos, downloaded when opened. The decode blocks in the provider, which a
     * coroutine can't interrupt, so it runs outside the caller's job and the caller stops waiting for it. The provider
     * is asked to stop through a [CancellationSignal]; a picture that still arrives after the timeout, or after the
     * caller was cancelled, is recycled and not read.
     */
    private suspend fun decodeInTime(uri: Uri): Bitmap? {
        val decoded = CompletableDeferred<Bitmap>()
        val download = CancellationSignal()
        decodeScope.launch {
            runCatching { decode(uri, download) }.fold(
                onSuccess = { if (!decoded.complete(it)) it.recycle() },
                onFailure = { decoded.completeExceptionally(it) },
            )
        }
        return try {
            withTimeoutOrNull(decodeTimeout) { decoded.await() }
        } finally {
            // Nothing once it has completed; a picture completing in the same instant is left to the GC.
            decoded.cancel()
            if (decoded.isCancelled) download.cancel() // abandoned (or failed, when cancelling is harmless)
        }
    }

    private val decodeScope = CoroutineScope(SupervisorJob() + decodeContext)
}

/** How long opening a picked picture may take before it is reported as unreadable (#79): a cloud photo downloads. */
internal val PICTURE_DECODE_TIMEOUT = 30.seconds

/** zxing-cpp on a bitmap. Not unit-tested: the native library doesn't run on the JVM (wiki: integrations/zxing-cpp). */
class ZxingCppBitmapReader : BitmapReader {
    private val reader = BarcodeReader(ZXING_CPP_OPTIONS)

    override fun read(bitmap: Bitmap): ScannedBarcode? = firstScannedBarcode(reader.read(bitmap))
}

/**
 * The most pixels a picture is decoded at (32 MB as ARGB_8888). Above any phone or tablet screenshot, so a store app's
 * card is read at its own size: scaling it down could blur a dense barcode's modules. A 50 MP photo is scaled down.
 */
internal const val MAX_PICTURE_PIXELS = 8_000_000L

/**
 * Decodes a picture into a software ARGB_8888 bitmap of at most [maxPixels]. zxing-cpp's JNI side can't lock a hardware
 * bitmap's pixels and reads only 8-bit RGBA (or alpha) ones. Not unit-tested: Robolectric's `ImageDecoder` can't
 * decode a picture (its legacy graphics mode has no decoder, and its native mode doesn't run on Windows).
 */
internal fun decodePicture(
    resolver: ContentResolver,
    uri: Uri,
    cancel: CancellationSignal,
    maxPixels: Long = MAX_PICTURE_PIXELS,
): Bitmap = ImageDecoder.decodeBitmap(
    // Opened with the signal, so a cloud provider can stop its download when the read is abandoned (review of #79).
    ImageDecoder.createSource(
        Callable { resolver.openAssetFileDescriptor(uri, "r", cancel) ?: throw FileNotFoundException("No file for $uri") },
    ),
) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        pictureTargetSize(info.size.width, info.size.height, maxPixels)?.let { (width, height) ->
            decoder.setTargetSize(width, height)
        }
    }.toReadableConfig()

/** This bitmap, or for any other config (a wide-gamut picture decodes as RGBA_F16) an ARGB_8888 copy; the original is recycled. */
internal fun Bitmap.toReadableConfig(): Bitmap {
    if (config == Bitmap.Config.ARGB_8888) return this
    val original = config
    val copy = copy(Bitmap.Config.ARGB_8888, false)
    recycle()
    return copy ?: throw IOException("Couldn't convert a $original picture")
}

/** The size to decode a [width]×[height] picture at so it has at most [maxPixels]; `null` when it already fits. */
internal fun pictureTargetSize(width: Int, height: Int, maxPixels: Long = MAX_PICTURE_PIXELS): Pair<Int, Int>? {
    val pixels = width.toLong() * height
    if (pixels <= maxPixels) return null
    val scale = sqrt(maxPixels.toDouble() / pixels)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    // A short side kept at 1 px would leave the long one over the limit.
    return when {
        targetWidth.toLong() * targetHeight <= maxPixels -> targetWidth to targetHeight
        targetWidth > targetHeight -> (maxPixels / targetHeight).toInt() to targetHeight
        else -> targetWidth to (maxPixels / targetWidth).toInt()
    }
}
