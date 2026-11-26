package com.bdaniel.loyscan.ui

import android.graphics.Bitmap
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
    val near = HashMap<Int, Boolean>() // a logo has few distinct colours
    return IntArray(pixels.size) { i ->
        val pixel = pixels[i]
        val rgb = pixel and 0xFFFFFF
        if (near.getOrPut(rgb) { distance(lab(rgb), brandLab) <= RECOLOUR_DISTANCE }) (pixel and ALPHA) or colour else pixel
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
private fun linear(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/**
 * This logo drawn with [treatment] (#240) on [brandColor], the colour behind it, which Recolour compares with: a new
 * ARGB_8888 bitmap, so a bitmap the image loader holds in its cache, or one that can't be changed, is never altered. As
 * is, or a tint or brand colour that isn't a colour, is the logo itself.
 */
fun Bitmap.drawnWith(treatment: LogoTreatment, brandColor: String): Bitmap {
    val style = treatment.style ?: return this
    val tint = treatment.tint?.toBrandColorOrNull()?.toArgb() ?: return this
    val brand = if (style == LogoStyle.RECOLOUR) brandColor.toBrandColorOrNull()?.toArgb() ?: return this else 0
    val pixels = IntArray(width * height)
    val argb = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    argb.getPixels(pixels, 0, width, 0, 0, width, height)
    val drawn = when (style) {
        LogoStyle.SINGLE_COLOUR -> inSingleColour(pixels, tint)
        LogoStyle.RECOLOUR -> recoloured(pixels, brand, tint)
    }
    return Bitmap.createBitmap(drawn, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * [drawnWith] for the image loader: the logo on a tile, in the card screen's band and in a store's badge (#240). Coil
 * caches the drawn logo under [cacheKey]: a Recoloured one's names the brand colour, so a new colour draws it again.
 */
class LogoTreatmentTransformation(private val treatment: LogoTreatment, private val brandColor: String) : Transformation {
    override val cacheKey: String = "logoTreatment(${treatment.style},${treatment.tint}" +
        (if (treatment.style == LogoStyle.RECOLOUR) ",$brandColor)" else ")")

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input.drawnWith(treatment, brandColor)
}
