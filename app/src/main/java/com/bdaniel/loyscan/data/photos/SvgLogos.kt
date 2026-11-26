package com.bdaniel.loyscan.data.photos

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** The largest SVG file, in bytes, drawn as a store's logo (#211): a logo is far smaller, even with a picture inside. */
internal const val MAX_SVG_BYTES = 5L * 1024 * 1024

/** The size a browser gives an SVG that states neither its size nor a viewBox (#211). */
private const val DEFAULT_SVG_WIDTH = 300f
private const val DEFAULT_SVG_HEIGHT = 150f

/**
 * Draws the SVG in [source] into [target] as a store's logo (#211): a PNG on a transparent background, [MAX_LOGO_SIDE]
 * pixels on its long side, which [encodeStoreLogo] then crops as it crops any logo. The shape comes from the SVG's width
 * and height, else from its viewBox, else it is a browser's 300×150. Nothing outside the file is read: no
 * `SVGExternalFileResolver` is registered, so AndroidSVG skips an `<image>` or a font that isn't embedded, and its parser
 * never resolves an external entity. Throws an [IOException] for a file over [MAX_SVG_BYTES], one that isn't an SVG, or
 * any failure to draw it, so the caller shows that the picture couldn't be used.
 */
internal fun drawSvgLogo(source: File, target: File) {
    if (source.length() > MAX_SVG_BYTES) throw IOException("The SVG is too large: ${source.length()} bytes")
    val text = source.readBytes().unpacked()
    checkEntities(text)
    // A logo is drawn in one go: AndroidSVG can't be stopped midway, so a hostile file that is slow to draw (deep <use>
    // chains, say) holds the IO thread until it ends. Accepted: the user picks or shares the file (PR #242 review).
    try {
        val svg = text.inputStream().use(SVG::getFromInputStream)
        val (width, height) = svg.shape()
        val scale = MAX_LOGO_SIDE / max(width, height)
        val drawnWidth = max(1, (width * scale).roundToInt())
        val drawnHeight = max(1, (height * scale).roundToInt())
        // Scaled by giving its user units a viewBox, if it has none, and the logo's pixels as its size.
        if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, width, height)
        svg.setDocumentWidth(drawnWidth.toFloat())
        svg.setDocumentHeight(drawnHeight.toFloat())
        val logo = Bitmap.createBitmap(drawnWidth, drawnHeight, Bitmap.Config.ARGB_8888)
        try {
            svg.renderToCanvas(Canvas(logo))
            val encoded = target.outputStream().use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!encoded) throw IOException("Couldn't encode the logo")
        } finally {
            logo.recycle()
        }
    } catch (e: IOException) {
        throw e
    } catch (e: Throwable) { // a broken or hostile SVG can fail in any way, a StackOverflowError too: none may crash the app
        throw IOException("Couldn't draw the SVG", e)
    }
}

/**
 * The SVG's text: a gzipped one (an `.svgz`) unpacked here, at most [MAX_SVG_BYTES], so [checkEntities] reads what
 * AndroidSVG parses. AndroidSVG would unpack it itself, past that check (PR #242 review).
 */
private fun ByteArray.unpacked(): ByteArray {
    if (!isGzip()) return this
    val unpacked = ByteArrayOutputStream()
    GZIPInputStream(inputStream()).use { gzip ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = gzip.read(buffer)
            if (read < 0) break
            unpacked.write(buffer, 0, read)
            if (unpacked.size() > MAX_SVG_BYTES) throw IOException("The SVG is too large unpacked")
        }
    }
    // Packed twice, AndroidSVG would unpack the inner layer past the checks; no real logo is.
    return unpacked.toByteArray().also { if (it.isGzip()) throw IOException("The SVG is packed twice") }
}

private fun ByteArray.isGzip() = size >= 2 && this[0] == GZIP_MAGIC_0 && this[1] == GZIP_MAGIC_1

private const val GZIP_MAGIC_0 = 0x1f.toByte()
private const val GZIP_MAGIC_1 = 0x8b.toByte()

/**
 * Refuses an SVG whose entities AndroidSVG shouldn't expand (#211). AndroidSVG expands entities a DOCTYPE declares, and
 * some vector editors export an SVG with plain ones (`<!ENTITY ns_svg "http://…">`), so they stay on. An entity whose
 * value names another entity is the shape of an expansion bomb ("billion laughs"), which only the device's XML parser
 * would otherwise stop; one from another file (`SYSTEM`, `PUBLIC`) or a parameter entity (`%`) has no use in a logo. So
 * every declaration must be a name and a quoted value with no `&` or `%`. A file that isn't 8-bit text (a NUL byte, as
 * in UTF-16) is refused too, so its declarations can't escape this check.
 */
private fun checkEntities(svg: ByteArray) {
    if (svg.any { it == 0.toByte() }) throw IOException("The SVG isn't UTF-8 or ASCII text")
    val text = svg.toString(Charsets.UTF_8)
    val declared = ANY_ENTITY.findAll(text).count()
    if (declared != PLAIN_ENTITY.findAll(text).count()) throw IOException("The SVG declares an entity it can't use")
}

private val ANY_ENTITY = Regex("<!ENTITY")
private val PLAIN_ENTITY = Regex("""<!ENTITY\s+[^\s%"'>]+\s+("[^"&%]*"|'[^'&%]*')\s*>""")

/**
 * The SVG's width and height, in its own units, as [drawSvgLogo] describes. AndroidSVG reads a width or height of zero, or
 * in percent, as not stated. Throws for a shape that can't be drawn.
 */
private fun SVG.shape(): Pair<Float, Float> {
    val viewBox = documentViewBox
    val (width, height) = when {
        documentWidth > 0f && documentHeight > 0f -> documentWidth to documentHeight
        viewBox != null -> viewBox.width() to viewBox.height()
        else -> DEFAULT_SVG_WIDTH to DEFAULT_SVG_HEIGHT
    }
    if (!(width > 0f && height > 0f && width.isFinite() && height.isFinite())) throw IOException("The SVG has no size: $width × $height")
    return width to height
}
