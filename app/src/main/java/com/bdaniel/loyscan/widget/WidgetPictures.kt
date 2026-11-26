package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * The most memory the widget's logos take together (#181). The widget's bitmaps travel to the home screen in one
 * update, which Android refuses past a limit (1.5 × the screen's pixels × 4 bytes, about 15 MB on a 1080 × 2400 phone,
 * and a binder transaction's 1 MB where a bitmap isn't shared memory); 4 MB stays well under the first.
 */
const val WIDGET_LOGO_BUDGET_BYTES: Long = 4L * 1024 * 1024

/**
 * The store logos the widget draws (#181), as bitmaps, each scaled down, never up, to fit [box] (the logo box at the
 * screen's density, [widgetLogoBox]); when the logos shown together would take more than [budgetBytes], all are scaled
 * down alike to fit it. A logo is decoded straight to the size it is shown at, and only the bitmaps last handed out are
 * kept, so this never holds more than the budget; a logo is decoded again only when that size changes (another logo
 * came or went past the budget). A logo's file is read by name, which a new logo never reuses. One whose file is
 * missing, isn't a picture or whose name isn't a photo's is left out, so its tiles show the initials; that is
 * remembered too, until the logo is no longer asked for. [file] is `FileCardPhotos.file`.
 */
class WidgetPictures(
    private val file: (String) -> File,
    private val box: IntSize,
    private val budgetBytes: Long = WIDGET_LOGO_BUDGET_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    /** Each logo's size fitted to [box], or null for one that can't be read. */
    private var fitted: Map<String, IntSize?> = emptyMap()

    /** The bitmaps last handed out. */
    private var shown: Map<String, Bitmap> = emptyMap()

    /** The bitmap of each logo [tiles] show, by the logo's name; only the logos that could be read. */
    suspend fun forTiles(tiles: List<WidgetTile>): Map<String, Bitmap> = mutex.withLock {
        val names = tiles.mapNotNullTo(linkedSetOf()) { it.logo }
        val known = fitted
        fitted = names.associateWith { name -> if (name in known) known[name] else withContext(ioDispatcher) { fittedSize(name) } }
        val sizes = buildMap { fitted.forEach { (name, size) -> if (size != null) put(name, size) } }
        val scale = widgetLogoScale(sizes.values.toList(), budgetBytes)
        val before = shown
        shown = buildMap {
            sizes.forEach { (name, size) ->
                val target = if (scale >= 1f) size else size.scaledBy(scale)
                val bitmap = before[name]?.takeIf { it.width == target.width && it.height == target.height }
                    ?: withContext(ioDispatcher) { decode(name, target) }
                if (bitmap != null) put(name, bitmap)
            }
        }
        shown
    }

    /** The logo's size fitted to [box], read from its header only. */
    private fun fittedSize(name: String): IntSize? = read(name) {
        val bounds = bounds(file(name))
        if (bounds.width <= 0 || bounds.height <= 0) null else fitWithin(bounds, box)
    }

    /** The logo decoded at [size]: sampled down by powers of two, then scaled. */
    private fun decode(name: String, size: IntSize): Bitmap? = read(name) {
        val logo = file(name)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds(logo).width, size.width) }
        // From a stream it closes: on Windows a file BitmapFactory.decodeFile had open couldn't be deleted (#88).
        logo.inputStream().use { BitmapFactory.decodeStream(it, null, options) }?.resizedTo(size)
    }

    private fun bounds(logo: File): IntSize {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        logo.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        return IntSize(bounds.outWidth, bounds.outHeight)
    }

    /** [block], or null when the logo can't be read: a missing file, or a name that isn't a photo's. */
    private fun <T> read(name: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Timber.w(e, "Couldn't read the store logo $name for the widget")
        null
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
