package com.bdaniel.loyscan.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil.size.Size
import coil.transform.Transformation
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A logo's ARGB [pixels] in Single colour (#240): every pixel in [tint]'s colour, opaque as far as its brightness says,
 * so detail inside the logo (a letter cut out of a disc) stays visible. Which end is solid follows the logo (the owner's
 * choice, 2026-11-26): on a mostly dark one its darkest pixel is solid and white clear; on a mostly light one its
 * lightest pixel solid and black clear. "Mostly" is by area: the mean CIE lightness L* of its pixels, each weighed by its
 * opacity, against mid-grey; linear luminance would call a logo a third white light. Scaling to the logo's own darkest
 * or lightest pixel (among those at least half opaque) makes a logo in one colour solid. A pixel's own transparency is kept (soft edges). Brightness is
 * the WCAG relative luminance, as [contrastingTextColor] uses.
 */
fun inSingleColour(pixels: IntArray, tint: Int): IntArray {
    val luminance = DoubleArray(pixels.size) { relativeLuminance(pixels[it]) }
    var weight = 0.0
    var weightedLightness = 0.0
    var darkest = 1.0
    var lightest = 0.0
    // The extremes come from the logo's solid pixels: a nearly clear edge pixel's colour is noise once decoded, and would
    // keep a one-colour logo from coming out solid (review of PR #244). Only a logo with none at all uses every pixel.
    val extremesFrom = if (pixels.any { (it ushr 24) >= HALF_OPAQUE }) HALF_OPAQUE else 1
    pixels.forEachIndexed { i, pixel ->
        val alpha = pixel ushr 24
        if (alpha > 0) {
            weight += alpha
            weightedLightness += alpha * lightness(luminance[i])
        }
        if (alpha >= extremesFrom) {
            darkest = minOf(darkest, luminance[i])
            lightest = maxOf(lightest, luminance[i])
        }
    }
    val colour = tint and 0xFFFFFF
    if (weight == 0.0) return IntArray(pixels.size) { colour }
    val light = weightedLightness / weight > MID_GREY_LIGHTNESS
    return IntArray(pixels.size) { i ->
        val opacity = when {
            light -> if (lightest > 0.0) luminance[i] / lightest else 1.0
            else -> if (darkest < 1.0) (1.0 - luminance[i]) / (1.0 - darkest) else 1.0
        }
        val alpha = ((pixels[i] ushr 24) * opacity.coerceIn(0.0, 1.0)).roundToInt()
        (alpha shl 24) or colour
    }
}

/**
 * A logo's ARGB [pixels] Recoloured (#240): every pixel within [RECOLOUR_DISTANCE] of the [brand] colour, the CIE76
 * distance in CIELAB, in [tint]'s colour at its own opacity (soft edges kept); the other colours left alone. Only a
 * pixel's colour is compared, not its opacity.
 */
fun recoloured(pixels: IntArray, brand: Int, tint: Int): IntArray {
    val brandLab = lab(brand)
    val colour = tint and 0xFFFFFF
    // Each pixel on its own: a logo cropped from a photo has as many colours as pixels (review of PR #247).
    return IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        if (distance(lab(pixel), brandLab) <= RECOLOUR_DISTANCE) (pixel and ALPHA) or colour else pixel
    }
}

/** How near, as the CIE76 ΔE in CIELAB, a colour is to the brand colour to be Recoloured: its shades, not another hue. */
private const val RECOLOUR_DISTANCE = 25.0

private const val ALPHA = 0xFF shl 24

