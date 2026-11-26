package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** A picture shared to LoyScan as a store logo (#151): copied in at once. Reads its route, so it runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
class SharedLogoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photos = FakeCardPhotos()
    private val handle = SavedStateHandle(route = SharedLogo(PICTURE))

    @Test
    fun theSharedPicture_isCopiedIn_once() = runTest {
        val viewModel = SharedLogoViewModel(handle, photos)

        assertThat(photos.staged).containsExactly(Uri.parse(PICTURE))
        assertThat(viewModel.state.value).isEqualTo(SharedLogoState(staged = "staged-1"))
    }

    @Test
    fun aSharedSvg_isCopiedIn_asALogo() = runTest { // #211: drawn when it is copied in
        val svg = "content://example.provider/logo.svg"

        val viewModel = SharedLogoViewModel(SavedStateHandle(route = SharedLogo(svg)), photos)

        assertThat(photos.staged).containsExactly(Uri.parse(svg))
        assertThat(viewModel.state.value).isEqualTo(SharedLogoState(staged = "staged-1"))
    }

    @Test
    fun whileItIsCopied_nothingIsStagedYet() = runTest {
        photos.importGate = CompletableDeferred()

        val viewModel = SharedLogoViewModel(handle, photos)

        assertThat(viewModel.state.value).isEqualTo(SharedLogoState())
    }

    @Test
    fun aPictureThatCantBeCopied_saysSo() = runTest {
        photos.importFailure = IOException("no such picture")

        val viewModel = SharedLogoViewModel(handle, photos)

        assertThat(viewModel.state.value).isEqualTo(SharedLogoState(failed = true))
    }

    @Test
    fun afterProcessDeath_theCopyIsKept_andNotMadeAgain() = runTest {
        SharedLogoViewModel(handle, photos)

        val restored = SharedLogoViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), photos)

        assertThat(photos.staged).hasSize(1)
        assertThat(restored.state.value.staged).isEqualTo("staged-1")
    }
}

private const val PICTURE = "content://media/picker/0/7"
