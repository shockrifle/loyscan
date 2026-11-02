package com.bdaniel.loyscan

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.ui.scanner.BitmapReader
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.bdaniel.loyscan.ui.scanner.ZxingCppPictureReader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** Robolectric for [Uri], which a picked picture is. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pictureReader = FakePictureReader()
    private val viewModel = ScannerViewModel(hintDelay = 8.seconds, pictureReader)
    private val picture = Uri.parse("content://media/picker/0/42")
    private val barcode = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)

    @Test
    fun hint_appearsOnceTheCameraHasRunForTheHintDelay() = runTest {
        viewModel.onCameraStarted(torchAvailable = false)

        advanceTimeBy(7.9.seconds)
        assertThat(viewModel.state.value.showHint).isFalse()
        advanceTimeBy(0.2.seconds)
        assertThat(viewModel.state.value.showHint).isTrue()
    }

    @Test
    fun hint_doesNotCountTimeBeforeTheCameraStarts() = runTest {
        advanceTimeBy(60.seconds)
        assertThat(viewModel.state.value.showHint).isFalse()

        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(7.9.seconds)

        assertThat(viewModel.state.value.showHint).isFalse()
    }

    @Test
    fun hint_isNotDelayedAgainWhenTheCameraRestarts() = runTest { // e.g. rotation rebinds the camera
        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(5.seconds)

        viewModel.onCameraStarted(torchAvailable = false)
        advanceTimeBy(3.1.seconds)

        assertThat(viewModel.state.value.showHint).isTrue()
    }

    @Test
    fun torch_togglesOnlyWhenTheCameraHasOne() = runTest {
        viewModel.toggleTorch()
        assertThat(viewModel.state.value.torchOn).isFalse()

        viewModel.onCameraStarted(torchAvailable = true)
        viewModel.toggleTorch()
        assertThat(viewModel.state.value).isEqualTo(ScannerViewModel.State(torchAvailable = true, torchOn = true))

        viewModel.toggleTorch()
        assertThat(viewModel.state.value.torchOn).isFalse()
    }

    @Test
    fun torch_isOffWhenTheCameraRestartsWithoutOne() = runTest {
        viewModel.onCameraStarted(torchAvailable = true)
        viewModel.toggleTorch()

        viewModel.onCameraStarted(torchAvailable = false)

        assertThat(viewModel.state.value.torchOn).isFalse()
    }

    @Test
    fun onScanned_acceptsTheFirstScanOnly() {
        assertThat(viewModel.onScanned()).isTrue()
        assertThat(viewModel.onScanned()).isFalse()
        assertThat(viewModel.onScanned()).isFalse()
    }

    @Test
    fun cameraFailure_isReported() = runTest {
        viewModel.onCameraFailed()
        runCurrent()

        assertThat(viewModel.state.value.cameraFailed).isTrue()
    }

    @Test
    fun cameraUnavailable_isShown_untilItIsAvailableAgain() = runTest { // #40
        viewModel.onCameraAvailable(false)
        assertThat(viewModel.state.value.cameraUnavailable).isTrue()

        viewModel.onCameraAvailable(true)
        assertThat(viewModel.state.value.cameraUnavailable).isFalse()
    }

    @Test
    fun aPickedPictureWithABarcode_isHandedOnAsAScan() = runTest { // #76
        pictureReader.result = PictureScan.Found(barcode)

        viewModel.pictureScans.test {
            viewModel.onPicturePicked(picture)

            assertThat(awaitItem()).isEqualTo(barcode)
        }
        assertThat(pictureReader.reads).containsExactly(picture)
        assertThat(viewModel.state.value.pictureProblem).isNull()
    }

    @Test
    fun aPickedPicture_isReadingUntilTheReaderAnswers() = runTest {
        val gate = CompletableDeferred<Unit>().also { pictureReader.gate = it }

        viewModel.onPicturePicked(picture)
        assertThat(viewModel.state.value.readingPicture).isTrue()

        gate.complete(Unit)
        assertThat(viewModel.state.value.readingPicture).isFalse()
    }

    @Test
    fun aPickedPictureWithoutABarcode_saysSo_andHandsOnNothing() = runTest {
        pictureReader.result = PictureScan.NoBarcode

        viewModel.pictureScans.test {
            viewModel.onPicturePicked(picture)

            expectNoEvents()
        }
        assertThat(viewModel.state.value.pictureProblem).isEqualTo(ScannerViewModel.PictureProblem.NO_BARCODE)
    }

    @Test
    fun anUnreadablePicture_saysSo() = runTest {
        pictureReader.result = PictureScan.Unreadable

        viewModel.onPicturePicked(picture)

        assertThat(viewModel.state.value.pictureProblem).isEqualTo(ScannerViewModel.PictureProblem.UNREADABLE)
    }

    @Test
    fun anotherPicture_clearsTheLastProblemWhileItIsRead() = runTest {
        viewModel.onPicturePicked(picture)
        assertThat(viewModel.state.value.pictureProblem).isEqualTo(ScannerViewModel.PictureProblem.NO_BARCODE)
        pictureReader.gate = CompletableDeferred()

        viewModel.onPicturePicked(Uri.parse("content://media/picker/0/43"))

        assertThat(viewModel.state.value.pictureProblem).isNull()
    }

    @Test
    fun cancellingThePicker_leavesTheStateAsItWas() = runTest {
        viewModel.onPicturePicked(picture)
        val before = viewModel.state.value

        viewModel.onPicturePicked(null)

        assertThat(viewModel.state.value).isEqualTo(before)
        assertThat(pictureReader.reads).containsExactly(picture)
    }

    @Test
    fun aPictureReadAfterTheCameraScanned_isNotHandedOn() = runTest { // first scan only
        pictureReader.result = PictureScan.Found(barcode)
        assertThat(viewModel.onScanned()).isTrue()

        viewModel.pictureScans.test {
            viewModel.onPicturePicked(picture)

            expectNoEvents()
        }
    }

    @Test
    fun aCameraScanAfterAPictureScan_isDropped() = runTest {
        pictureReader.result = PictureScan.Found(barcode)

        viewModel.onPicturePicked(picture)

        assertThat(viewModel.onScanned()).isFalse()
    }

    @Test
    fun aSecondPictureWhileOneIsRead_isIgnored() = runTest {
        pictureReader.gate = CompletableDeferred()
        viewModel.onPicturePicked(picture)

        viewModel.onPicturePicked(Uri.parse("content://media/picker/0/43"))

        assertThat(pictureReader.reads).containsExactly(picture)
    }

    @Test
    fun aPictureThatTimesOut_canBePickedAgain_andItsLateResultIsIgnored() = runTest { // #79
        val firstDownload = CompletableDeferred<Unit>()
        var decodes = 0
        val viewModel = ScannerViewModel(
            hintDelay = 8.seconds,
            ZxingCppPictureReader(
                decode = { _, _ ->
                    if (decodes++ == 0) firstDownload.await()
                    Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                },
                newReader = { BitmapReader { barcode } },
                decodeContext = mainDispatcherRule.testDispatcher,
                decodeTimeout = 30.seconds,
            ),
        )

        viewModel.pictureScans.test {
            viewModel.onPicturePicked(picture)
            advanceTimeBy(30.seconds)
            runCurrent()
            val timedOut = viewModel.state.value
            assertThat(timedOut.readingPicture).isFalse()
            assertThat(timedOut.pictureProblem).isEqualTo(ScannerViewModel.PictureProblem.UNREADABLE)

            firstDownload.complete(Unit)
            runCurrent()
            expectNoEvents()
            assertThat(viewModel.state.value).isEqualTo(timedOut)

            viewModel.onPicturePicked(picture)
            assertThat(awaitItem()).isEqualTo(barcode)
        }
    }
}
