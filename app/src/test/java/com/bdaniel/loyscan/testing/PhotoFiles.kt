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
