package com.bdaniel.loyscan.testing

import java.io.File
import java.util.Base64

/** Writes a real 1×1 PNG to this file, creating its directory, so the app's image loader can decode it. */
fun File.writeOnePixelPng(): File = apply {
    parentFile!!.mkdirs()
    writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG))
}

/** A 1×1 PNG. */
private const val ONE_PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

/** Writes a real 2×1 PNG to this file, creating its directory: a picture twice as wide as tall, like a wordmark (#151). */
fun File.writeWidePng(): File = apply {
    parentFile!!.mkdirs()
    writeBytes(Base64.getDecoder().decode(TWO_BY_ONE_PNG))
}

/** A 2×1 PNG. */
private const val TWO_BY_ONE_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAIAAAB7QOjdAAAADUlEQVR4nGP4z8AARAAI/gH/xp559wAAAABJRU5ErkJggg=="

/**
 * Writes a real [size]×[size] PNG in one [argb] colour to this file, creating its directory: a logo that blends into a
 * brand colour of the same (#240). Encoded with `Bitmap.compress`, so the test needs `@GraphicsMode(NATIVE)`.
 */
fun File.writeSolidPng(argb: Int, size: Int = 8): File = apply {
    parentFile!!.mkdirs()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(argb)
    outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

/**
 * Writes a real 100 × 100 PNG to this file, creating its directory: a clear field with a 50 × 50 square in one [argb]
 * colour in its middle, so a logo's Outline (#240) has room around its shape. Needs `@GraphicsMode(NATIVE)`.
 */
fun File.writeSquareOnClearPng(argb: Int): File = apply {
    parentFile!!.mkdirs()
    val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    for (y in 25 until 75) for (x in 25 until 75) bitmap.setPixel(x, y, argb)
    outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
}

/**
 * The pixel on the ring of an outlined [writeSquareOnClearPng] logo (#240), in this capture of the box it is fitted
 * into whole: the logo with its ring is a square of the box's shorter side, s, in its middle; the ring (4% of the
 * square's side, about 0.04 s) runs just outside the 50% square, so 0.25 s down from its top, in the middle across, is
 * on it whatever size the logo was decoded at.
 */
fun androidx.compose.ui.graphics.PixelMap.ringPixel(): androidx.compose.ui.graphics.Color {
    val side = minOf(width, height)
    return this[width / 2, (height - side) / 2 + side / 4]
}
