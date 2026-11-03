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
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

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

    @Test
    fun import_storesThePictureInThePhotoDirectory_underANewName() = runTest {
        val picked = aPicture(40, 20)

        val first = photos.import(Uri.fromFile(picked))
        val second = photos.import(Uri.fromFile(picked))

        assertThat(first).isNotEqualTo(second)
        assertThat(photos.file(first).parentFile).isEqualTo(directory)
        assertThat(directory.list()).asList().containsExactly(first, second)
        assertThat(photos.file(first).shown()).isEqualTo(Shown(40, 20, Color.RED, Color.GREEN, Color.BLUE))
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun import_dropsTheLocationAndTheOtherExif() = runTest { // #88
        val picked = aPicture(40, 20).withExif(
            ExifInterface.TAG_GPS_LATITUDE to "47/1,29/1,0/1",
            ExifInterface.TAG_GPS_LATITUDE_REF to "N",
            ExifInterface.TAG_GPS_LONGITUDE to "19/1,2/1,0/1",
            ExifInterface.TAG_GPS_LONGITUDE_REF to "E",
            ExifInterface.TAG_MAKE to "Example",
            ExifInterface.TAG_DATETIME to "2026:05:05 05:05:05",
        )

        val imported = photos.file(photos.import(Uri.fromFile(picked)))

        val exif = ExifInterface(imported.path)
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME)).isNull()
    }

    @Test
    fun import_scalesALargePictureDown_toItsLongSideLimit() = runTest { // #88
        val wide = photos.import(Uri.fromFile(aPicture(5000, 2500, "wide.jpg")))
        val tall = photos.import(Uri.fromFile(aPicture(1000, 3000, "tall.jpg")))

        assertThat(photos.file(wide).size()).isEqualTo(MAX_PHOTO_SIDE to MAX_PHOTO_SIDE / 2)
        assertThat(photos.file(tall).size()).isEqualTo(683 to MAX_PHOTO_SIDE)
    }

    @Test
    fun import_keepsTheSizeOfASmallerPicture() = runTest { // #88: not enlarged
        val imported = photos.import(Uri.fromFile(aPicture(300, 200)))

        assertThat(photos.file(imported).size()).isEqualTo(300 to 200)
    }

    @Test
    fun import_turnsThePictureTheWayItsExifOrientationSays() = runTest { // #88
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
            photos.file(photos.import(Uri.fromFile(picked))).shown()
        }

        assertThat(imported).isEqualTo(shown)
    }

    @Test
    @Config(sdk = [29]) // #92: the platform ExifInterface reads a PNG's or WebP's EXIF only from API 30
    fun import_onAndroid10_turnsAPngOrWebpTheWayItsExifOrientationSays() = runTest {
        val rotated = Shown(20, 40, Color.BLUE, Color.RED, Color.YELLOW) // aPicture(40, 20) turned 90° clockwise

        val imported = listOf(Bitmap.CompressFormat.PNG to "png", @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP to "webp")
            .associate { (format, extension) ->
                val picked = aPicture(40, 20, "rotated.$extension", format)
                    .withExif(ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString())
                extension to photos.file(photos.import(Uri.fromFile(picked))).shown()
            }

        assertThat(imported).isEqualTo(mapOf("png" to rotated, "webp" to rotated))
    }

    @Test
    fun import_putsATransparentPictureOnWhite() = runTest { // #88: a JPEG has no transparency, which would show black
        val transparent = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val picked = temporaryFolder.newFile("transparent.png").apply {
            outputStream().use { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        val imported = photos.file(photos.import(Uri.fromFile(picked)))

        assertThat(BitmapFactory.decodeFile(imported.path).getPixel(5, 5).nearest()).isEqualTo(Color.WHITE)
    }

    @Test
    fun import_ofAFileThatIsntAPicture_throws_andLeavesNoFile() = runTest {
        val picked = temporaryFolder.newFile("picked.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertThrows(IOException::class.java) { runBlocking { photos.import(Uri.fromFile(picked)) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun import_ofAPictureThatCantBeRead_throws_andLeavesNoFile() = runTest {
        val missing = Uri.fromFile(File(temporaryFolder.root, "gone.jpg"))

        assertThrows(FileNotFoundException::class.java) { runBlocking { photos.import(missing) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun import_cancelledWhileCopying_leavesNoFile() = runBlocking { // e.g. Back while a cloud picture downloads
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
        val job = launch(Dispatchers.Default) { copying.import(PICKED) }
        reading.await()

        job.cancel()
        release.countDown()
        job.join()

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun import_ofAPictureThatFailsPartway_throws_andLeavesNoFile() = runTest {
        val failing = object : InputStream() {
            private var sent = false

            override fun read(): Int = if (sent) throw IOException("connection lost") else 1.also { sent = true }
        }

        assertThrows(IOException::class.java) { runBlocking { FileCardPhotos(directory, { failing }).import(PICKED) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun delete_removesThosePhotos_andSkipsNamesWithoutAFile() = runTest {
        val picked = aPicture(4, 4)
        val kept = photos.import(Uri.fromFile(picked))
        val deleted = photos.import(Uri.fromFile(picked))

        photos.delete(listOf(deleted, "never-stored"))

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun delete_skipsANameThatIsntAPhotoName_andDeletesTheRest() = runTest { // #87 review: an edit drops such a name
        val picked = aPicture(4, 4)
        val deleted = photos.import(Uri.fromFile(picked))

        photos.delete(listOf("../loyscan.db", deleted))

        assertThat(directory.list()).asList().isEmpty()
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun discard_deletesThosePhotosInTheBackground() = runTest {
        val background = FileCardPhotos(directory, { null }, StandardTestDispatcher(testScheduler))
        val picked = aPicture(4, 4)
        val kept = photos.import(Uri.fromFile(picked))
        val discarded = photos.import(Uri.fromFile(picked))

        background.discard(listOf(discarded))
        advanceUntilIdle()

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun exists_saysWhetherThePhotosFileIsThere() = runTest {
        val imported = photos.import(Uri.fromFile(aPicture(4, 4)))

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
