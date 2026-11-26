package com.bdaniel.loyscan

import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.domain.LogoTreatment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.keep
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Reads its route (`CustomStore.storeId`), which needs a Bundle, so it runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
class CustomStoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-22T10:00:00Z")
    private val stores = FakeStoreRepository(listOf(aStore(id = "existing", name = "Existing Store")))
    private val cards = FakeLoyaltyCardRepository()
    private val photos = FakeCardPhotos()

    @Test
    fun theForm_startsWithNoName_theFirstColourAndCode128_andCantBeCreated() = runTest {
        viewModel().state.test {
            val state = awaitItem()
            assertThat(state.name).isEmpty()
            assertThat(state.color).isEqualTo(CustomStoreViewModel.COLORS.first().hex)
            assertThat(state.format).isEqualTo(BarcodeFormat.CODE_128)
            assertThat(state.canSave).isFalse()
        }
    }

    @Test
    fun theFormats_areTheOnesACustomStoreCanPick() { // #47
        assertThat(CustomStoreViewModel.FORMATS).isEqualTo(BarcodeFormat.forCustomStores)
    }

    @Test
    fun aName_letsTheStoreBeCreated_butABlankOneDoesNot() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            skipItems(1)

            viewModel.onNameChange("My Shop")
            assertThat(awaitItem().canSave).isTrue()

            viewModel.onNameChange("   ")
            assertThat(awaitItem().canSave).isFalse()
        }
    }

    @Test
    fun create_savesACustomStoreWithTheTrimmedName_colourAndFormat_andNamesIt() = runTest {
        val viewModel = viewModel()
        val color = CustomStoreViewModel.COLORS[2].hex

        viewModel.onNameChange("  My Shop ")
        viewModel.onColorChange(color)
        viewModel.onFormatChange(BarcodeFormat.EAN_13)

        viewModel.state.test {
            skipItems(1)
            viewModel.save()

            val created = expectMostRecentItem().createdStoreId
            val saved = stores.stores.value.single { it.id == created }
            assertThat(UUID.fromString(saved.id).toString()).isEqualTo(saved.id)
            assertThat(saved).isEqualTo(
                aStore(
                    id = saved.id,
                    name = "My Shop",
                    color = color,
                    logo = null,
                    aliases = emptyList(),
                    countryCodes = emptyList(),
                    defaultBarcodeFormat = BarcodeFormat.EAN_13,
                    cardNumberRule = null,
                    isCustom = true,
                    updatedAt = now,
                )
            )
        }
    }

    @Test
    fun create_savesTheStoreOnce() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange("My Shop")

        viewModel.save()
        viewModel.save()

        assertThat(stores.stores.value.filter { it.isCustom }).hasSize(1)
    }

    @Test
    fun aSecondCreateWhileTheFirstIsSaving_savesNothing() = runTest { // #47 review: a double tap
        val viewModel = viewModel()
        viewModel.onNameChange("My Shop")
        val gate = CompletableDeferred<Unit>()
        stores.addGate = gate

        viewModel.save()
        viewModel.save()
        gate.complete(Unit)

        assertThat(stores.stores.value.filter { it.isCustom }).hasSize(1)
    }

    @Test
    fun create_withoutAName_savesNothing() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange(" ")

        viewModel.save()

        assertThat(stores.stores.value.filter { it.isCustom }).isEmpty()
    }

    @Test
    fun aFailedCreate_saysSo_andCanBeTriedAgain() = runTest {
        val viewModel = viewModel()
        stores.addFailure = IllegalStateException("insert failed")

        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange("My Shop")
            skipItems(1)

            viewModel.save()
            val failed = expectMostRecentItem()
            assertThat(failed.saveFailed).isTrue()
            assertThat(failed.createdStoreId).isNull()
            assertThat(failed.canSave).isTrue()

            stores.addFailure = null
            viewModel.save()
            val created = expectMostRecentItem()
            assertThat(created.saveFailed).isFalse()
            assertThat(created.createdStoreId).isNotNull()
        }
    }

    @Test
    fun theNameColourAndFormat_areRestoredAfterProcessDeath() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).apply {
            onNameChange("My Shop")
            onColorChange(CustomStoreViewModel.COLORS[1].hex)
            onFormatChange(BarcodeFormat.QR_CODE)
        }

        viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })).state.test {
            val state = awaitItem()
            assertThat(state.name).isEqualTo("My Shop")
            assertThat(state.color).isEqualTo(CustomStoreViewModel.COLORS[1].hex)
            assertThat(state.format).isEqualTo(BarcodeFormat.QR_CODE)
        }
    }

    @Test
    fun editingAStore_startsAsTheStore() = runTest { // #53
        stores.stores.value += custom

        editViewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.editing).isTrue()
            assertThat(state.ready).isTrue()
            assertThat(state.name).isEqualTo("My Shop")
            assertThat(state.color).isEqualTo(CustomStoreViewModel.COLORS[4].hex)
            assertThat(state.format).isEqualTo(BarcodeFormat.EAN_13)
            assertThat(state.canSave).isTrue()
        }
    }

    @Test
    fun editingAStore_showsItsLogo() = runTest { // #151
        photos.stored += "logo-1.png"
        stores.stores.value += custom.copy(logo = "logo-1.png")

        editViewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.logo).isEqualTo("logo-1.png")
            assertThat(state.storeId).isEqualTo("custom")
        }
    }

    @Test
    fun editingAStore_showsHowItsLogoIsDrawn() = runTest { // #240: the form's logo slot draws it as its tiles do
        val treatment = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF")
        photos.stored += "logo-1.png"
        stores.stores.value += custom.copy(logo = "logo-1.png", logoTreatment = treatment)

        editViewModel().state.test {
            assertThat(expectMostRecentItem().logoTreatment).isEqualTo(treatment)
        }
    }

    @Test
    fun aLogoWhoseFileIsMissing_isNoLogo() = runTest { // #151
        stores.stores.value += custom.copy(logo = "logo-1.png")

        editViewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.ready).isTrue()
            assertThat(state.logo).isNull()
        }
    }

    @Test
    fun aLogoSetWhileTheFormIsOpen_isShown() = runTest { // #151: on the logo screen, opened from the form
        stores.stores.value += custom
        val viewModel = editViewModel()

        viewModel.state.test {
            assertThat(expectMostRecentItem().logo).isNull()
            photos.stored += "logo-1.png"
            stores.setLogo("custom", "logo-1.png")

            assertThat(awaitItem().logo).isEqualTo("logo-1.png")
        }
    }

    @Test
    fun aNewStore_hasNoLogo() = runTest { // #151: its logo is set after creating, through Edit
        viewModel().state.test {
            val state = expectMostRecentItem()
            assertThat(state.logo).isNull()
            assertThat(state.storeId).isNull()
        }
    }

    @Test
    fun savingAnEdit_keepsTheStoresLogo() = runTest { // #151
        photos.stored += "logo-1.png"
        stores.stores.value += custom.copy(logo = "logo-1.png")
        val viewModel = editViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange("My Gym")
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(stores.stores.value.single { it.id == "custom" }.logo).isEqualTo("logo-1.png")
    }

    @Test
    fun savingAnEdit_writesTheNewNameColourAndFormat_andSaysItIsSaved() = runTest { // #53
        stores.stores.value += custom
        val viewModel = editViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange(" My Gym ")
            viewModel.onColorChange(CustomStoreViewModel.COLORS[1].hex)
            viewModel.onFormatChange(BarcodeFormat.QR_CODE)
            viewModel.save()

            val state = expectMostRecentItem()
            assertThat(state.saved).isTrue()
            assertThat(state.createdStoreId).isNull()
            assertThat(stores.stores.value.single { it.id == custom.id }).isEqualTo(
                custom.copy(
                    name = "My Gym",
                    color = CustomStoreViewModel.COLORS[1].hex,
                    defaultBarcodeFormat = BarcodeFormat.QR_CODE,
                    updatedAt = now,
                )
            )
            assertThat(stores.stores.value).hasSize(2)
        }
    }

    @Test
    fun aFailedEdit_saysSo_andCanBeTriedAgain() = runTest { // #53
        stores.stores.value += custom
        stores.updateFailure = IllegalStateException("update failed")
        val viewModel = editViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange("My Gym")
            viewModel.save()
            val failed = expectMostRecentItem()
            assertThat(failed.saveFailed).isTrue()
            assertThat(failed.saved).isFalse()
            assertThat(failed.canSave).isTrue()

            stores.updateFailure = null
            viewModel.save()
            assertThat(expectMostRecentItem().saved).isTrue()
            assertThat(stores.stores.value.single { it.id == custom.id }.name).isEqualTo("My Gym")
        }
    }

    @Test
    fun editingAStoreThatIsGone_saysSo_andCantBeSaved() = runTest { // #53
        val viewModel = editViewModel()

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertThat(state.storeGone).isTrue()
            assertThat(state.ready).isFalse()
            assertThat(state.canSave).isFalse()
            viewModel.save()
            assertThat(stores.stores.value).hasSize(1)
        }
    }

    @Test
    fun aCatalogueStore_cantBeEdited() = runTest { // #53
        val viewModel = editViewModel("existing")

        viewModel.state.test {
            assertThat(expectMostRecentItem().storeGone).isTrue()
            viewModel.onNameChange("Renamed")
            viewModel.save()
            assertThat(expectMostRecentItem().canSave).isFalse()
            assertThat(stores.stores.value.single().name).isEqualTo("Existing Store")
        }
    }

    @Test
    fun anEdit_isRestoredAfterProcessDeath() = runTest { // #53
        stores.stores.value += custom
        val handle = SavedStateHandle(route = CustomStore(custom.id))
        viewModel(handle).onNameChange("My Gym")

        viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })).state.test {
            val state = expectMostRecentItem()
            assertThat(state.name).isEqualTo("My Gym")
            assertThat(state.format).isEqualTo(BarcodeFormat.EAN_13)
        }
    }

    @Test
    fun editingAStore_saysHowManyCardsItHas() = runTest { // #53
        stores.stores.value += custom
        cards.cards.value = listOf(
            aLoyaltyCard(id = cardId(1), store = custom),
            aLoyaltyCard(id = cardId(2), store = custom),
            aLoyaltyCard(id = cardId(3), store = stores.stores.value.first()),
        )

        editViewModel().state.test {
            assertThat(expectMostRecentItem().cardCount).isEqualTo(2)
        }
    }

    @Test
    fun delete_removesTheStore_andSaysItIsDeleted() = runTest { // #53
        stores.stores.value += custom
        val viewModel = editViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.delete()

            val state = expectMostRecentItem()
            assertThat(state.deletion).isEqualTo(Deletion.DELETED)
            assertThat(state.storeGone).isFalse()
            assertThat(stores.stores.value.map { it.id }).containsExactly("existing")
        }
    }

    @Test
    fun aSecondDeleteWhileTheFirstIsDeleting_deletesOnce() = runTest { // #53: a double tap
        stores.stores.value += custom
        val viewModel = editViewModel()
        val gate = CompletableDeferred<Unit>()
        stores.deleteGate = gate

        viewModel.state.test {
            skipItems(1)
            viewModel.delete()
            viewModel.delete()
            gate.complete(Unit)
            viewModel.delete()

            assertThat(expectMostRecentItem().deletion).isEqualTo(Deletion.DELETED)
            assertThat(stores.deleteCalls).isEqualTo(1)
        }
    }

    @Test
    fun aStoreGoneBeforeItsDeleteReturns_isNotSaidToBeGone() = runTest { // #53 review: Room may re-emit first
        stores.stores.value += custom
        val viewModel = editViewModel()
        val gate = CompletableDeferred<Unit>()
        stores.deleteGate = gate

        viewModel.state.test {
            skipItems(1)
            viewModel.delete()
            stores.stores.value = stores.stores.value.filterNot { it.id == custom.id }

            val state = expectMostRecentItem()
            assertThat(state.deletion).isEqualTo(Deletion.DELETING)
            assertThat(state.storeGone).isFalse()
            gate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedDelete_saysSo_canBeTriedAgain_andIsForgottenWhenDismissed() = runTest { // #53
        stores.stores.value += custom
        stores.deleteFailure = IllegalStateException("delete failed")
        val viewModel = editViewModel()

        viewModel.state.test {
            skipItems(1)
            viewModel.delete()
            assertThat(expectMostRecentItem().deletion).isEqualTo(Deletion.FAILED)

            viewModel.dismissDeletion()
            assertThat(expectMostRecentItem().deletion).isEqualTo(Deletion.NONE)

            stores.deleteFailure = null
            viewModel.delete()
            assertThat(expectMostRecentItem().deletion).isEqualTo(Deletion.DELETED)
        }
    }

    @Test
    fun aNewStoreStillSavingWhenTheFormCloses_isStillSaved() = runTest { // #65: Back pressed during the save
        val gate = CompletableDeferred<Unit>()
        stores.addGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { viewModel() }
        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange("My Gym")
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(stores.stores.value.map { it.name }).containsExactly("Existing Store", "My Gym")
    }

    @Test
    fun anEditStillSavingWhenTheFormCloses_isStillSaved() = runTest { // #65
        stores.stores.value += custom
        val gate = CompletableDeferred<Unit>()
        stores.updateGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { editViewModel() }
        viewModel.state.test {
            skipItems(1)
            viewModel.onNameChange("My Gym")
            viewModel.save()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(stores.stores.value.single { it.id == custom.id }.name).isEqualTo("My Gym")
    }

    @Test
    fun aDeleteStillRunningWhenTheFormCloses_isStillDone() = runTest { // #65
        stores.stores.value += custom
        val gate = CompletableDeferred<Unit>()
        stores.deleteGate = gate
        val owner = ViewModelStore()
        val viewModel = owner.keep { editViewModel() }
        viewModel.state.test {
            skipItems(1)
            viewModel.delete()
            cancelAndIgnoreRemainingEvents()
        }

        owner.clear()
        gate.complete(Unit)

        assertThat(stores.stores.value.map { it.id }).containsExactly("existing")
    }

    @Test
    fun aNewStore_orOneThatIsGone_isNotDeleted() = runTest { // #53
        viewModel().delete()
        editViewModel("existing").delete()

        assertThat(stores.deleteCalls).isEqualTo(0)
    }

    private val custom = aStore(
        id = "custom",
        name = "My Shop",
        color = CustomStoreViewModel.COLORS[4].hex,
        countryCodes = emptyList(),
        defaultBarcodeFormat = BarcodeFormat.EAN_13,
        isCustom = true,
    )

    @Test
    fun anUntouchedForm_hasNoChanges_creatingOrEditing() = runTest { // #209
        stores.stores.value += custom

        viewModel().state.test { assertThat(expectMostRecentItem().hasChanges).isFalse() }
        editViewModel().state.test { assertThat(expectMostRecentItem().hasChanges).isFalse() }
    }

    @Test
    fun theNameColourOrFormat_isAChange_whenItDiffersFromTheStoreOrTheDefaults() = runTest { // #209
        stores.stores.value += custom
        val edits: List<Pair<String, CustomStoreViewModel.() -> Unit>> = listOf(
            "name" to { onNameChange("My Gym") },
            "colour" to { onColorChange(CustomStoreViewModel.COLORS[1].hex) },
            "format" to { onFormatChange(BarcodeFormat.QR_CODE) },
        )

        for ((what, edit) in edits) {
            for (form in listOf(viewModel(), editViewModel())) {
                form.state.test {
                    skipItems(1)
                    form.edit()
                    assertWithMessage(what).that(expectMostRecentItem().hasChanges).isTrue()
                }
            }
        }
    }

    @Test
    fun aValueChangedAndChangedBack_orOnlyPaddedWithSpaces_isNoChange() = runTest { // #209
        stores.stores.value += custom
        val form = editViewModel()

        form.state.test {
            skipItems(1)
            form.onNameChange("My Gym")
            form.onNameChange(" My Shop ")
            form.onColorChange(CustomStoreViewModel.COLORS[1].hex)
            form.onColorChange(CustomStoreViewModel.COLORS[4].hex)
            form.onFormatChange(BarcodeFormat.QR_CODE)
            form.onFormatChange(BarcodeFormat.EAN_13)

            assertThat(expectMostRecentItem().hasChanges).isFalse()
        }
    }

    @Test
    fun aChange_isStillAChange_afterProcessDeath() = runTest { // #209
        stores.stores.value += custom
        val handle = SavedStateHandle(route = CustomStore(custom.id))
        viewModel(handle).onNameChange("My Gym")

        viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })).state.test {
            assertThat(expectMostRecentItem().hasChanges).isTrue()
        }
    }

    @Test
    fun onceSavingHasStarted_orTheStoreIsDeleting_theFormHasNoChangesToAskAbout() = runTest { // #209: Back leaves (#65)
        stores.stores.value += custom
        stores.updateGate = CompletableDeferred()
        stores.deleteGate = CompletableDeferred()
        val saving = editViewModel()
        val deleting = editViewModel()

        saving.state.test {
            skipItems(1)
            saving.onNameChange("My Gym")
            saving.save()
            assertThat(expectMostRecentItem().hasChanges).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        deleting.state.test {
            skipItems(1)
            deleting.onNameChange("My Gym")
            deleting.delete()
            assertThat(expectMostRecentItem().hasChanges).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        stores.updateGate!!.complete(Unit)
        stores.deleteGate!!.complete(Unit)
    }

    private fun editViewModel(storeId: String = custom.id) = viewModel(SavedStateHandle(route = CustomStore(storeId)))

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CustomStoreViewModel(handle, stores, cards, photos, Clock.fixed(now, ZoneOffset.UTC))
}
