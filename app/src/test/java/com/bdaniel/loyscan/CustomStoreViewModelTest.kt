package com.bdaniel.loyscan

import androidx.lifecycle.SavedStateHandle
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

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
            assertThat(state.canCreate).isFalse()
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
            assertThat(awaitItem().canCreate).isTrue()

            viewModel.onNameChange("   ")
            assertThat(awaitItem().canCreate).isFalse()
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
            viewModel.create()

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

        viewModel.create()
        viewModel.create()

        assertThat(stores.stores.value.filter { it.isCustom }).hasSize(1)
    }

    @Test
    fun aSecondCreateWhileTheFirstIsSaving_savesNothing() = runTest { // #47 review: a double tap
        val viewModel = viewModel()
        viewModel.onNameChange("My Shop")
        val gate = CompletableDeferred<Unit>()
        stores.addGate = gate

        viewModel.create()
        viewModel.create()
        gate.complete(Unit)

        assertThat(stores.stores.value.filter { it.isCustom }).hasSize(1)
    }

    @Test
    fun create_withoutAName_savesNothing() = runTest {
        val viewModel = viewModel()
        viewModel.onNameChange(" ")

        viewModel.create()

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

            viewModel.create()
            val failed = expectMostRecentItem()
            assertThat(failed.createFailed).isTrue()
            assertThat(failed.createdStoreId).isNull()
            assertThat(failed.canCreate).isTrue()

            stores.addFailure = null
            viewModel.create()
            val created = expectMostRecentItem()
            assertThat(created.createFailed).isFalse()
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

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CustomStoreViewModel(handle, stores, Clock.fixed(now, ZoneOffset.UTC))
}
