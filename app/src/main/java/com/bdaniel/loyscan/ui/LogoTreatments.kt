package com.bdaniel.loyscan.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import coil.size.Size
import coil.transform.Transformation
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A logo's ARGB [pixels] in Single colour (#240): every pixel in [tint]'s colour, opaque as far as its brightness says,
 * so detail inside the logo (a letter cut out of a disc) stays visible. Which end is solid follows the logo (the owner's
 * choice, 2026-11-26): on a mostly dark one its darkest pixel is solid and white clear; on a mostly light one its
 * lightest pixel solid and black clear. "Mostly" is by area: the mean CIE lightness L* of its pixels, each weighed by its
 * opacity, against mid-grey; linear luminance would call a logo a third white light. Scaling to the logo's own darkest
 * or lightest pixel makes a logo in one colour solid. A pixel's own transparency is kept (soft edges). Brightness is
 * the WCAG relative luminance, as [contrastingTextColor] uses.
 */
fun inSingleColour(pixels: IntArray, tint: Int): IntArray {
    val luminance = DoubleArray(pixels.size) { relativeLuminance(pixels[it]) }
    var weight = 0.0
    var weightedLightness = 0.0
    var darkest = 1.0
    var lightest = 0.0
    pixels.forEachIndexed { i, pixel ->
        val alpha = pixel ushr 24
        if (alpha > 0) {
            weight += alpha
            weightedLightness += alpha * lightness(luminance[i])
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

/** CIE L* of mid-grey: a logo lighter than it on average, by area, is mostly light. */
private const val MID_GREY_LIGHTNESS = 50.0

/** The CIE lightness L*, 0 to 100, of a WCAG relative [luminance]: even steps as the eye sees them. */
private fun lightness(luminance: Double): Double =
    if (luminance > 216.0 / 24389) 116 * luminance.pow(1.0 / 3) - 16 else luminance * 24389 / 27

/** The WCAG relative luminance of an ARGB [pixel]'s colour, 0 for black to 1 for white. */
private fun relativeLuminance(pixel: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(pixel shr 16 and 0xFF) + 0.7152 * linear(pixel shr 8 and 0xFF) + 0.0722 * linear(pixel and 0xFF)
}

/**
 * This logo drawn with [treatment] (#240): a new ARGB_8888 bitmap, so a bitmap the image loader holds in its cache, or
 * one that can't be changed, is never altered. As is, or a tint that isn't a colour, is the logo itself.
 */
fun Bitmap.drawnWith(treatment: LogoTreatment): Bitmap {
    if (treatment.style != LogoStyle.SINGLE_COLOUR) return this
    val tint = treatment.tint?.toBrandColorOrNull()?.toArgb() ?: return this
    val pixels = IntArray(width * height)
    val argb = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
    argb.getPixels(pixels, 0, width, 0, 0, width, height)
    return Bitmap.createBitmap(inSingleColour(pixels, tint), width, height, Bitmap.Config.ARGB_8888)
}

/** [drawnWith] for the image loader: the logo on a tile, in the card screen's band and in a store's badge (#240). */
class LogoTreatmentTransformation(private val treatment: LogoTreatment) : Transformation {
    override val cacheKey: String = "logoTreatment(${treatment.style},${treatment.tint})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input.drawnWith(treatment)
}
