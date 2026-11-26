package com.bdaniel.loyscan.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

/**
 * Card photos as files (#10): a picked picture is stored in the photo directory, and deleted from there. Native
 * graphics, so pictures are really decoded and encoded (#88).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileCardPhotosTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { File(temporaryFolder.root, "card_photos") }
    private val photos by lazy {
        FileCardPhotos(directory, ApplicationProvider.getApplicationContext<Context>().contentResolver)
    }

    // A stored photo is re-encoded through the crop path (stage then crop WHOLE), the way the form stores a pick; the
    // framed crop is covered by crop_storesOnlyTheFramedPartOfTheUprightPicture below.
    private suspend fun importWhole(picture: Uri) = photos.crop(photos.stage(picture), PhotoCrop.WHOLE)

    @Test
    fun stored_dropsTheLocationAndTheOtherExif() = runTest { // #88
        val picked = aPicture(40, 20).withExif(
            ExifInterface.TAG_GPS_LATITUDE to "47/1,29/1,0/1",
            ExifInterface.TAG_GPS_LATITUDE_REF to "N",
            ExifInterface.TAG_GPS_LONGITUDE to "19/1,2/1,0/1",
            ExifInterface.TAG_GPS_LONGITUDE_REF to "E",
            ExifInterface.TAG_MAKE to "Example",
            ExifInterface.TAG_DATETIME to "2026:05:05 05:05:05",
        )

        val imported = photos.file(importWhole(Uri.fromFile(picked)))

        assertThat(imported.shown()).isEqualTo(Shown(40, 20, Color.RED, Color.GREEN, Color.BLUE))
        val exif = ExifInterface(imported.path)
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME)).isNull()
    }

    @Test
    fun stored_scalesALargePictureDown_toItsLongSideLimit() = runTest { // #88
        val wide = importWhole(Uri.fromFile(aPicture(5000, 2500, "wide.jpg")))
        val tall = importWhole(Uri.fromFile(aPicture(1000, 3000, "tall.jpg")))

        assertThat(photos.file(wide).size()).isEqualTo(MAX_PHOTO_SIDE to MAX_PHOTO_SIDE / 2)
        assertThat(photos.file(tall).size()).isEqualTo(683 to MAX_PHOTO_SIDE)
    }

    @Test
    fun stored_keepsTheSizeOfASmallerPicture() = runTest { // #88: not enlarged
        val imported = importWhole(Uri.fromFile(aPicture(300, 200)))

        assertThat(photos.file(imported).size()).isEqualTo(300 to 200)
    }

    @Test
    fun stored_turnsThePictureTheWayItsExifOrientationSays() = runTest { // #88
        // Each orientation of aPicture(40, 20) as it is shown.
        val shown = mapOf(
            ExifInterface.ORIENTATION_NORMAL to Shown(40, 20, Color.RED, Color.GREEN, Color.BLUE),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to Shown(40, 20, Color.GREEN, Color.RED, Color.YELLOW),
            ExifInterface.ORIENTATION_ROTATE_180 to Shown(40, 20, Color.YELLOW, Color.BLUE, Color.GREEN),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to Shown(40, 20, Color.BLUE, Color.YELLOW, Color.RED),
            ExifInterface.ORIENTATION_TRANSPOSE to Shown(20, 40, Color.RED, Color.BLUE, Color.GREEN),
            ExifInterface.ORIENTATION_ROTATE_90 to Shown(20, 40, Color.BLUE, Color.RED, Color.YELLOW),
            ExifInterface.ORIENTATION_TRANSVERSE to Shown(20, 40, Color.YELLOW, Color.GREEN, Color.BLUE),
            ExifInterface.ORIENTATION_ROTATE_270 to Shown(20, 40, Color.GREEN, Color.YELLOW, Color.RED),
        )

        val imported = shown.mapValues { (orientation, _) ->
            val picked = aPicture(40, 20, "oriented-$orientation.jpg")
                .withExif(ExifInterface.TAG_ORIENTATION to orientation.toString())
            photos.file(importWhole(Uri.fromFile(picked))).shown()
        }

        assertThat(imported).isEqualTo(shown)
    }

    @Test
    @Config(sdk = [29]) // #92: the platform ExifInterface reads a PNG's or WebP's EXIF only from API 30
    fun stored_onAndroid10_turnsAPngOrWebpTheWayItsExifOrientationSays() = runTest {
        val rotated = Shown(20, 40, Color.BLUE, Color.RED, Color.YELLOW) // aPicture(40, 20) turned 90° clockwise

        val imported = listOf(Bitmap.CompressFormat.PNG to "png", @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP to "webp")
            .associate { (format, extension) ->
                val picked = aPicture(40, 20, "rotated.$extension", format)
                    .withExif(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString())
                extension to photos.file(importWhole(Uri.fromFile(picked))).shown()
            }

        assertThat(imported).isEqualTo(mapOf("png" to rotated, "webp" to rotated))
    }

    @Test
    fun stored_putsATransparentPictureOnWhite() = runTest { // #88: a JPEG has no transparency, which would show black
        val transparent = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val picked = temporaryFolder.newFile("transparent.png").apply {
            outputStream().use { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        val imported = photos.file(importWhole(Uri.fromFile(picked)))

        assertThat(BitmapFactory.decodeFile(imported.path).getPixel(5, 5).nearest()).isEqualTo(Color.WHITE)
    }

    @Test
    fun stage_storesTheRawPickedBytes_inThePhotoDirectory_underANewName() = runTest { // #10: for the crop screen to show
        val picked = aPicture(40, 20)

        val first = photos.stage(Uri.fromFile(picked))
        val second = photos.stage(Uri.fromFile(picked))

        assertThat(first).isNotEqualTo(second)
        assertThat(photos.file(first).parentFile).isEqualTo(directory)
        assertThat(photos.file(first).readBytes()).isEqualTo(picked.readBytes()) // as it is, so the crop screen reads its EXIF
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun stage_ofAFileThatIsntAPicture_throws_andLeavesNoFile() = runTest { // the crop screen only shows a real picture
        val picked = temporaryFolder.newFile("picked.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertThrows(IOException::class.java) { runBlocking { photos.stage(Uri.fromFile(picked)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stage_ofAPictureThatCantBeRead_throws_andLeavesNoFile() = runTest {
        val missing = Uri.fromFile(File(temporaryFolder.root, "gone.jpg"))

        assertThrows(FileNotFoundException::class.java) { runBlocking { photos.stage(missing) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stage_cancelledWhileCopying_leavesNoFile() = runBlocking {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = object : InputStream() {
            private var sent = false

            override fun read(): Int {
                if (sent) return release.await().let { -1 }
                sent = true
                reading.countDown()
                return 1
            }
        }
        val copying = FileCardPhotos(directory, { slow })
        val job = launch(Dispatchers.Default) { copying.stage(PICKED) }
        reading.await()

        job.cancel()
        release.countDown()
        job.join()

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stage_ofAPictureThatFailsPartway_throws_andLeavesNoFile() = runTest {
        val failing = object : InputStream() {
            private var sent = false

            override fun read(): Int = if (sent) throw IOException("connection lost") else 1.also { sent = true }
        }

        assertThrows(IOException::class.java) { runBlocking { FileCardPhotos(directory, { failing }).stage(PICKED) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun crop_storesOnlyTheFramedPartOfTheUprightPicture_andDeletesTheStagedSource() = runTest { // #10
        val picked = aPicture(40, 20)
        suspend fun cropped(crop: PhotoCrop) = photos.crop(photos.stage(Uri.fromFile(picked)), crop)

        val topLeft = cropped(PhotoCrop(0f, 0f, 0.5f, 0.5f))
        val rightHalf = cropped(PhotoCrop(0.5f, 0f, 1f, 1f))
        val bottomRight = cropped(PhotoCrop(0.5f, 0.5f, 1f, 1f))

        assertThat(photos.file(topLeft).shown()).isEqualTo(Shown(20, 10, Color.RED, Color.RED, Color.RED))
        assertThat(photos.file(rightHalf).shown()).isEqualTo(Shown(20, 20, Color.GREEN, Color.GREEN, Color.YELLOW))
        assertThat(photos.file(bottomRight).shown()).isEqualTo(Shown(20, 10, Color.YELLOW, Color.YELLOW, Color.YELLOW))
        assertThat(directory.list()).asList().containsExactly(topLeft, rightHalf, bottomRight) // no staged files left
    }

    @Test
    fun crop_isInTheUprightPicture_evenWhenTheSourceIsRotated() = runTest { // #10: the user frames the picture upright
        val rotated = aPicture(40, 20, "rotated.jpg")
            .withExif(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString())

        val topLeft = photos.crop(photos.stage(Uri.fromFile(rotated)), PhotoCrop(0f, 0f, 0.5f, 0.5f))

        // Upright the picture is 20×40, blue top-left (see import_turnsThePicture…); its top-left quarter is all blue.
        assertThat(photos.file(topLeft).shown()).isEqualTo(Shown(10, 20, Color.BLUE, Color.BLUE, Color.BLUE))
    }

    @Test
    fun crop_ofANonPictureStagedFile_throws_andLeavesNoFile() = runTest { // a corrupt capture the camera app wrote
        val capture = photos.newCapture()
        File(capture.uri.path!!).writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(IOException::class.java) { runBlocking { photos.crop(capture.name, PhotoCrop.WHOLE) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty() // the staged capture goes too
    }

    @Test
    fun crop_ofAStagedCapture_storesIt_andDeletesTheCapture() = runTest { // #10: a captured photo is a staged file already
        val capture = photos.newCapture()
        aPicture(40, 20).copyTo(File(capture.uri.path!!))

        val cropped = photos.crop(capture.name, PhotoCrop.WHOLE)

        assertThat(photos.file(cropped).shown()).isEqualTo(Shown(40, 20, Color.RED, Color.GREEN, Color.BLUE))
        assertThat(directory.list()).asList().containsExactly(cropped)
    }

    @Test
    fun crop_ofAFrameReachingPastThePicture_throws_andLeavesNoFile() = runTest { // #151: only a logo is framed zoomed out
        val staged = photos.stage(Uri.fromFile(aPicture(40, 20)))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { photos.crop(staged, PhotoCrop(-0.5f, 0f, 1.5f, 1f)) }
        }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun cropLogo_storesAPng_inThePhotoDirectory_andDeletesTheStagedSource() = runTest { // #151: backed up as photos are
        val staged = photos.stage(Uri.fromFile(aPicture(40, 20)))

        val logo = photos.cropLogo(staged, PhotoCrop.WHOLE)

        assertThat(photos.file(logo).parentFile).isEqualTo(directory)
        assertThat(directory.list()).asList().containsExactly(logo)
        assertThat(photos.file(logo).mimeType()).isEqualTo("image/png")
    }

    @Test
    fun logoPixels_areAStoredLogosColours_withTheirOpacity() = runTest { // #240: the contrast check reads them
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0x80FFFFFF.toInt(), 0x00000000)
        File(directory.apply { mkdirs() }, "logo").outputStream().use {
            Bitmap.createBitmap(pixels, 3, 1, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        assertThat(photos.logoPixels("logo")?.toList()).containsExactly(0xFFFF0000.toInt(), 0x80FFFFFF.toInt(), 0x00000000).inOrder()
    }

    @Test
    fun logoPixels_ofALargeLogo_areSampledDown() = runTest { // 512 px is a logo's most; a quarter of each side is plenty
        File(directory.apply { mkdirs() }, "logo").outputStream().use {
            Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        assertThat(photos.logoPixels("logo")?.size).isEqualTo(128 * 64)
    }

    @Test
    fun logoPixels_ofAMissingOrUnreadableFile_areNull() = runTest { // the logo is still saved, As is
        File(directory.apply { mkdirs() }, "broken").writeText("not a picture")

        assertThat(photos.logoPixels("missing")).isNull()
        assertThat(photos.logoPixels("broken")).isNull()
        assertThat(photos.logoPixels("../outside")).isNull()
    }

    @Test
    fun stagePhoto_copiesAStoredPhotoForTheCrop_andKeepsTheOriginal() = runTest { // #151: a card's photo as a logo
        val photo = importWhole(Uri.fromFile(aPicture(40, 20)))
        val original = photos.file(photo).readBytes()

        val staged = photos.stagePhoto(photo)

        assertThat(staged).isNotEqualTo(photo)
        assertThat(photos.file(staged).parentFile).isEqualTo(directory)
        assertThat(photos.file(staged).readBytes()).isEqualTo(original)
        assertThat(photos.file(photo).readBytes()).isEqualTo(original)
    }

    @Test
    fun cropLogo_ofAStagedPhotoCopy_keepsTheCardsPhoto() = runTest { // #151: the crop deletes its source, the copy
        val photo = importWhole(Uri.fromFile(aPicture(40, 20)))

        val logo = photos.cropLogo(photos.stagePhoto(photo), PhotoCrop.WHOLE)

        assertThat(directory.list()).asList().containsExactly(photo, logo)
    }

    @Test
    fun stagePhoto_ofAMissingPhoto_throws_andLeavesNoFile() = runTest { // #151
        assertThrows(FileNotFoundException::class.java) { runBlocking { photos.stagePhoto("gone") } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stagePhoto_ofAFileThatIsntAPicture_throws_andLeavesOnlyIt() = runTest { // #151: a corrupt photo isn't copied
        directory.mkdirs()
        File(directory, "corrupt").writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(IOException::class.java) { runBlocking { photos.stagePhoto("corrupt") } }

        assertThat(directory.list()).asList().containsExactly("corrupt")
    }

    @Test
    fun stagePhoto_ofANameThatIsntAPhotoName_throws_andLeavesNoFile() = runTest { // #151: it comes from a card's row
        assertThrows(IllegalArgumentException::class.java) { runBlocking { photos.stagePhoto("../escape") } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun cropLogo_scalesALargePictureDown_toItsLongSideLimit() = runTest { // #151
        val wide = photos.cropLogo(photos.stage(Uri.fromFile(aPicture(2000, 1000, "wide.jpg"))), PhotoCrop.WHOLE)
        val small = photos.cropLogo(photos.stage(Uri.fromFile(aPicture(300, 200, "small.jpg"))), PhotoCrop.WHOLE)

        assertThat(photos.file(wide).size()).isEqualTo(512 to 256)
        assertThat(photos.file(small).size()).isEqualTo(300 to 200)
    }

    @Test
    fun cropLogo_keepsTransparency() = runTest { // #151: a logo shows on the brand colour
        val transparent = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val picked = temporaryFolder.newFile("transparent.png").apply {
            outputStream().use { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        val logo = photos.file(photos.cropLogo(photos.stage(Uri.fromFile(picked)), PhotoCrop.WHOLE))

        assertThat(Color.alpha(BitmapFactory.decodeFile(logo.path).getPixel(5, 5))).isEqualTo(0)
    }

    @Test
    fun cropLogo_storesOnlyTheFramedPart() = runTest { // #151
        val staged = photos.stage(Uri.fromFile(aPicture(40, 20)))

        val logo = photos.cropLogo(staged, PhotoCrop(0.5f, 0f, 1f, 1f))

        assertThat(photos.file(logo).shown()).isEqualTo(Shown(20, 20, Color.GREEN, Color.GREEN, Color.YELLOW))
    }

    @Test
    fun cropLogo_isInTheUprightPicture() = runTest { // #151
        val rotated = aPicture(40, 20, "rotated.jpg")
            .withExif(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString())

        val logo = photos.cropLogo(photos.stage(Uri.fromFile(rotated)), PhotoCrop.WHOLE)

        assertThat(photos.file(logo).shown()).isEqualTo(Shown(20, 40, Color.BLUE, Color.RED, Color.YELLOW))
    }

    @Test
    fun cropLogo_ofAFrameReachingPastThePicture_isTransparentOutsideIt() = runTest { // #151: a wide logo framed whole
        val staged = photos.stage(Uri.fromFile(aPicture(40, 20)))

        // A frame twice the picture's width, centred on it: a quarter of the picture's width is empty on each side.
        val logo = BitmapFactory.decodeFile(photos.file(photos.cropLogo(staged, PhotoCrop(-0.5f, 0f, 1.5f, 1f))).path)

        assertThat(logo.width to logo.height).isEqualTo(80 to 20)
        assertThat(Color.alpha(logo.getPixel(5, 10))).isEqualTo(0)
        assertThat(logo.getPixel(25, 5).nearest()).isEqualTo(Color.RED)
        assertThat(logo.getPixel(55, 15).nearest()).isEqualTo(Color.YELLOW)
        assertThat(Color.alpha(logo.getPixel(75, 10))).isEqualTo(0)
    }

    @Test
    fun cropLogo_dropsThePicturesMetadata() = runTest { // #151, as for photos (#88)
        val picked = aPicture(40, 20).withExif(ExifInterface.TAG_GPS_LATITUDE to "47/1,30/1,0/1", ExifInterface.TAG_MAKE to "Maker")

        val logo = photos.file(photos.cropLogo(photos.stage(Uri.fromFile(picked)), PhotoCrop.WHOLE))

        val exif = ExifInterface(logo.path)
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
    }

    @Test
    fun cropLogo_ofANonPictureStagedFile_throws_andLeavesNoFile() = runTest { // #151
        val capture = photos.newCapture()
        File(capture.uri.path!!).writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(IOException::class.java) { runBlocking { photos.cropLogo(capture.name, PhotoCrop.WHOLE) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAPicture_stagesItAsItIs() = runTest { // #211: as stage does
        val picked = aPicture(40, 20, "picked.png", Bitmap.CompressFormat.PNG)

        val staged = photos.stageLogo(Uri.fromFile(picked))

        assertThat(photos.file(staged).readBytes()).isEqualTo(picked.readBytes())
    }

    @Test
    fun stageLogo_ofAnSvg_drawsItOnTransparency_atTheLogosLongSide() = runTest { // #211
        val svg = anSvg("""<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>""")

        val staged = photos.file(photos.stageLogo(Uri.fromFile(svg)))

        val drawn = BitmapFactory.decodeFile(staged.path)
        assertThat(staged.mimeType()).isEqualTo("image/png")
        assertThat(drawn.width to drawn.height).isEqualTo(512 to 256)
        assertThat(drawn.getPixel(128, 128)).isEqualTo(Color.RED)
        assertThat(Color.alpha(drawn.getPixel(384, 128))).isEqualTo(0)
        assertThat(directory.list()).asList().containsExactly(staged.name) // the copied SVG is gone
    }

    @Test
    fun stageLogo_ofAnSvgWithOnlyAViewBox_takesItsShapeFromTheViewBox() = runTest { // #211
        val svg = anSvg("""<svg xmlns="$SVG_NS" viewBox="0 0 300 100"><rect width="150" height="100" fill="red"/></svg>""")

        val drawn = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(svg))).path)

        assertThat(drawn.width to drawn.height).isEqualTo(512 to 171) // 512 × 100 / 300 = 170.7
        assertThat(drawn.getPixel(64, 85)).isEqualTo(Color.RED)
        assertThat(Color.alpha(drawn.getPixel(448, 85))).isEqualTo(0)
    }

    @Test
    fun stageLogo_ofAnSvgWithNoSizeAtAll_drawsItAsABrowserDoes_300By150() = runTest { // #211
        val svg = anSvg("""<svg xmlns="$SVG_NS"><rect width="150" height="150" fill="red"/></svg>""")

        val drawn = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(svg))).path)

        assertThat(drawn.width to drawn.height).isEqualTo(512 to 256)
        assertThat(drawn.getPixel(128, 128)).isEqualTo(Color.RED)
        assertThat(Color.alpha(drawn.getPixel(384, 128))).isEqualTo(0)
    }

    @Test
    fun stageLogo_ofAVeryThinSvg_isAtLeastOnePixelWide() = runTest { // #211
        val svg = anSvg("""<svg xmlns="$SVG_NS" width="1" height="100000"><rect width="1" height="100000" fill="red"/></svg>""")

        val staged = photos.file(photos.stageLogo(Uri.fromFile(svg)))

        assertThat(staged.size()).isEqualTo(1 to 512)
    }

    @Test
    fun stageLogo_ofAnSvgThatCantBeDrawn_throws_andLeavesNoFile() = runTest { // #211: the logo screen says so
        val unreadable = listOf(
            "hello",
            """<svg xmlns="$SVG_NS" width="200" height="100"><rect""",
            """<html xmlns="http://www.w3.org/1999/xhtml"/>""",
            """<svg xmlns="$SVG_NS" viewBox="0 0 0 100"><rect width="100" height="100" fill="red"/></svg>""",
        )

        unreadable.forEachIndexed { index, text ->
            val svg = anSvg(text, "unreadable-$index.svg")
            assertThrows(text, IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(svg)) } }
        }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAnSvgOverTheSizeLimit_throws_andLeavesNoFile() = runTest { // #211: too large to open
        val svg = """<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>"""
        fun padded(size: Long, name: String) = anSvg(svg + "<!--" + "x".repeat((size - svg.length - 7).toInt()) + "-->", name)
        val atTheLimit = padded(5L * 1024 * 1024, "at-the-limit.svg")
        val overIt = padded(5L * 1024 * 1024 + 1, "over-it.svg")
        assertThat(atTheLimit.length()).isEqualTo(5L * 1024 * 1024)

        val staged = photos.stageLogo(Uri.fromFile(atTheLimit))
        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(overIt)) } }

        assertThat(directory.list()).asList().containsExactly(staged)
    }

    @Test
    fun stageLogo_drawsNothingFromAFileTheSvgRefersTo() = runTest { // #211: nothing outside the file is read
        val red = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val redPng = temporaryFolder.newFile("red.png").apply { outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        fun referringTo(href: String) =
            """<svg xmlns="$SVG_NS" xmlns:xlink="http://www.w3.org/1999/xlink" width="100" height="100">""" +
                """<image xlink:href="$href" width="100" height="100"/></svg>"""
        val embedded = anSvg(referringTo("data:image/png;base64," + Base64.getEncoder().encodeToString(redPng.readBytes())), "embedded.svg")
        val linked = anSvg(referringTo(Uri.fromFile(redPng).toString()), "linked.svg")

        val drawnEmbedded = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(embedded))).path)
        val drawnLinked = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(linked))).path)

        assertThat(drawnEmbedded.getPixel(256, 256)).isEqualTo(Color.RED) // the control: an image in the file is drawn
        assertThat(Color.alpha(drawnLinked.getPixel(256, 256))).isEqualTo(0)
    }

    @Test
    fun stageLogo_ofAnSvgWithEntities_drawsThem() = runTest { // #211: as some vector editors export an SVG
        val svg = anSvg(
            """<?xml version="1.0"?><!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd" [""" +
                """<!ENTITY ns_svg "$SVG_NS"> <!ENTITY fill 'red'>]>""" +
                """<svg xmlns="&ns_svg;" width="200" height="100"><rect width="100" height="100" fill="&fill;"/></svg>""",
        )

        val drawn = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(svg))).path)

        assertThat(drawn.getPixel(128, 128)).isEqualTo(Color.RED)
    }

    @Test
    fun stageLogo_ofAnSvgWithAnEntityFromAnotherFile_throws_andLeavesNoFile() = runTest { // #211: nothing outside the file is read
        val rect = temporaryFolder.newFile("rect.xml").apply { writeText("""<rect width="100" height="100" fill="red"/>""") }
        val svg = anSvg(
            """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY rect SYSTEM "${Uri.fromFile(rect)}">]>""" +
                """<svg xmlns="$SVG_NS" width="200" height="100">&rect;</svg>""",
        )

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(svg)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAnSvgWithEntitiesInsideEntities_throws_andLeavesNoFile() = runTest { // #211: the shape of an expansion bomb
        val svg = anSvg(
            """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;&a;"><!ENTITY c "&b;&b;&b;&b;">]>""" +
                """<svg xmlns="$SVG_NS" width="200" height="100"><title>&c;</title><rect width="100" height="100" fill="red"/></svg>""",
        )

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(svg)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAnSvgWithEntitiesInsideEntities_inUtf16_throws_andLeavesNoFile() = runTest { // #211: not missed for its encoding
        val svg = temporaryFolder.newFile("utf16.svg").apply {
            writeText(
                """<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE svg [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;&a;">]>""" +
                    """<svg xmlns="$SVG_NS" width="200" height="100"><title>&b;</title><rect width="100" height="100" fill="red"/></svg>""",
                Charsets.UTF_16,
            )
        }

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(svg)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAGzippedSvg_drawsIt() = runTest { // #211 review: an .svgz is an image/svg+xml too
        val svg = gzipped("""<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>""")

        val drawn = BitmapFactory.decodeFile(photos.file(photos.stageLogo(Uri.fromFile(svg))).path)

        assertThat(drawn.getPixel(128, 128)).isEqualTo(Color.RED)
    }

    @Test
    fun stageLogo_ofAGzippedSvgWithEntitiesInsideEntities_throws_andLeavesNoFile() = runTest { // #211 review: not missed for its packing
        val svg = gzipped(
            """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;&a;">]>""" +
                """<svg xmlns="$SVG_NS" width="200" height="100"><title>&b;</title><rect width="100" height="100" fill="red"/></svg>""",
        )

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(svg)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAnSvgGzippedTwice_throws_andLeavesNoFile() = runTest { // #211 review: AndroidSVG would unpack the inner one
        val svg = """<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>"""
        val inner = ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(svg.toByteArray()) } }.toByteArray()
        val twice = temporaryFolder.newFile("twice.svgz").apply { GZIPOutputStream(outputStream()).use { it.write(inner) } }

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(twice)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_ofAGzippedSvgOverTheSizeLimitUnpacked_throws_andLeavesNoFile() = runTest { // #211 review: the limit is on the SVG
        val svg = """<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>"""
        val packed = gzipped(svg + "<!--" + "x".repeat(5 * 1024 * 1024) + "-->")
        assertThat(packed.length()).isLessThan(1024L * 1024)

        assertThrows(IOException::class.java) { runBlocking { photos.stageLogo(Uri.fromFile(packed)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun stageLogo_drawsTheSvgOnTheIoDispatcher() = runBlocking { // #211: never on the caller's (main) thread
        var ioThread: Thread? = null
        val io = Executors.newSingleThreadExecutor { Thread(it).also { thread -> ioThread = thread } }
        var drawnOn: Thread? = null
        val drawing = FileCardPhotos(
            directory,
            ApplicationProvider.getApplicationContext<Context>().contentResolver::openInputStream,
            io.asCoroutineDispatcher(),
            drawSvg = { source, target ->
                drawnOn = Thread.currentThread()
                drawSvgLogo(source, target)
            },
        )
        val svg = anSvg("""<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>""")

        try {
            drawing.stageLogo(Uri.fromFile(svg))
        } finally {
            io.shutdown()
        }

        assertThat(drawnOn).isSameInstanceAs(ioThread)
        assertThat(drawnOn).isNotSameInstanceAs(Thread.currentThread())
    }

    @Test
    fun stage_ofAnSvg_throws_andLeavesNoFile() = runTest { // #211: only a logo may be an SVG; card photos are unchanged
        val svg = anSvg("""<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>""")

        assertThrows(IOException::class.java) { runBlocking { photos.stage(Uri.fromFile(svg)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun cropLogo_ofAStagedSvg_storesTheUsualPng() = runTest { // #211
        val svg = anSvg("""<svg xmlns="$SVG_NS" width="200" height="100"><rect width="100" height="100" fill="red"/></svg>""")

        val logo = photos.file(photos.cropLogo(photos.stageLogo(Uri.fromFile(svg)), PhotoCrop.WHOLE))

        val drawn = BitmapFactory.decodeFile(logo.path)
        assertThat(logo.mimeType()).isEqualTo("image/png")
        assertThat(drawn.width to drawn.height).isEqualTo(512 to 256)
        assertThat(drawn.getPixel(128, 128)).isEqualTo(Color.RED)
        assertThat(Color.alpha(drawn.getPixel(384, 128))).isEqualTo(0)
    }

    @Test
    fun newCapture_isAFileInThePhotoDirectory_underANewName() = runTest { // #10: the camera app writes it, then crop stores it
        val first = photos.newCapture()
        val second = photos.newCapture()

        assertThat(first.name).isNotEqualTo(second.name)
        assertThat(first.name).endsWith(".jpg") // #10 review: a camera app may go by the target's extension
        assertThat(File(first.uri.path!!)).isEqualTo(File(directory, first.name))
    }

    @Test
    fun aCapture_isDeletedByItsName() = runTest { // #10: once imported, or when the camera app took none
        val capture = photos.newCapture()
        aPicture(4, 4).copyTo(File(capture.uri.path!!))

        photos.delete(listOf(capture.name))

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun delete_removesThosePhotos_andSkipsNamesWithoutAFile() = runTest {
        val picked = aPicture(4, 4)
        val kept = importWhole(Uri.fromFile(picked))
        val deleted = importWhole(Uri.fromFile(picked))

        photos.delete(listOf(deleted, "never-stored"))

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun delete_skipsANameThatIsntAPhotoName_andDeletesTheRest() = runTest { // #87 review: an edit drops such a name
        val picked = aPicture(4, 4)
        val deleted = importWhole(Uri.fromFile(picked))

        photos.delete(listOf("../loyscan.db", deleted))

        assertThat(directory.list()).asList().isEmpty()
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun discard_deletesThosePhotosInTheBackground() = runTest {
        val background = FileCardPhotos(directory, { null }, StandardTestDispatcher(testScheduler))
        val picked = aPicture(4, 4)
        val kept = importWhole(Uri.fromFile(picked))
        val discarded = importWhole(Uri.fromFile(picked))

        background.discard(listOf(discarded))
        advanceUntilIdle()

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun exists_saysWhetherThePhotosFileIsThere() = runTest {
        val imported = importWhole(Uri.fromFile(aPicture(4, 4)))

        assertThat(photos.exists(imported)).isTrue()
        assertThat(photos.exists("never-stored")).isFalse()
    }

    @Test
    fun exists_ofANameThatIsntAPhotoName_isFalse() = runTest { // #87 review: the card screen and form ask for every stored name
        assertThat(photos.exists("../loyscan.db")).isFalse()
        assertThat(photos.exists("")).isFalse()
    }

    @Test
    fun deleteUnnamed_deletesOnlyOldPhotosNotNamed() {
        val now = Instant.parse("2026-05-05T05:05:05Z")
        fun photo(name: String, age: Duration) =
            photos.file(name).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)); setLastModified((now - age).toEpochMilli()) }
        val named = photo("named", Duration.ofDays(9))
        val old = photo("old", Duration.ofDays(9))
        val recent = photo("recent", Duration.ofMinutes(1))

        photos.deleteUnnamed(setOf("named"), olderThan = now - Duration.ofDays(1))

        assertThat(named.exists()).isTrue()
        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    @Test
    fun file_refusesANameThatLeavesThePhotoDirectory() {
        assertThrows(IllegalArgumentException::class.java) { photos.file("../loyscan.db") }
    }

    /**
     * A [width]×[height] picture at full quality, a JPEG unless [format] says otherwise: red top left, green top right,
     * blue bottom left, yellow bottom right.
     */
    private fun aPicture(
        width: Int,
        height: Int,
        name: String = "picked.jpg",
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (middle, centre) = width / 2f to height / 2f
        fun quarter(left: Float, top: Float, right: Float, bottom: Float, colour: Int) =
            canvas.drawRect(left, top, right, bottom, Paint().apply { color = colour })
        quarter(0f, 0f, middle, centre, Color.RED)
        quarter(middle, 0f, width.toFloat(), centre, Color.GREEN)
        quarter(0f, centre, middle, height.toFloat(), Color.BLUE)
        quarter(middle, centre, width.toFloat(), height.toFloat(), Color.YELLOW)
        return temporaryFolder.newFile(name).apply {
            outputStream().use { bitmap.compress(format, 100, it) }
        }
    }

    /** An SVG file holding [text], as a picker or a share hands one over. */
    private fun anSvg(text: String, name: String = "logo.svg"): File = temporaryFolder.newFile(name).apply { writeText(text) }

    /** An `.svgz`: [text] gzipped. */
    private fun gzipped(text: String): File =
        temporaryFolder.newFile("logo.svgz").apply { GZIPOutputStream(outputStream()).use { it.write(text.toByteArray()) } }

    /** Writes EXIF [tags] into this JPEG, PNG or WebP, through androidx's ExifInterface: the platform's can't on API 29. */
    private fun File.withExif(vararg tags: Pair<String, String>): File = apply {
        val exif = ExifInterface(path)
        tags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.saveAttributes()
    }

    private fun File.size(): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    private fun File.mimeType(): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outMimeType
    }

    /** A picture as shown: its size, and its colours a quarter of the way in from three corners. */
    private data class Shown(val width: Int, val height: Int, val topLeft: Int, val topRight: Int, val bottomLeft: Int)

    private fun File.shown(): Shown {
        val bitmap = BitmapFactory.decodeFile(path)
        val (width, height) = bitmap.width to bitmap.height
        return Shown(
            width,
            height,
            topLeft = bitmap.getPixel(width / 4, height / 4).nearest(),
            topRight = bitmap.getPixel(width * 3 / 4, height / 4).nearest(),
            bottomLeft = bitmap.getPixel(width / 4, height * 3 / 4).nearest(),
        )
    }

    private companion object {
        val PICKED: Uri = Uri.parse("content://media/picker/0/1")

        const val SVG_NS = "http://www.w3.org/2000/svg"

        /** The test colour nearest to this pixel, so a JPEG's small colour shifts don't count. */
        fun Int.nearest(): Int = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.WHITE, Color.BLACK)
            .minBy { colour ->
                val (red, green, blue) = Triple(
                    Color.red(colour) - Color.red(this),
                    Color.green(colour) - Color.green(this),
                    Color.blue(colour) - Color.blue(this),
                )
                red * red + green * green + blue * blue
            }
    }
}
