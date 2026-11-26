package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.ui.OUTLINE_SHARE
import com.bdaniel.loyscan.ui.drawnWith
import com.bdaniel.loyscan.ui.outlineColour
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
 * The pictures the widget draws, as bitmaps, by file name: the front photos of the cards that show theirs on the widget
 * (#185), and the stores' logos (#181) of the other tiles and of those whose photo can't be read, each drawn with its
 * store's treatment (#240; a changed one is drawn again). A logo is scaled down, never up, to fit [logoBox] whole (the logo box at the
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

    /**
     * How a picture is drawn: a photo As is, a logo with its treatment (#240) on its store's [brandColor]. Two are drawn
     * alike when their [key]s are equal: the brand colour counts only for Recolour, which compares with it, and through
     * the Outline's colour, which follows it, so a new colour redraws only a logo it changes (review of PR #247).
     */
    private data class DrawnAs(val treatment: LogoTreatment, val brandColor: String = "") {
        val key: Any = Triple(
            treatment,
            brandColor.takeIf { treatment.style == LogoStyle.RECOLOUR },
            outlineColour(treatment, brandColor),
        )
    }

    private data class Crop(val offset: IntOffset, val size: IntSize)

    private val mutex = Mutex()

    /** Each picture's fitted size, or null for one that can't be read. */
    private var fitted: Map<String, Fitted?> = emptyMap()

    /** The bitmaps last handed out, and how each was drawn (#240; a photo As is). */
    private var shown: Map<String, Bitmap> = emptyMap()
    private var shownAs: Map<String, Any> = emptyMap()

    /** The size each bitmap last handed out was drawn for: an outlined one's ring makes its own size differ (#240). */
    private var shownFor: Map<String, IntSize> = emptyMap()

    /**
     * The bitmap of the picture each of [tiles] draws, by its name: its photo, else its store's logo. A tile's logo is
     * read only when its photo can't be (review of #185): it would never be drawn, and would take budget from the
     * pictures that are. Only the pictures that could be read.
     */
    suspend fun forTiles(tiles: List<WidgetTile>): Map<String, Bitmap> = mutex.withLock { drawnBy(tiles) }

    private suspend fun drawnBy(tiles: List<WidgetTile>): Map<String, Bitmap> {
        val known = fitted
        val next = mutableMapOf<String, Fitted?>()
        suspend fun fitted(name: String, photo: Boolean): Fitted? = when (name) {
            in next -> next[name]
            in known -> known[name]
            else -> withContext(ioDispatcher) { fit(name, photo) }
        }.also { next[name] = it }

        val before = shown
        val beforeAs = shownAs
        val beforeFor = shownFor
        // A picture whose header reads but whose pixels don't is found only when decoded: it is remembered as
        // unreadable and the tiles are worked out again, so its tile falls back to its logo. Each pass marks at least
        // one more picture unreadable, so this ends.
        while (true) {
            val drawn = linkedMapOf<String, Fitted>()
            val drawnAs = mutableMapOf<String, DrawnAs>()
            for (tile in tiles) {
                val photo = tile.photo?.let { name -> fitted(name, photo = true)?.let { name to it } }
                val logo = if (photo == null) tile.logo?.let { name -> fitted(name, photo = false)?.let { name to it } } else null
                photo?.let { (name, fit) -> drawn[name] = fit; drawnAs[name] = DrawnAs(LogoTreatment.AS_IS) }
                // A logo's file is its store's alone (a new logo is a new name), so one name is drawn one way, on its
                // store's colour, which Recolour compares with (#240).
                logo?.let { (name, fit) -> drawn[name] = fit; drawnAs[name] = DrawnAs(tile.logoTreatment, tile.color) }
            }
            val scale = widgetLogoScale(drawn.values.map { it.size }, budgetBytes)
            val decoded = drawn.mapValues { (name, fit) ->
                val target = if (scale >= 1f) fit.size else fit.size.scaledBy(scale)
                val drawnWith = drawnAs.getValue(name)
                before[name]?.takeIf { beforeFor[name] == target && beforeAs[name] == drawnWith.key }
                    ?: withContext(ioDispatcher) { drawn(name, fit.crop, target, drawnWith) }
            }
            val failed = decoded.filterValues { it == null }.keys
            failed.forEach { next[it] = null }
            if (failed.isEmpty()) {
                fitted = next
                shown = decoded.mapValues { (_, bitmap) -> checkNotNull(bitmap) }
                shownAs = drawnAs.filterKeys { it in shown }.mapValues { (_, drawnWith) -> drawnWith.key }
                shownFor = drawn.filterKeys { it in shown }.mapValues { (_, fit) -> if (scale >= 1f) fit.size else fit.size.scaledBy(scale) }
                return shown
            }
        }
    }

    /**
     * The picture drawn as [drawnWith] at [target]: an outlined logo is decoded smaller, at [target] ÷ (1 + 2 ×
     * [OUTLINE_SHARE]), so with its ring (#240) it still fits its box and the budget.
     */
    private fun drawn(name: String, crop: Crop?, target: IntSize, drawnWith: DrawnAs): Bitmap? {
        val size = if (outlineColour(drawnWith.treatment, drawnWith.brandColor) == null) target else target.insideOutline()
        return decode(name, crop, size)?.drawnWith(drawnWith.treatment, drawnWith.brandColor)
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

/** The size a logo is decoded at so that with its Outline's ring it is about this size, at most a pixel off (#240). */
private fun IntSize.insideOutline(): IntSize =
    IntSize(max(1, (width / (1 + 2 * OUTLINE_SHARE)).toInt()), max(1, (height / (1 + 2 * OUTLINE_SHARE)).toInt()))

private fun IntSize.scaledBy(scale: Float): IntSize = IntSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
