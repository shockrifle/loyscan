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
 * The store logos the widget draws (#181), as bitmaps: each decoded once (cached by its name, which a new logo never
 * reuses) and scaled down, never up, to fit [box] (the logo box at the screen's density, [widgetLogoBox]). When the
 * logos shown together would take more than [budgetBytes], all are scaled down alike to fit it. A logo whose file is
 * missing, isn't a picture or whose name isn't a photo's is left out, so its tiles show the initials; that is cached
 * too, until the logo is no longer asked for. [file] is `FileCardPhotos.file`.
 */
class WidgetLogos(
    private val file: (String) -> File,
    private val box: IntSize,
    private val budgetBytes: Long = WIDGET_LOGO_BUDGET_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var decoded: Map<String, Bitmap?> = emptyMap()

    /** The bitmap of each logo [tiles] show, by the logo's name; only the logos that could be read. */
    suspend fun forTiles(tiles: List<WidgetTile>): Map<String, Bitmap> = mutex.withLock {
        val names = tiles.mapNotNullTo(linkedSetOf()) { it.logo }
        val known = decoded
        decoded = names.associateWith { name -> if (name in known) known[name] else withContext(ioDispatcher) { decode(name) } }
        val shown = buildMap { decoded.forEach { (name, bitmap) -> if (bitmap != null) put(name, bitmap) } }
        val scale = widgetLogoScale(shown.values.map { IntSize(it.width, it.height) }, budgetBytes)
        if (scale >= 1f) shown else shown.mapValues { (_, bitmap) -> bitmap.scaledBy(scale) }
    }

    private fun decode(name: String): Bitmap? = try {
        val logo = file(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // From a stream it closes: on Windows a file BitmapFactory.decodeFile had open couldn't be deleted (#88).
        logo.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val target = fitWithin(IntSize(bounds.outWidth, bounds.outHeight), box)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, target.width) }
            logo.inputStream().use { BitmapFactory.decodeStream(it, null, options) }?.let { it.resizedTo(target) }
        }
    } catch (e: Exception) { // a missing file, or a name that isn't a photo's: the initials show
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

private fun Bitmap.scaledBy(scale: Float): Bitmap =
    resizedTo(IntSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt())))