/** CIELAB (D65 white) of an ARGB [pixel]'s colour: L*, a*, b*. */
private fun lab(pixel: Int): DoubleArray {
    val r = linear(pixel shr 16 and 0xFF)
    val g = linear(pixel shr 8 and 0xFF)
    val b = linear(pixel and 0xFF)
    fun f(t: Double) = if (t > 216.0 / 24389) t.pow(1.0 / 3) else (24389.0 / 27 * t + 16) / 116
    val fx = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
    val fy = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
    val fz = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

private fun distance(a: DoubleArray, b: DoubleArray): Double =
    sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

/** At least half opaque: a pixel of the logo itself, not of its soft edge. */
private const val HALF_OPAQUE = 128

/** CIE L* of mid-grey: a logo lighter than it on average, by area, is mostly light. */
private const val MID_GREY_LIGHTNESS = 50.0

/** The CIE lightness L*, 0 to 100, of a WCAG relative [luminance]: even steps as the eye sees them. */
private fun lightness(luminance: Double): Double =
    if (luminance > 216.0 / 24389) 116 * luminance.pow(1.0 / 3) - 16 else luminance * 24389 / 27

/** The WCAG relative luminance of an ARGB [pixel]'s colour, 0 for black to 1 for white. */
private fun relativeLuminance(pixel: Int): Double = 0.2126 * linear(pixel shr 16 and 0xFF) + 0.7152 * linear(pixel shr 8 and 0xFF) + 0.0722 * linear(pixel and 0xFF)

/** An sRGB [channel], 0 to 255, as linear light, 0 to 1. */
private fun linear(channel: Int): Double = LINEAR[channel]

/** [linear] worked out once for each of the 256 values: every pixel of a logo needs three. */
private val LINEAR = DoubleArray(256) { channel ->
    val c = channel / 255.0
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/**
 * This logo drawn with [treatment] (#240) on [brandColor], the colour behind it, which Recolour compares with and the
 * Outline's colour follows: a new ARGB_8888 bitmap, so a bitmap the image loader holds in its cache, or one that can't
 * be changed, is never altered. A style whose tint isn't a colour is left out, and so is an Outline, or Recolour, on a
 * brand colour that isn't one; with nothing left to draw, the logo itself. An Outline grows the bitmap by
 * [outlineRadius] on every side.
 */
fun Bitmap.drawnWith(treatment: LogoTreatment, brandColor: String): Bitmap {
    val brand = brandColor.toBrandColorOrNull()?.toArgb()
    val tint = treatment.tint?.toBrandColorOrNull()?.toArgb()
    val style = treatment.style?.takeIf { tint != null && (it != LogoStyle.RECOLOUR || brand != null) }
    val outline = outlineColour(treatment, brandColor)
    if (style == null && outline == null) return this
    var pixels = IntArray(width * height)
    val argb = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    argb.getPixels(pixels, 0, width, 0, 0, width, height)
    pixels = when (style) {
        LogoStyle.SINGLE_COLOUR -> inSingleColour(pixels, checkNotNull(tint))
        LogoStyle.RECOLOUR -> recoloured(pixels, checkNotNull(brand), checkNotNull(tint))
        null -> pixels
    }
    if (outline == null) return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val radius = outlineRadius(width, height)
    return Bitmap.createBitmap(
        outlined(pixels, width, height, radius, outline),
        width + 2 * radius,
        height + 2 * radius,
        Bitmap.Config.ARGB_8888,
    )
}

/**
 * The colour of the logo's Outline (#240), ARGB: white or black, whichever contrasts more with [brandColor]; null without
 * an Outline, or on a brand colour that isn't one.
 */
fun outlineColour(treatment: LogoTreatment, brandColor: String): Int? {
    if (!treatment.outline) return null
    val brand = brandColor.toBrandColorOrNull()?.toArgb() ?: return null
    return if (defaultLogoTint(brand) == BLACK_HEX) BLACK_ARGB else WHITE_ARGB
}

/**
 * [drawnWith] for the image loader: the logo on a tile, in the card screen's band and in a store's badge (#240). Coil
 * caches the drawn logo under [cacheKey]: a Recoloured one's names the brand colour and an outlined one's its Outline's
 * colour, so a new brand colour draws it again only when that changes how it looks.
 */
class LogoTreatmentTransformation(private val treatment: LogoTreatment, private val brandColor: String) : Transformation {
    override val cacheKey: String = "logoTreatment(${treatment.style},${treatment.tint}" +
        (if (treatment.style == LogoStyle.RECOLOUR) ",$brandColor" else "") +
        (outlineColour(treatment, brandColor)?.let { ",outline=${Integer.toHexString(it)}" } ?: "") + ")"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input.drawnWith(treatment, brandColor)
}

/**
 * How a new logo, its ARGB [pixels], is drawn on its [brand] colour when it is saved (#240): As is when it shows (it
 * doesn't [blendsIn]); else Recoloured in [defaultLogoTint] when that shows; else in Single colour, in the same colour.
 * A logo with nothing drawn is As is.
 */
fun preselectedTreatment(pixels: IntArray, brand: Int): LogoTreatment {
    if (!blendsIn(pixels, brand)) return LogoTreatment.AS_IS
    val tint = defaultLogoTint(brand)
    val tintArgb = if (tint == BLACK_HEX) BLACK_ARGB else WHITE_ARGB
    val style = if (blendsIn(recoloured(pixels, brand, tintArgb), brand)) LogoStyle.SINGLE_COLOUR else LogoStyle.RECOLOUR
    return LogoTreatment(style = style, tint = tint)
}

/**
 * Whether a logo, its ARGB [pixels], blends into the [brand] colour (#240): more than half of it, each pixel weighed by
 * its opacity, has less than [LEGIBLE_CONTRAST] with the brand colour, judged as it shows there, blended onto it.
 */
private fun blendsIn(pixels: IntArray, brand: Int): Boolean {
    val brandLuminance = relativeLuminance(brand)
    var drawn = 0L
    var faint = 0L
    for (pixel in pixels) {
        val alpha = pixel ushr 24
        if (alpha == 0) continue
        drawn += alpha
        if (contrast(relativeLuminance(pixel.over(brand)), brandLuminance) < LEGIBLE_CONTRAST) faint += alpha
    }
    return faint * 2 > drawn
}

/** WCAG's least contrast for large text and graphics. */
private const val LEGIBLE_CONTRAST = 3.0

/** The WCAG contrast ratio of two relative luminances, 1 to 21. */
private fun contrast(a: Double, b: Double): Double = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

/**
 * This ARGB pixel drawn over [background], source-over: as the screen shows it on an opaque background (an opaque
 * colour), or a logo over its Outline. Clear over clear is clear.
 */
private fun Int.over(background: Int): Int {
    val alpha = (this ushr 24) / 255.0
    val behind = (background ushr 24) / 255.0 * (1 - alpha)
    val total = alpha + behind
    if (total == 0.0) return 0
    fun channel(shift: Int) =
        (((this shr shift and 0xFF) * alpha + (background shr shift and 0xFF) * behind) / total).roundToInt()
    return ((total * 255).roundToInt() shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/**
 * White or black, `#RRGGBB`, whichever contrasts more with the [brand] colour, ARGB (#240): the colour Single colour and
 * Recolour start in.
 */
fun defaultLogoTint(brand: Int): String = if (Color(brand).contrastingTextColor() == Color.Black) BLACK_HEX else WHITE_HEX

private const val BLACK_HEX = "#000000"
private const val WHITE_HEX = "#FFFFFF"
private const val BLACK_ARGB = 0xFF000000.toInt()
private const val WHITE_ARGB = 0xFFFFFFFF.toInt()

/**
 * A logo's ARGB [pixels], [width] × [height], with an Outline (#240): a ring in [colour] [radius] pixels wide around its
 * shape (the pixels at least half opaque), the logo drawn over it, on a square grown by [radius] on every side so a logo
 * touching its crop's edge keeps its whole ring. The ring's opacity at a distance d from the shape is r + 0.5 − d,
 * clamped to 0..1: a soft edge one pixel wide. A hole in the logo narrower than the ring fills with it.
 */
fun outlined(pixels: IntArray, width: Int, height: Int, radius: Int, colour: Int): IntArray {
    val outWidth = width + 2 * radius
    val outHeight = height + 2 * radius
    val distances = DoubleArray(outWidth * outHeight) { i ->
        val x = i % outWidth - radius
        val y = i / outWidth - radius
        val inside = x in 0 until width && y in 0 until height && (pixels[y * width + x] ushr 24) >= HALF_OPAQUE
        if (inside) 0.0 else Double.POSITIVE_INFINITY
    }
    squaredDistances(distances, outWidth, outHeight)
    val rgb = colour and 0xFFFFFF
    return IntArray(outWidth * outHeight) { i ->
        val ring = ((radius + 0.5 - sqrt(distances[i])).coerceIn(0.0, 1.0) * 255).roundToInt()
        val x = i % outWidth - radius
        val y = i / outWidth - radius
        val logo = if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0
        logo.over((ring shl 24) or rgb)
    }
}

/** How wide a logo's Outline is (#240): [OUTLINE_SHARE] of its longer side, so it looks alike at every size; a pixel at least. */
fun outlineRadius(width: Int, height: Int): Int = maxOf(1, (OUTLINE_SHARE * maxOf(width, height)).roundToInt())

/** An Outline's width as a share of the logo's longer side (the owner's choice, 2026-11-26). */
const val OUTLINE_SHARE = 0.04

/**
 * Replaces each value of [grid], 0 inside the shape and infinite outside it, with the squared Euclidean distance to the
 * nearest 0: Felzenszwalb and Huttenlocher's distance transform, along the rows and then the columns, in linear time.
 */
private fun squaredDistances(grid: DoubleArray, width: Int, height: Int) {
    val line = DoubleArray(maxOf(width, height))
    val result = DoubleArray(line.size)
    val hull = IntArray(line.size)
    val bounds = DoubleArray(line.size + 1)
    for (y in 0 until height) {
        for (x in 0 until width) line[x] = grid[y * width + x]
        squaredDistances1d(line, width, result, hull, bounds)
        for (x in 0 until width) grid[y * width + x] = result[x]
    }
    for (x in 0 until width) {
        for (y in 0 until height) line[y] = grid[y * width + x]
        squaredDistances1d(line, height, result, hull, bounds)
        for (y in 0 until height) grid[y * width + x] = result[y]
    }
}

/** The one-dimensional pass of [squaredDistances] over the first [n] values of [f], into [result]: the lower envelope of parabolas. */
private fun squaredDistances1d(f: DoubleArray, n: Int, result: DoubleArray, hull: IntArray, bounds: DoubleArray) {
    var k = -1
    for (q in 0 until n) {
        if (f[q] == Double.POSITIVE_INFINITY) continue
        while (k >= 0) {
            val p = hull[k]
            val s = ((f[q] + q * q) - (f[p] + p * p)) / (2.0 * (q - p))
            if (s > bounds[k]) {
                k++
                hull[k] = q
                bounds[k] = s
                break
            }
            k--
        }
        if (k < 0) {
            k = 0
            hull[0] = q
            bounds[0] = Double.NEGATIVE_INFINITY
        }
        bounds[k + 1] = Double.POSITIVE_INFINITY
    }
    if (k < 0) {
        result.fill(Double.POSITIVE_INFINITY, 0, n)
        return
    }
    var j = 0
    for (q in 0 until n) {
        while (bounds[j + 1] < q) j++
        val p = hull[j]
        result[q] = (q - p).toDouble() * (q - p) + f[p]
    }
}
