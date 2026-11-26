package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The most memory the widget's pictures take together (#181, photos since #185). The widget's bitmaps travel to the
 * home screen in one update, which Android refuses past a limit (1.5 × the screen's pixels × 4 bytes, about 15 MB on a
 * 1080 × 2400 phone, and a binder transaction's 1 MB where a bitmap isn't shared memory); 4 MB stays well under the
 * first.
 */
const val WIDGET_LOGO_BUDGET_BYTES: Long = 4L * 1024 * 1024

/**
 * The pictures the widget draws, as bitmaps, by file name: the stores' logos (#181) and the front photos of the cards
 * that show theirs on the widget (#185). A logo is scaled down, never up, to fit [logoBox] whole (the logo box at the
 * screen's density, [widgetLogoBox]). A photo is cut around its middle to [photoBox]'s shape and scaled down, never
 * up, to it ([widgetPhotoBox]): the tile crops it to fill anyway, and a photo kept whole ("use whole photo") may have
 * any shape, so this keeps every photo within its tile's pixels. When the pictures shown together would take more than
 * [budgetBytes], all are scaled down alike to fit it. A picture is decoded straight to the size it is shown at, and
 * only the bitmaps last handed out are kept, so this never holds more than the budget; a picture is decoded again only
 * when that size changes (another picture came or went past the budget). A picture's file is read by name, which a new
 * logo or photo never reuses. One whose file is missing, isn't a picture or whose name isn't a photo's is left out, so
 * its tiles show the logo or the initials; that is remembered too, until it is no longer asked for. [file] is
 * `FileCardPhotos.file`.
 */
class WidgetPictures(
    private val file: (String) -> File,
    private val logoBox: IntSize,
    private val photoBox: IntSize,
    private val budgetBytes: Long = WIDGET_LOGO_BUDGET_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The part of a picture shown, [crop] (its whole when null), and its size fitted to its box. */
    private data class Fitted(val crop: Crop?, val size: IntSize)

    private data class Crop(val offset: IntOffset, val size: IntSize)

    private val mutex = Mutex()

    /** Each picture's fitted size, or null for one that can't be read. */
    private var fitted: Map<String, Fitted?> = emptyMap()

    /** The bitmaps last handed out. */
    private var shown: Map<String, Bitmap> = emptyMap()

    /** The bitmap of each logo and photo [tiles] show, by its name; only the pictures that could be read. */
    suspend fun forTiles(tiles: List<WidgetTile>): Map<String, Bitmap> = mutex.withLock {
        val isPhoto = buildMap {
            tiles.forEach { tile ->
                tile.logo?.let { putIfAbsent(it, false) }
                tile.photo?.let { put(it, true) }
            }
        }
        val known = fitted
        fitted = isPhoto.mapValues { (name, photo) ->
            if (name in known) known[name] else withContext(ioDispatcher) { fit(name, photo) }
        }
        val readable = buildMap { fitted.forEach { (name, fit) -> if (fit != null) put(name, fit) } }
        val scale = widgetLogoScale(readable.values.map { it.size }, budgetBytes)
        val before = shown
        shown = buildMap {
            readable.forEach { (name, fit) ->
                val target = if (scale >= 1f) fit.size else fit.size.scaledBy(scale)
                val bitmap = before[name]?.takeIf { it.width == target.width && it.height == target.height }
                    ?: withContext(ioDispatcher) { decode(name, fit.crop, target) }
                if (bitmap != null) put(name, bitmap)
            }
        }
        shown
    }

    /** What part of the picture is shown and its size in its box, read from its header only. */
    private fun fit(name: String, photo: Boolean): Fitted? = read(name) {
        val bounds = bounds(file(name))
        when {
            bounds.width <= 0 || bounds.height <= 0 -> null
            photo -> cropToShape(bounds, photoBox).let { crop -> Fitted(crop, fitWithin(crop.size, photoBox)) }
            else -> Fitted(crop = null, size = fitWithin(bounds, logoBox))
        }
    }

    /** The picture's [crop] (all of it when null) decoded at [size]: sampled down by powers of two, then scaled. */
    private fun decode(name: String, crop: Crop?, size: IntSize): Bitmap? = read(name) {
        val picture = file(name)
        val sourceWidth = crop?.size?.width ?: bounds(picture).width
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(sourceWidth, size.width) }
        // From a stream it closes: on Windows a file BitmapFactory.decodeFile had open couldn't be deleted (#88).
        val decoded = picture.inputStream().use { BitmapFactory.decodeStream(it, null, options) } ?: return@read null
        val part = crop?.let { decoded.cut(it, options.inSampleSize) } ?: decoded
        part.resizedTo(size)
    }

    private fun bounds(picture: File): IntSize {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        picture.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        return IntSize(bounds.outWidth, bounds.outHeight)
    }

    /** [block], or null when the picture can't be read: a missing file, or a name that isn't a photo's. */
    private fun <T> read(name: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Timber.w(e, "Couldn't read the picture $name for the widget")
        null
    }

    /** [crop], given in the picture's pixels, out of this bitmap decoded at 1/[sample] of them. */
    private fun Bitmap.cut(crop: Crop, sample: Int): Bitmap {
        val x = (crop.offset.x / sample).coerceIn(0, width - 1)
        val y = (crop.offset.y / sample).coerceIn(0, height - 1)
        val w = max(1, crop.size.width / sample).coerceAtMost(width - x)
        val h = max(1, crop.size.height / sample).coerceAtMost(height - y)
        return Bitmap.createBitmap(this, x, y, w, h)
    }

    /** The largest part of a picture of [size] at [box]'s shape, around its middle (#185). */
    private fun cropToShape(size: IntSize, box: IntSize): Crop {
        val boxRatio = box.width.toFloat() / box.height
        val cropSize = if (size.width.toFloat() / size.height > boxRatio) {
            IntSize(max(1, (size.height * boxRatio).toInt()), size.height)
        } else {
            IntSize(size.width, max(1, (size.width / boxRatio).toInt()))
        }
        return Crop(IntOffset((size.width - cropSize.width) / 2, (size.height - cropSize.height) / 2), cropSize)
    }
}

/** [size] scaled down, never up, to fit [box] whole, keeping its shape; at least one pixel each way. */
fun fitWithin(size: IntSize, box: IntSize): IntSize {
    val scale = min(1f, min(box.width / size.width.toFloat(), box.height / size.height.toFloat()))
    return IntSize(max(1, (size.width * scale).toInt()), max(1, (size.height * scale).toInt()))
}

/** How much to scale every side of bitmaps of [sizes] (ARGB, 4 bytes a pixel) so together they fit [budgetBytes]: at most 1. */
fun widgetLogoScale(sizes: List<IntSize>, budgetBytes: Long): Float {
    val bytes = sizes.sumOf { it.width.toLong() * it.height * 4 }
    return if (bytes <= budgetBytes) 1f else sqrt(budgetBytes.toDouble() / bytes).toFloat()
}

/** The largest power of two that decodes a [width]-pixel picture no narrower than [target] pixels. */
private fun sampleSize(width: Int, target: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= target) sample *= 2
    return sample
}

private fun Bitmap.resizedTo(size: IntSize): Bitmap =
    if (width == size.width && height == size.height) this else Bitmap.createScaledBitmap(this, size.width, size.height, true)

private fun IntSize.scaledBy(scale: Float): IntSize = IntSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
