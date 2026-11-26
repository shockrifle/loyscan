package com.bdaniel.loyscan

import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aLoyaltyCard
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
    private val cards = FakeLoyaltyCardRepository()
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
    fun aPickedSvg_opensTheCrop_andTheCropBecomesTheStoresLogo() = runTest { // #211: drawn when it is copied in
        val svg = Uri.parse("content://media/picker/0/logo.svg")
        val viewModel = viewModel()

        viewModel.onPicked(svg)
        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
        viewModel.confirmCrop(square)

        assertThat(photos.staged).containsExactly(svg)
        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isEqualTo("logo-1")
        assertThat(viewModel.state.value.failed).isFalse()
    }

    @Test
    fun theCardPhotos_areTheFrontPhotosOfThisStoresCards() = runTest { // #151: a card's photo as the logo's source
        val shop = stores.stores.value.single { it.id == "shop" }
        val other = stores.stores.value.single { it.id == "other" }
        photos.stored += listOf("front-a", "front-d", "back-b", "front-o")
        cards.cards.value = listOf(
            aLoyaltyCard(id = "a", store = shop, label = "Mine", frontPhotoPath = "front-a"),
            aLoyaltyCard(id = "b", store = shop, backPhotoPath = "back-b"), // a back photo only
            aLoyaltyCard(id = "c", store = shop, frontPhotoPath = "lost"), // its file is missing
            aLoyaltyCard(id = "d", store = shop, number = "999", frontPhotoPath = "front-d"), // no label: its number
            aLoyaltyCard(id = "o", store = other, frontPhotoPath = "front-o"),
        )

        val state = viewModel().state.value

        assertThat(state.cardPhotos).containsExactly(
            CardPhotoChoice(cardId = "a", title = "Mine", photo = "front-a"),
            CardPhotoChoice(cardId = "d", title = "999", photo = "front-d"),
        ).inOrder()
    }

    @Test
    fun aCardsPhoto_opensTheCropOnACopy_andTheCropBecomesTheLogo() = runTest { // #151
        givenACardPhoto()
        val viewModel = viewModel()

        viewModel.onCardPhoto("front")
        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
        viewModel.confirmCrop(square)

        assertThat(photos.stagedPhotos).containsExactly("front")
        assertThat(photos.croppedLogos).containsExactly("staged-1" to square)
        assertThat(photos.stored).contains("front") // the card keeps its photo
        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isEqualTo("logo-1")
    }

    @Test
    fun aPhotoThatIsntOneOfThisStoresCards_isRefused_andSaysSo() = runTest { // #151: the list changed meanwhile
        photos.stored += "elsewhere"
        val viewModel = viewModel()

        viewModel.onCardPhoto("elsewhere")

        assertThat(photos.stagedPhotos).isEmpty()
        assertThat(viewModel.state.value.cropping).isNull()
        assertThat(viewModel.state.value.failed).isTrue()
    }

    @Test
    fun aCardPhotoThatCantBeCopied_saysSo() = runTest { // #151
        givenACardPhoto()
        photos.importFailure = IOException("not a picture")
        val viewModel = viewModel()

        viewModel.onCardPhoto("front")

        assertThat(viewModel.state.value.failed).isTrue()
        assertThat(viewModel.state.value.working).isFalse()
        assertThat(viewModel.state.value.cropping).isNull()
    }

    @Test
    fun whileACardPhotoIsCopied_noOtherPictureIsTaken() = runTest { // #151
        givenACardPhoto()
        val gate = CompletableDeferred<Unit>()
        photos.importGate = gate
        val viewModel = viewModel()

        viewModel.onCardPhoto("front")
        assertThat(viewModel.state.value.working).isTrue()
        viewModel.onPicked(picked)
        viewModel.onCardPhoto("front")
        gate.complete(Unit)

        assertThat(photos.stagedPhotos).containsExactly("front")
        assertThat(photos.staged).isEmpty()
        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
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

        val restored = StoreLogoViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), stores, cards, photos)

        assertThat(restored.state.value.cropping).isEqualTo("staged-1")
    }

    @Test
    fun aPictureInTheRoute_opensTheCropOnIt_andCropMakesItTheLogo() = runTest { // #151: a picture shared to LoyScan
        photos.stored += "shared"
        val viewModel = viewModel(SavedStateHandle(route = StoreLogo("shop", picture = "shared")))

        assertThat(viewModel.state.value.cropping).isEqualTo("shared")
        viewModel.confirmCrop(square)

        assertThat(photos.croppedLogos.map { it.first }).containsExactly("shared")
        assertThat(stores.stores.value.single { it.id == "shop" }.logo).isEqualTo("logo-1")
        assertThat(viewModel.state.value.cropping).isNull()
    }

    @Test
    fun aPictureInTheRoute_cancelled_isDiscarded_andNotOfferedAgain() = runTest { // #151
        photos.stored += "shared"
        val handle = SavedStateHandle(route = StoreLogo("shop", picture = "shared"))
        viewModel(handle).cancelCrop()

        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

        assertThat(photos.stored).doesNotContain("shared")
        assertThat(restored.state.value.cropping).isNull()
    }

    @Test
    fun aPictureInTheRoute_isStillInTheCrop_afterProcessDeath() = runTest { // #151
        photos.stored += "shared"
        val handle = SavedStateHandle(route = StoreLogo("shop", picture = "shared"))
        viewModel(handle)

        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

        assertThat(restored.state.value.cropping).isEqualTo("shared")
    }

    @Test
    fun aPictureInTheRoute_cropped_isNotOfferedAgain_afterProcessDeath() = runTest { // #151
        photos.stored += "shared"
        val handle = SavedStateHandle(route = StoreLogo("shop", picture = "shared"))
        viewModel(handle).confirmCrop(square)

        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))

        assertThat(restored.state.value.cropping).isNull()
        assertThat(photos.croppedLogos).hasSize(1)
    }

    @Test
    fun aPictureInTheRoute_forAStoreThatIsGone_isDiscarded_withoutACrop() = runTest { // #151
        photos.stored += "shared"
        val viewModel = viewModel(SavedStateHandle(route = StoreLogo("gone", picture = "shared")))

        assertThat(viewModel.state.value.storeGone).isTrue()
        assertThat(viewModel.state.value.cropping).isNull()
        assertThat(photos.stored).doesNotContain("shared")
    }

    @Test
    fun aPicturePickedWhileTheRoutesPictureIsChecked_staysInTheCrop_andTheRoutesIsDiscarded() = runTest { // #151 review
        photos.stored += "shared"
        val gate = CompletableDeferred<Unit>()
        photos.existsGate = gate
        val viewModel = viewModel(SavedStateHandle(route = StoreLogo("shop", picture = "shared")))

        viewModel.onPicked(picked)
        gate.complete(Unit)

        assertThat(viewModel.state.value.cropping).isEqualTo("staged-1")
        assertThat(photos.stored).doesNotContain("shared")
    }

    @Test
    fun aPictureInTheRoute_whoseFileIsMissing_opensNoCrop() = runTest { // #151: e.g. swept while the app was away
        val viewModel = viewModel(SavedStateHandle(route = StoreLogo("shop", picture = "shared")))

        assertThat(viewModel.state.value.ready).isTrue()
        assertThat(viewModel.state.value.cropping).isNull()
    }

    @Test
    fun choosingSingleColour_onADarkBrandColour_drawsTheLogoWhite() = runTest { // #240: whichever contrasts more
        val viewModel = viewModelWithALogo(color = "#336699")

        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"))
        assertThat(viewModel.state.value.store?.logoTreatment).isEqualTo(shop().logoTreatment)
    }

    @Test
    fun choosingSingleColour_onALightBrandColour_drawsTheLogoBlack() = runTest { // #240
        val viewModel = viewModelWithALogo(color = "#FFD500")

        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#000000"))
    }

    @Test
    fun choosingAColour_drawsTheLogoInIt() = runTest { // #240
        val viewModel = viewModelWithALogo()
        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)

        viewModel.onLogoTint("#C62828")

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#C62828"))
        assertThat(viewModel.state.value.store?.logoTreatment?.tint).isEqualTo("#C62828")
    }

    @Test
    fun choosingAsIs_drawsTheLogoAsIs_andKeepsItsOutline() = runTest { // #240: the outline goes with any style
        val viewModel = viewModelWithALogo()
        stores.setLogoTreatment("shop", LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#C62828", outline = true))

        viewModel.onLogoStyle(null)

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment(outline = true))
    }

    @Test
    fun aColour_whileTheLogoIsAsIs_isNotSaved() = runTest { // #240: the swatches show only for another style
        val viewModel = viewModelWithALogo()

        viewModel.onLogoTint("#C62828")

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun withoutALogo_aStyleIsNotSaved() = runTest { // #240: the controls show only with a logo
        val viewModel = viewModel()

        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)

        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun aStyleThatCantBeSaved_saysSo() = runTest { // #240
        val viewModel = viewModelWithALogo()
        stores.setLogoTreatmentFailure = IllegalStateException("disk full")

        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)

        assertThat(viewModel.state.value.styleFailed).isTrue()
        assertThat(viewModel.state.value.failed).isFalse() // not "the picture couldn't be used"
        assertThat(viewModel.state.value.working).isFalse()
        assertThat(shop().logoTreatment).isEqualTo(LogoTreatment.AS_IS)
    }

    @Test
    fun aStyleSavedWhileAPictureIsCopiedIn_keepsTheScreenWorking() = runTest { // review of PR #244
        val viewModel = viewModelWithALogo()
        val styleSaved = CompletableDeferred<Unit>()
        stores.setLogoTreatmentGate = styleSaved
        photos.importGate = CompletableDeferred()
        viewModel.onLogoStyle(LogoStyle.SINGLE_COLOUR)
        viewModel.onPicked(picked)

        styleSaved.complete(Unit)

        assertThat(viewModel.state.value.working).isTrue()
        assertThat(shop().logoTreatment.style).isEqualTo(LogoStyle.SINGLE_COLOUR)
    }

    private suspend fun viewModelWithALogo(color: String = "#336699"): StoreLogoViewModel {
        stores.stores.value = stores.stores.value.map { if (it.id == "shop") it.copy(color = color) else it }
        stores.setLogo("shop", "logo")
        photos.stored += "logo"
        return viewModel()
    }

    private fun shop() = stores.stores.value.single { it.id == "shop" }

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

    private fun viewModel(handle: SavedStateHandle) = StoreLogoViewModel(handle, stores, cards, photos)

    private fun givenACardPhoto() {
        photos.stored += "front"
        cards.cards.value = listOf(
            aLoyaltyCard(id = "card", store = stores.stores.value.single { it.id == "shop" }, frontPhotoPath = "front"),
        )
    }
}
