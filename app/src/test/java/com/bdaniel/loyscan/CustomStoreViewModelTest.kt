package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.MainDispatcherRule
import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
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

    private val custom = aStore(
        id = "custom",
        name = "My Shop",
        color = CustomStoreViewModel.COLORS[4].hex,
        countryCodes = emptyList(),
        defaultBarcodeFormat = BarcodeFormat.EAN_13,
        isCustom = true,
    )

    private fun editViewModel(storeId: String = custom.id) = viewModel(SavedStateHandle(route = CustomStore(storeId)))

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CustomStoreViewModel(handle, stores, Clock.fixed(now, ZoneOffset.UTC))
}
