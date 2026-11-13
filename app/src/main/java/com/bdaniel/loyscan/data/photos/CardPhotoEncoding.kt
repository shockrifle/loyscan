package com.bdaniel.loyscan.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The longest side, in pixels, a card photo is stored at (#88). */
internal const val MAX_PHOTO_SIDE = 2048

/** The longest side, in pixels, a store logo is stored at (#151): it is drawn at most tile-sized. */
const val MAX_LOGO_SIDE = 512

/** A card photo's JPEG quality: re-encoded once, on import. */
private const val PHOTO_JPEG_QUALITY = 90

/**
 * A sub-rectangle of a picture to keep, as fractions of its upright width and height (#10): `(0, 0)` is the top left,
 * `(1, 1)` the bottom right. [WHOLE] keeps all of it. The crop screen ([com.bdaniel.loyscan.ui.PhotoCropScreen]) makes
 * one from the user's pan and zoom; it is in the upright picture, the way its EXIF orientation shows it, so
 * [encodeCardPhoto] turns the picture before it crops.
 */
data class PhotoCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite)) { "the crop must be finite: $left, $top, $right, $bottom" }
        require(right > left && bottom > top) { "the crop must have an area: $left..$right, $top..$bottom" }
    }

    /**
     * Whether the crop lies within the picture. Only a logo's may reach past it (#151): framed zoomed out, so that a wide
     * wordmark fits a square frame whole; a card photo's frame is always covered.
     */
    val isWithinPicture: Boolean
        get() = left >= 0f && top >= 0f && right <= 1f && bottom <= 1f

    companion object {
        val WHOLE = PhotoCrop(0f, 0f, 1f, 1f)
    }
}

/**
 * Writes the picture in [source] to [target] as a JPEG at most [maxSide] pixels on its long side, never enlarged,
 * turned the way its EXIF orientation says and cropped to [crop] (#88, #10). The JPEG is encoded afresh, so none of the
 * picture's metadata (its location included) comes along. A transparent picture is put on white, since a JPEG has none
 * and would show it black. Throws an [IOException] if [source] isn't a picture that can be decoded.
 */
internal fun encodeCardPhoto(source: File, target: File, crop: PhotoCrop = PhotoCrop.WHOLE, maxSide: Int = MAX_PHOTO_SIDE) {
    require(crop.isWithinPicture) { "A card photo's crop must be within the picture: $crop" }
    encodePicture(source, target, crop, maxSide, background = Color.WHITE, Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY)
}

/**
 * Writes the picture in [source] to [target] as a store's logo (#151): a PNG at most [MAX_LOGO_SIDE] pixels on its long
 * side, never enlarged, upright, cropped to [crop] and without metadata, like a card photo, but keeping its transparency
 * so it shows on the brand colour. Where [crop] reaches past the picture, the logo is transparent.
 */
internal fun encodeStoreLogo(source: File, target: File, crop: PhotoCrop = PhotoCrop.WHOLE) =
    encodePicture(source, target, crop, MAX_LOGO_SIDE, background = null, Bitmap.CompressFormat.PNG, quality = 100)

/** The shared encoding of [encodeCardPhoto] and [encodeStoreLogo]; [background] fills the canvas first, `null` leaves it transparent. */
private fun encodePicture(
    source: File,
    target: File,
    crop: PhotoCrop,
    maxSide: Int,
    background: Int?,
    format: Bitmap.CompressFormat,
    quality: Int,
) {
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
        // The kept region, in the upright picture's pixels. The frame is cropped after the picture is turned upright.
        val cropLeft = crop.left * shownWidth
        val cropTop = crop.top * shownHeight
        val cropWidth = (crop.right - crop.left) * shownWidth
        val cropHeight = (crop.bottom - crop.top) * shownHeight
        val scale = min(1f, maxSide.toFloat() / max(cropWidth, cropHeight))
        val width = max(1, (cropWidth * scale).roundToInt())
        val height = max(1, (cropHeight * scale).roundToInt())
        val transform = orientationMatrix(orientation, decoded.width.toFloat(), decoded.height.toFloat()).apply {
            postTranslate(-cropLeft, -cropTop)
            postScale(width.toFloat() / cropWidth, height.toFloat() / cropHeight)
        }
        val photo = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(photo).apply {
                background?.let(::drawColor)
                drawBitmap(decoded, transform, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val encoded = target.outputStream().use { photo.compress(format, quality, it) }
            if (!encoded) throw IOException("Couldn't encode the photo")
        } finally {
            photo.recycle()
        }
    } finally {
        decoded.recycle()
    }
}

/** Whether [file] holds a picture that can be decoded: its bounds read to a positive size. For a staged pick (#10). */
internal fun isPicture(file: File): Boolean {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
    return bounds.outWidth > 0 && bounds.outHeight > 0
}

/** The largest power of two to divide [longSide] by that still leaves at least [maxSide]: BitmapFactory's `inSampleSize`. */
private fun sampleSize(longSide: Int, maxSide: Int): Int {
    var sample = 1
    while (longSide / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/**
 * The picture's EXIF orientation; normal when it has none, or its EXIF can't be read. Through androidx's ExifInterface:
 * the platform's reads a PNG's or WebP's EXIF only from API 30 (#92).
 */
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
