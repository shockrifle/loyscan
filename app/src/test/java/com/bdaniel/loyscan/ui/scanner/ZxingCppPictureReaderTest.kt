package com.bdaniel.loyscan.ui.scanner

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The reading around zxing-cpp, with the decoding and the native reader replaced: zxing-cpp's library doesn't run on
 * the JVM. Robolectric for [Bitmap] and [Uri].
 */
@RunWith(AndroidJUnit4::class)
class ZxingCppPictureReaderTest {

    private val uri = Uri.parse("content://media/picker/0/42")
    private val picture = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)

    private fun reader(
        decode: (Uri) -> Bitmap = { picture },
        read: BitmapReader = BitmapReader { barcode },
    ) = ZxingCppPictureReader(decode, newReader = { read })

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
        val scan = ZxingCppPictureReader({ picture }, newReader = { throw UnsatisfiedLinkError("no zxingcpp") }).read(uri)

        assertThat(scan).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun thePicture_isDecodedAndReadOffTheCallersThread() = runTest {
        val caller = Thread.currentThread()
        val threads = mutableListOf<Thread>()

        reader(
            decode = { threads += Thread.currentThread(); picture },
            read = { threads += Thread.currentThread(); barcode },
        ).read(uri)

        assertThat(threads).hasSize(2)
        assertThat(threads).doesNotContain(caller)
    }

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
    fun aSmallPicture_isDecodedAtItsOwnSize() {
        assertThat(pictureTargetSize(1080, 2400, maxSide = 2400)).isNull()
        assertThat(pictureTargetSize(640, 480, maxSide = 2048)).isNull()
    }

    @Test
    fun aLargePicture_isScaledDownToTheMaximumOnItsLongSide() {
        assertThat(pictureTargetSize(4000, 3000, maxSide = 2000)).isEqualTo(2000 to 1500)
        assertThat(pictureTargetSize(1440, 3200, maxSide = 2048)).isEqualTo(922 to 2048)
    }

    @Test
    fun aVeryThinPicture_keepsAtLeastOnePixel() {
        assertThat(pictureTargetSize(10_000, 1, maxSide = 2048)).isEqualTo(2048 to 1)
    }
}
