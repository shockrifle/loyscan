package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.keep
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** A store's logo screen (#151). Reads its route (`StoreLogo.storeId`), which needs a Bundle, so it runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
class StoreLogoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stores = FakeStoreRepository(listOf(aStore(id = "shop", name = "Shop"), aStore(id = "other", name = "Other")))
    private val photos = FakeCardPhotos()
    private val picked: Uri = Uri.parse("content://media/picker/0/1")
    private val square = PhotoCrop(0.25f, 0f, 0.75f, 1f)

    @Test
    fun theScreen_startsAsTheStore_withItsLogo() = runTest {
        stores.setLogo("shop", "logo")
        photos.stored += "logo"

        val state = viewModel().state.value

        assertThat(state.store?.name).isEqualTo("Shop")
        assertThat(state.store?.logo).isEqualTo("logo")
        assertThat(state.ready).isTrue()
    }

    @Test
    fun aLogoWhoseFileIsMissing_isNoLogo() = runTest { // as a missing photo (#87)
        stores.setLogo("shop", "lost")

        val state = viewModel().state.value

        assertThat(state.store?.name).isEqualTo("Shop")
        assertThat(state.store?.logo).isNull()
    }

    @Test
    fun aStoreThatIsGone_saysSo_andTakesNoLogo() = runTest {
        val viewModel = viewModel(storeId = "gone")

        viewModel.onPicked(picked)

        assertThat(viewModel.state.value.storeGone).isTrue()
        assertThat(viewModel.state.value.ready).isFalse()
        assertThat(photos.staged).isEmpty()
    }

    @Test
    fun aPickedPicture_opensTheCrop_andTheCropBecomesTheStoresLogo() = runTest {
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
        viewModel.confirmCrop(square)

        assertThat(photos.croppedLogos).containsExactly("staged-1" to square)
        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isEqualTo("logo-1")
        assertThat(viewModel.state.value.store?.logo).isEqualTo("logo-1")
        assertThat(viewModel.state.value.cropping).isNull()
        assertThat(viewModel.state.value.working).isFalse()
    }

    @Test
    fun aCancelledPick_changesNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onPicked(null)

        assertThat(viewModel.state.value.cropping).isNull()
        assertThat(photos.staged).isEmpty()
    }

    @Test
    fun cancellingTheCrop_dropsThePicture_andKeepsTheLogo() = runTest {
        stores.setLogo("shop", "logo")
        photos.stored += "logo"
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        viewModel.cancelCrop()

        assertThat(viewModel.state.value.cropping).isNull()
        assertThat(photos.stored).containsExactly("logo")
        assertThat(viewModel.state.value.store?.logo).isEqualTo("logo")
    }

    @Test
    fun whileAPictureIsCopiedIn_theScreenIsWorking_andNoOtherIsTaken() = runTest {
        val gate = CompletableDeferred<Unit>()
        photos.importGate = gate
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        assertThat(viewModel.state.value.working).isTrue()
        viewModel.onPicked(Uri.parse("content://media/picker/0/2"))
        viewModel.removeLogo()
        gate.complete(Unit)

        assertThat(photos.staged).containsExactly(picked)
        assertThat(viewModel.state.value.working).isFalse()
        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
    }

    @Test
    fun aPictureThatCantBeCopiedIn_saysSo_untilTheNextPick() = runTest {
        photos.importFailure = IOException("not a picture")
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        assertThat(viewModel.state.value.failed).isTrue()
        assertThat(viewModel.state.value.working).isFalse()
        photos.importFailure = null
        viewModel.onPicked(picked)

        assertThat(viewModel.state.value.failed).isFalse()
    }

    @Test
    fun aPictureThatCantBeCropped_saysSo_andKeepsTheLogo() = runTest {
        stores.setLogo("shop", "logo")
        photos.stored += "logo"
        photos.cropFailure = IOException("can't encode")
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        viewModel.confirmCrop(square)

        assertThat(viewModel.state.value.failed).isTrue()
        assertThat(viewModel.state.value.store?.logo).isEqualTo("logo")
        assertThat(photos.stored).containsExactly("logo") // the staged picture is gone too
    }

    @Test
    fun aLogoThatCantBeSaved_isDeleted_andSaysSo() = runTest {
        stores.setLogoFailure = IllegalStateException("disk full")
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        viewModel.confirmCrop(square)

        assertThat(viewModel.state.value.failed).isTrue()
        assertThat(viewModel.state.value.working).isFalse()
        assertThat(photos.stored).doesNotContain("logo-1")
    }

    @Test
    fun aLogoStillSavingWhenTheScreenCloses_isStillSaved() = runTest { // as a store's save (#65)
        val gate = CompletableDeferred<Unit>()
        stores.setLogoGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel() }
        viewModel.onPicked(picked)
        viewModel.confirmCrop(square)

        owner.clear()
        gate.complete(Unit)

        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isEqualTo("logo-1")
    }

    @Test
    fun aPictureInTheCrop_isRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle(route = StoreLogo("shop"))
        viewModel(handle).onPicked(picked)

        val restored = StoreLogoViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), stores, photos)

        assertThat(restored.state.value.cropping).isEqualTo("staged-1")
    }

    @Test
    fun removingTheLogo_clearsIt() = runTest {
        stores.setLogo("shop", "logo")
        photos.stored += "logo"
        val viewModel = viewModel()

        viewModel.removeLogo()

        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isNull()
        assertThat(viewModel.state.value.store?.logo).isNull()
    }

    @Test
    fun aRemoveThatFails_saysSo_andKeepsTheLogo() = runTest {
        stores.setLogo("shop", "logo")
        photos.stored += "logo"
        stores.setLogoFailure = IllegalStateException("disk full")
        val viewModel = viewModel()

        viewModel.removeLogo()

        assertThat(viewModel.state.value.failed).isTrue()
        assertThat(viewModel.state.value.store?.logo).isEqualTo("logo")
    }

    @Test
    fun anotherStoresLogo_isLeftAlone() = runTest {
        val viewModel = viewModel()

        viewModel.onPicked(picked)
        viewModel.confirmCrop(square)

        assertThat(stores.stores.value.single { it.id == "other" }.logo).isNull()
    }

    private fun viewModel(storeId: String = "shop") = viewModel(SavedStateHandle(route = StoreLogo(storeId)))

    private fun viewModel(handle: SavedStateHandle) = StoreLogoViewModel(handle, stores, photos)
}
