package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.testing.FakePictureReader
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads its route (`SharedPicture.uri`), which needs a Bundle, so it runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
class SharedPictureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reader = FakePictureReader()
    private val savedStateHandle = SavedStateHandle(route = SharedPicture(SHARED_URI))

    @Test
    fun theSharedPicture_isRead_andWhileItIsTheStateIsReading() = runTest {
        reader.gate = CompletableDeferred()

        val viewModel = SharedPictureViewModel(savedStateHandle, reader)

        viewModel.state.test {
            assertThat(awaitItem()).isNull()
            assertThat(reader.reads).containsExactly(Uri.parse(SHARED_URI))

            reader.result = PictureScan.Found(EAN_13_SCAN)
            reader.gate!!.complete(Unit)
            assertThat(awaitItem()).isEqualTo(PictureScan.Found(EAN_13_SCAN))
        }
    }

    @Test
    fun aPictureWithoutABarcode_saysSo() = runTest {
        reader.result = PictureScan.NoBarcode

        val viewModel = SharedPictureViewModel(savedStateHandle, reader)

        assertThat(viewModel.state.value).isEqualTo(PictureScan.NoBarcode)
    }

    @Test
    fun aPictureThatCannotBeOpened_saysSo() = runTest {
        reader.result = PictureScan.Unreadable

        val viewModel = SharedPictureViewModel(savedStateHandle, reader)

        assertThat(viewModel.state.value).isEqualTo(PictureScan.Unreadable)
    }

    @Test
    fun afterProcessDeath_theBarcodeReadIsKept_andThePictureIsNotReadAgain() = runTest { // #77
        val gs1 = ScannedBarcode("10ABC\u001D9799123456", BarcodeFormat.GS1_128, gs1 = true)
        reader.result = PictureScan.Found(gs1)
        SharedPictureViewModel(savedStateHandle, reader)
        val restoredReader = FakePictureReader()

        val restored = SharedPictureViewModel(savedStateHandle, restoredReader)

        assertThat(restored.state.value).isEqualTo(PictureScan.Found(gs1))
        assertThat(restoredReader.reads).isEmpty()
    }

    @Test
    fun afterProcessDeath_aProblemIsKept_andThePictureIsNotReadAgain() = runTest { // #77
        for (problem in listOf(PictureScan.NoBarcode, PictureScan.Unreadable)) {
            val handle = SavedStateHandle(route = SharedPicture(SHARED_URI))
            SharedPictureViewModel(handle, FakePictureReader(problem))
            val restoredReader = FakePictureReader()

            val restored = SharedPictureViewModel(handle, restoredReader)

            assertThat(restored.state.value).isEqualTo(problem)
            assertThat(restoredReader.reads).isEmpty()
        }
    }

    @Test
    fun afterProcessDeath_whileReading_thePictureIsReadAgain() = runTest {
        reader.gate = CompletableDeferred()
        SharedPictureViewModel(savedStateHandle, reader)
        val restoredReader = FakePictureReader(PictureScan.Found(EAN_13_SCAN))

        val restored = SharedPictureViewModel(savedStateHandle, restoredReader)

        assertThat(restoredReader.reads).containsExactly(Uri.parse(SHARED_URI))
        assertThat(restored.state.value).isEqualTo(PictureScan.Found(EAN_13_SCAN))
    }
}

private const val SHARED_URI = "content://media/picker/0/42"
private val EAN_13_SCAN = ScannedBarcode("5901234123457", BarcodeFormat.EAN_13)
