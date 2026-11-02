package com.bdaniel.loyscan.ui.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.bdaniel.loyscan.domain.ScannedBarcode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import zxingcpp.BarcodeReader
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.sqrt

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
    private val decode: (Uri) -> Bitmap,
    private val newReader: () -> BitmapReader,
    private val decodeContext: CoroutineContext = Dispatchers.IO,
    private val readContext: CoroutineContext = Dispatchers.Default,
) : PictureReader {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this({ decodePicture(context.contentResolver, it) }, ::ZxingCppBitmapReader)

    override suspend fun read(uri: Uri): PictureScan {
        val bitmap = try {
            // Opening it can wait on the network: the photo picker also lists cloud photos, downloaded when opened.
            withContext(decodeContext) { decode(uri) }
        } catch (e: IOException) { // missing, not an image, or a format the decoder doesn't know
            Timber.w(e, "Couldn't decode the picked picture")
            return PictureScan.Unreadable
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
}

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
internal fun decodePicture(resolver: ContentResolver, uri: Uri, maxPixels: Long = MAX_PICTURE_PIXELS): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
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
