package com.bdaniel.loyscan.ui.scanner

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The reading around zxing-cpp, with the decoding and the native reader replaced: zxing-cpp's library doesn't run on
 * the JVM. Robolectric for [Bitmap] and [Uri].
 *
 * The decode runs on the test scheduler, so its timeout is in virtual time. A test that decodes on a real thread uses
 * `runBlocking`: under `runTest`, virtual time jumps to the timeout as soon as the test thread is idle, before the other
 * thread has decoded (#79).
 */
@RunWith(AndroidJUnit4::class)
class ZxingCppPictureReaderTest {

    private val uri = Uri.parse("content://media/picker/0/42")
    private val picture = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)

    private fun TestScope.reader(
        decode: suspend (Uri) -> Bitmap = { picture },
        read: BitmapReader = BitmapReader { barcode },
        decodeTimeout: Duration = 30.seconds,
    ) = ZxingCppPictureReader(
        decode,
        newReader = { read },
        decodeContext = StandardTestDispatcher(testScheduler),
        decodeTimeout = decodeTimeout,
    )

    @Test
    fun aPictureWithABarcode_isFound() = runTest {
        val decoded = mutableListOf<Uri>()

        val scan = reader(decode = { decoded += it; picture }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Found(barcode))
        assertThat(decoded).containsExactly(uri)
    }

    @Test
    fun aPictureWithoutABarcode_isNoBarcode() = runTest {
        val scan = reader(read = { null }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.NoBarcode)
    }

    @Test
    fun aPictureWhoseProviderThrows_isUnreadable() = runTest { // review of #76: any provider failure, not only I/O
        val scan = reader(decode = { throw IllegalArgumentException("unknown URI") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun leavingWhileThePictureIsDecoded_cancelsTheRead() = runTest { // review of #76: not swallowed as Unreadable
        val download = CompletableDeferred<Unit>()
        var scan: PictureScan? = null
        val job = launch { scan = reader(decode = { download.await(); picture }).read(uri) }
        runCurrent()

        job.cancel()
        job.join()
        download.complete(Unit)
        runCurrent()

        assertThat(scan).isNull()
        assertThat(picture.isRecycled).isTrue() // #79: the caller doesn't wait for the decode, so the late picture is recycled
    }

    @Test
    fun aPictureWhoseDecodeDoesNotFinishInTime_isUnreadable() = runTest { // #79: a cloud photo on a slow network
        val download = CompletableDeferred<Unit>()

        val scan = reader(decode = { download.await(); picture }, decodeTimeout = 30.seconds).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
        assertThat(currentTime).isEqualTo(30.seconds.inWholeMilliseconds)
    }

    @Test
    fun aDecodeFinishingAfterTheTimeout_isNotReadAndItsPictureIsRecycled() = runTest { // #79
        val download = CompletableDeferred<Unit>()
        var reads = 0
        val scan = reader(decode = { download.await(); picture }, read = { reads++; barcode }).read(uri)

        download.complete(Unit)
        runCurrent()

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
        assertThat(picture.isRecycled).isTrue()
        assertThat(reads).isEqualTo(0)
    }

    @Test
    fun aPictureThatCannotBeOpened_isUnreadable() = runTest {
        val scan = reader(decode = { throw FileNotFoundException("gone") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun aPictureThatCannotBeDecoded_isUnreadable() = runTest { // ImageDecoder.DecodeException is an IOException
        val scan = reader(decode = { throw IOException("not an image") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun aPictureWeMayNotOpen_isUnreadable() = runTest { // e.g. the picker's grant has expired
        val scan = reader(decode = { throw SecurityException("no grant") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun aPictureTheReaderRejects_isUnreadable() = runTest {
        val scan = reader(read = { error("Unsupported image format in AndroidBitmap") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun aReaderWithoutItsNativeLibrary_isUnreadable() = runTest {
        val scan = ZxingCppPictureReader(
            { picture },
            newReader = { throw UnsatisfiedLinkError("no zxingcpp") },
            decodeContext = StandardTestDispatcher(testScheduler),
        ).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun thePicture_isDecodedOnTheIoContextAndReadOnTheReadContext() = runBlocking { // review of #76: a cloud photo downloads
        val io = Executors.newSingleThreadExecutor { Thread(it, "test-io") }
        val cpu = Executors.newSingleThreadExecutor { Thread(it, "test-read") }
        val threads = mutableListOf<String>()
        try {
            ZxingCppPictureReader(
                decode = { threads += threadName(); picture },
                newReader = { BitmapReader { threads += threadName(); barcode } },
                decodeContext = io.asCoroutineDispatcher(),
                readContext = cpu.asCoroutineDispatcher(),
            ).read(uri)
        } finally {
            io.shutdown()
            cpu.shutdown()
        }

        assertThat(threads).containsExactly("test-io", "test-read").inOrder()
    }

    @Test
    fun byDefault_thePictureIsDecodedAndReadOffTheCallersThread() = runBlocking {
        val caller = Thread.currentThread()
        val threads = mutableListOf<Thread>()

        ZxingCppPictureReader(
            decode = { threads += Thread.currentThread(); picture },
            newReader = { BitmapReader { threads += Thread.currentThread(); barcode } },
        ).read(uri)

        assertThat(threads).hasSize(2)
        assertThat(threads).doesNotContain(caller)
    }

    /** The thread's own name, without the coroutine name that coroutines' debug mode appends to it. */
    private fun threadName() = Thread.currentThread().name.substringBefore(" @")

    @Test
    fun thePicture_isRecycledOnceRead() = runTest {
        reader().read(uri)

        assertThat(picture.isRecycled).isTrue()
    }

    @Test
    fun thePicture_isRecycledAlsoWhenTheReaderFails() = runTest {
        reader(read = { error("unreadable") }).read(uri)

        assertThat(picture.isRecycled).isTrue()
    }

    @Test
    fun aScreenshot_isDecodedAtItsOwnSize() { // review of #76: resampling a screenshot can lose a dense barcode's modules
        assertThat(pictureTargetSize(1440, 3200)).isNull() // QHD+ phone
        assertThat(pictureTargetSize(1344, 2992)).isNull()
        assertThat(pictureTargetSize(2208, 1840)).isNull() // foldable, inner screen
        assertThat(pictureTargetSize(2560, 1600)).isNull() // tablet
    }

    @Test
    fun aLargePhoto_isScaledDownToTheMaximumNumberOfPixels() {
        assertThat(pictureTargetSize(8000, 6000, maxPixels = 12_000_000)).isEqualTo(4000 to 3000)
        assertThat(pictureTargetSize(6000, 8000, maxPixels = 12_000_000)).isEqualTo(3000 to 4000)
    }

    @Test
    fun aVeryThinPicture_keepsAtLeastOnePixel() {
        assertThat(pictureTargetSize(1_000_000, 1, maxPixels = 1000)).isEqualTo(1000 to 1)
        assertThat(pictureTargetSize(1, 1_000_000, maxPixels = 1000)).isEqualTo(1 to 1000)
    }

    @Test
    fun aWideGamutPicture_isCopiedToArgb8888AndTheOriginalRecycled() { // zxing-cpp reads only 8-bit RGBA bitmaps
        val wide = Bitmap.createBitmap(4, 2, Bitmap.Config.RGBA_F16)

        val readable = wide.toReadableConfig()

        assertThat(readable.config).isEqualTo(Bitmap.Config.ARGB_8888)
        assertThat(listOf(readable.width, readable.height)).containsExactly(4, 2).inOrder()
        assertThat(wide.isRecycled).isTrue()
    }

    @Test
    fun anArgb8888Picture_isKeptAsItIs() {
        assertThat(picture.toReadableConfig()).isSameInstanceAs(picture)
    }
}
