package com.bdaniel.loyscan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeStoreRepository
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aStore
import androidx.navigation.testing.invoke
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CustomStoreScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stores = FakeStoreRepository()
    private val cards = FakeLoyaltyCardRepository()
    private val scanned = mutableListOf<String>()
    private var backs = 0

    @Test
    fun create_isDisabledUntilTheStoreHasAName() {
        setScreen()

        composeRule.onNodeWithText("Create").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Create").performScrollTo().assertIsEnabled()
    }

    @Test
    fun theFirstColourAndCode128_arePickedAtFirst() {
        setScreen()

        composeRule.onNodeWithContentDescription(CustomStoreViewModel.COLORS[0].name).assertIsSelected()
        composeRule.onNodeWithContentDescription(CustomStoreViewModel.COLORS[1].name).assertIsNotSelected()
        composeRule.onNodeWithText(BarcodeFormat.CODE_128.displayName).performScrollTo().assertIsSelected()
    }

    @Test
    fun everyFormatACustomStoreCanPick_isOffered() { // #47
        setScreen()

        CustomStoreViewModel.FORMATS.forEach {
            composeRule.onNodeWithText(it.displayName).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText(BarcodeFormat.GS1_128.displayName).assertDoesNotExist()
    }

    @Test
    fun creatingAStore_savesItWithTheChosenColourAndFormat_andOpensTheScannerForIt() {
        setScreen()
        val color = CustomStoreViewModel.COLORS[3]

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithContentDescription(color.name).performClick()
        composeRule.onNodeWithText(BarcodeFormat.EAN_13.displayName).performScrollTo().performClick()
        composeRule.onNodeWithText("Create").performScrollTo().performClick()

        composeRule.runOnIdle {
            val saved = stores.stores.value.single()
            assertThat(saved.name).isEqualTo("My Shop")
            assertThat(saved.color).isEqualTo(color.hex)
            assertThat(saved.defaultBarcodeFormat).isEqualTo(BarcodeFormat.EAN_13)
            assertThat(scanned).containsExactly(saved.id)
        }
    }

    @Test
    fun aFailedCreate_saysSo() {
        stores.addFailure = IllegalStateException("insert failed")
        setScreen()

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput("My Shop")
        composeRule.onNodeWithText("Create").performScrollTo().performClick()

        composeRule.onNodeWithText("The store couldn't be saved. Try again.").performScrollTo().assertIsDisplayed()
        assertThat(scanned).isEmpty()
    }

    @Test
    fun back_goesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun editingAStore_showsItsValues_andSavingItReturns() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", color = CustomStoreViewModel.COLORS[2].hex, isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithText("Edit store").assertIsDisplayed()
        composeRule.onNodeWithText("My Shop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(CustomStoreViewModel.COLORS[2].name).assertIsSelected()
        composeRule.onNodeWithText("Create").assertDoesNotExist()
        composeRule.onNodeWithText(BarcodeFormat.QR_CODE.displayName).performScrollTo().performClick()
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertThat(stores.stores.value.single().defaultBarcodeFormat).isEqualTo(BarcodeFormat.QR_CODE)
            assertThat(backs).isEqualTo(1)
            assertThat(scanned).isEmpty()
        }
    }

    @Test
    fun editingAStoreThatIsGone_saysSo_withoutAForm() { // #53
        setScreen(CustomStore("gone"))

        composeRule.onNodeWithText("This store is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun deletingAStore_saysHowManyCardsGoWithIt_andReturnsOnceDeleted() { // #53
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), store = store), aLoyaltyCard(id = cardId(2), store = store))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertIsDisplayed()
        composeRule.onNodeWithText("Its 2 cards are deleted with it.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle {
            assertThat(stores.stores.value).isEmpty()
            assertThat(backs).isEqualTo(1)
        }
    }

    @Test
    fun theDeleteDialog_namesTheStoreAsSaved_notAnUnsavedRename() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithTag(CUSTOM_STORE_NAME_TAG).performTextInput(" Renamed")
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertIsDisplayed()
    }

    @Test
    fun deletingAStoreWithOneCard_saysSo() { // #53
        val store = aStore(id = "custom", name = "My Shop", isCustom = true)
        stores.stores.value = listOf(store)
        cards.cards.value = listOf(aLoyaltyCard(store = store))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Its card is deleted with it.").assertIsDisplayed()
    }

    @Test
    fun deletingAStoreWithoutCards_saysSo_andCancelKeepsIt() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("It has no cards.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete My Shop?").assertDoesNotExist()
        composeRule.runOnIdle {
            assertThat(stores.stores.value).hasSize(1)
            assertThat(backs).isEqualTo(0)
        }
    }

    @Test
    fun aDeleteThatFails_saysSo_inTheDialog() { // #53
        stores.stores.value = listOf(aStore(id = "custom", name = "My Shop", isCustom = true))
        stores.deleteFailure = IllegalStateException("delete failed")
        setScreen(CustomStore("custom"))

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithText("The store couldn't be deleted. Try again.").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(backs).isEqualTo(0) }
    }

    @Test
    fun aNewStore_hasNoDeleteAction() { // #53
        setScreen()

        composeRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    private fun setScreen(route: CustomStore = CustomStore()) {
        val viewModel = CustomStoreViewModel(SavedStateHandle(route = route), stores, cards, Clock.fixed(SOME_INSTANT, ZoneOffset.UTC))
        val directions = CustomStoreDirections(onCreated = { scanned += it }, toBack = { backs++ })
        composeRule.setContent { CustomStoreScreen(directions = directions, viewModel = viewModel) }
    }
}
