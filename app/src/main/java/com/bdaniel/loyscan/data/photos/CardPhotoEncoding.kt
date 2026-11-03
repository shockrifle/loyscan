package com.bdaniel.loyscan.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The longest side, in pixels, a card photo is stored at (#88). */
internal const val MAX_PHOTO_SIDE = 2048

/** A card photo's JPEG quality: re-encoded once, on import. */
private const val PHOTO_JPEG_QUALITY = 90

/**
 * Writes the picture in [source] to [target] as a JPEG at most [maxSide] pixels on its long side, never enlarged, and
 * turned the way its EXIF orientation says (#88). The JPEG is encoded afresh, so none of the picture's metadata (its
 * location included) comes along. A transparent picture is put on white, since a JPEG has none and would show it black.
 * Throws an [IOException] if [source] isn't a picture that can be decoded.
 */
internal fun encodeCardPhoto(source: File, target: File, maxSide: Int = MAX_PHOTO_SIDE) {
    // From a stream closed here: under Robolectric on Windows, decodeFile left the file open, so it couldn't be deleted.
    fun decode(options: BitmapFactory.Options) = source.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also(::decode)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a picture")
    // Decoded at a power-of-two fraction that still covers maxSide, so a 50 MP photo isn't decoded at full size.
    val longSide = max(bounds.outWidth, bounds.outHeight)
    val decoded = decode(BitmapFactory.Options().apply { inSampleSize = sampleSize(longSide, maxSide) })
        ?: throw IOException("Couldn't decode the picture")
    try {
        val orientation = exifOrientation(source)
        val turned = orientation in TURNED_ORIENTATIONS
        val (shownWidth, shownHeight) = if (turned) decoded.height to decoded.width else decoded.width to decoded.height
        val scale = min(1f, maxSide.toFloat() / max(shownWidth, shownHeight))
        val width = max(1, (shownWidth * scale).roundToInt())
        val height = max(1, (shownHeight * scale).roundToInt())
        val transform = orientationMatrix(orientation, decoded.width.toFloat(), decoded.height.toFloat()).apply {
            postScale(width.toFloat() / shownWidth, height.toFloat() / shownHeight)
        }
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(photo).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, transform, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val encoded = target.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, it) }
            if (!encoded) throw IOException("Couldn't encode the photo")
        } finally {
            photo.recycle()
        }
    } finally {
        decoded.recycle()
    }
}

/** The largest power of two to divide [longSide] by that still leaves at least [maxSide]: BitmapFactory's `inSampleSize`. */
private fun sampleSize(longSide: Int, maxSide: Int): Int {
    var sample = 1
    while (longSide / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** The picture's EXIF orientation; normal when it has none, or its EXIF can't be read. */
private fun exifOrientation(picture: File): Int = try {
    picture.inputStream().use { ExifInterface(it) }.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
} catch (e: IOException) {
    ExifInterface.ORIENTATION_NORMAL
}

/** The orientations that swap a picture's width and height. */
private val TURNED_ORIENTATIONS = setOf(
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270,
)

/** Maps a stored [width]×[height] picture's pixels to where [orientation] shows them, with the top left at the origin. */
private fun orientationMatrix(orientation: Int, width: Float, height: Float) = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setValues(floatArrayOf(-1f, 0f, width, 0f, 1f, 0f, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_ROTATE_180 -> setValues(floatArrayOf(-1f, 0f, width, 0f, -1f, height, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setValues(floatArrayOf(1f, 0f, 0f, 0f, -1f, height, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_TRANSPOSE -> setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_ROTATE_90 -> setValues(floatArrayOf(0f, -1f, height, 1f, 0f, 0f, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_TRANSVERSE -> setValues(floatArrayOf(0f, -1f, height, -1f, 0f, width, 0f, 0f, 1f))
        ExifInterface.ORIENTATION_ROTATE_270 -> setValues(floatArrayOf(0f, 1f, 0f, -1f, 0f, width, 0f, 0f, 1f))
        else -> Unit // normal, or a value EXIF doesn't define
    }
}
