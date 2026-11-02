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
import kotlin.math.roundToInt

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
) : PictureReader {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this({ decodePicture(context.contentResolver, it) }, ::ZxingCppBitmapReader)

    override suspend fun read(uri: Uri): PictureScan = withContext(Dispatchers.Default) {
        val bitmap = try {
            decode(uri)
        } catch (e: IOException) { // missing, not an image, or a format the decoder doesn't know
            Timber.w(e, "Couldn't decode the picked picture")
            return@withContext PictureScan.Unreadable
        } catch (e: SecurityException) { // the picker's grant is gone
            Timber.w(e, "Couldn't open the picked picture")
            return@withContext PictureScan.Unreadable
        }
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

/** zxing-cpp on a bitmap. Not unit-tested: the native library doesn't run on the JVM (wiki: integrations/zxing-cpp). */
class ZxingCppBitmapReader : BitmapReader {
    private val reader = BarcodeReader(ZXING_CPP_OPTIONS)

    override fun read(bitmap: Bitmap): ScannedBarcode? = firstScannedBarcode(reader.read(bitmap))
}

/** The long side a picture is decoded at, at most: plenty for a barcode, and a 50 MP photo doesn't fill the memory. */
internal const val MAX_PICTURE_SIDE = 2048

/**
 * Decodes a picture into a software ARGB_8888 bitmap, at most [maxSide] on its long side. zxing-cpp's JNI side can't
 * lock a hardware bitmap's pixels and reads only 8-bit RGBA (or alpha) ones, so a wide-gamut picture decoded as
 * RGBA_F16 is copied. Not unit-tested: Robolectric's `ImageDecoder` can't decode a picture (its legacy graphics mode
 * has no decoder, and its native mode doesn't run on Windows).
 */
internal fun decodePicture(resolver: ContentResolver, uri: Uri, maxSide: Int = MAX_PICTURE_SIDE): Bitmap {
    val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        pictureTargetSize(info.size.width, info.size.height, maxSide)?.let { (width, height) ->
            decoder.setTargetSize(width, height)
        }
    }
    if (decoded.config == Bitmap.Config.ARGB_8888) return decoded
    val copy = decoded.copy(Bitmap.Config.ARGB_8888, false)
    decoded.recycle()
    return copy ?: throw IOException("Couldn't convert a ${decoded.config} picture")
}

/** The size to decode a [width]×[height] picture at so its long side is at most [maxSide]; `null` when it already fits. */
internal fun pictureTargetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int>? {
    val longSide = maxOf(width, height)
    if (longSide <= maxSide) return null
    val scale = maxSide.toDouble() / longSide
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}
