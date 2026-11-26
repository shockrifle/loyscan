package com.bdaniel.loyscan

import com.bdaniel.loyscan.testing.writeSolidPng
import com.bdaniel.loyscan.testing.writeSquareOnClearPng
import com.bdaniel.loyscan.testing.ringPixel
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.domain.LogoStyle
import com.bdaniel.loyscan.ui.CARD_TILE_LOGO_TAG
import com.bdaniel.loyscan.ui.CARD_TILE_PHOTO_TAG
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.lifecycle.SavedStateHandle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.testing.FakeCardPhotos
import com.bdaniel.loyscan.testing.ShownTexts
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeUserPreferencesRepository
import com.bdaniel.loyscan.testing.WAIT_TIMEOUT_MS
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.bdaniel.loyscan.testing.writeOnePixelPng
import com.bdaniel.loyscan.di.CardPhotosModule
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.test.onAllNodesWithText
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.bdaniel.loyscan.testing.assertTextFitsAndDoesNotOverlap
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.onAllNodesWithTag
import java.io.File
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example of a Compose screen test on Robolectric. The screen gets a ViewModel built on a fake repository and
 * recording directions; shared-transition scopes come from a real `SharedTransitionLayout`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
class ListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingACard_navigatesToItWithItsStoreColor() {
        val card = aLoyaltyCard(id = cardId(5), store = aStore(name = "Test Store", color = "#AA0000"))
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(listOf(card)), FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos())
        var navigatedTo: Pair<String, String>? = null
        val directions = ListDirections(toCard = { id, color -> navigatedTo = id to color; true }, toAddCard = {}, toEditCard = { _, _ -> }, toAbout = {})

        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                }
            }
        }
        tile("Test Store").performClick()

        assertThat(navigatedTo).isEqualTo(cardId(5) to "#AA0000")
    }

    @Test
    fun showsATileForEachCard() {
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "First Store")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Second Store")))

        tile("First Store").assertIsDisplayed()
        tile("Second Store").assertIsDisplayed()
    }

    @Test
    fun aTile_showsTheStoresInitials_andTheCardsLabel() {
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = "Work car"))

        composeRule.onNodeWithText("SC").assertIsDisplayed()
        composeRule.onNodeWithText("Work car").assertIsDisplayed()
        composeRule.onNodeWithText("Shell ClubSmart").assertDoesNotExist()
    }

    @Test
    fun aTile_isReadOutAsTheStoreName_andTheLabel() { // review of #7; #183: the store, though the tile doesn't show it
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Shell ClubSmart"), label = "Work car"), aLoyaltyCard(id = cardId(2), store = aStore(name = "Penny"), label = null))

        // TalkBack reads a merged node's content description in place of its text. The tiles, sorted by name.
        val tiles = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction()))
        tiles.assertCountEquals(2)
        tiles[0].assertContentDescriptionEquals("Penny")
        tiles[1].assertContentDescriptionEquals("Shell ClubSmart, Work car")
    }

    @Test
    fun aTilesInitials_andLabel_doNotOverlap_atALargeFontScale() { // review of #7
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = "Work car"), fontScale = 2f)

        val initials = composeRule.onNodeWithText("SC", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("Work car", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tile = composeRule.onNodeWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        // Robolectric's text widths are near zero, so only the vertical layout can be checked.
        assertThat(initials.height).isGreaterThan(0f) // shrunk to fit, not squeezed out (#183)
        assertThat(initials.bottom).isAtMost(label.top)
        assertThat(tile.contains(label.bottomRight - Offset(1f, 1f))).isTrue()
    }

    @Test
    fun aCardWithAFrontPhoto_showsItOnItsTile_inPlaceOfTheInitials() { // #10
        storedPhoto("front")
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = "Work car", frontPhotoPath = "front", showPhotoOnList = true), photos = FakeCardPhotos("front"))
        // Loaded, so a failed load (which shows the initials) would have shown by now.
        val loader = EntryPointAccessors.fromApplication(composeRule.activity, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("SC").assertDoesNotExist()
        composeRule.onNodeWithText("Work car").assertIsDisplayed()
        composeRule.onNodeWithText("Work car").assertContentDescriptionEquals("Shell ClubSmart, Work car")
    }

    @Test
    fun aFrontPhotoThatCantBeLoaded_showsTheInitials() { // review of #10: not a brand-colour box with no initials
        // The fake says the photo exists, but the app's loader finds no file to decode.
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), frontPhotoPath = "broken", showPhotoOnList = true), photos = FakeCardPhotos("broken"))

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper() // the loader's result comes on the main looper
            composeRule.onAllNodesWithText("SC").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCardWhoseStoreHasALogo_showsItOnItsTile_inPlaceOfTheInitials() { // #151
        storedPhoto("logo")
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "logo"), label = "Work car"), photos = FakeCardPhotos("logo"))
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("SC").assertDoesNotExist()
        composeRule.onNodeWithText("Work car").assertIsDisplayed()
    }

    @Test
    fun aFrontPhoto_takesPrecedenceOverTheStoresLogo() { // #151: the card's own photo is still its tile
        storedPhoto("front")
        storedPhoto("logo")
        setScreen(
            aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "logo"), frontPhotoPath = "front", showPhotoOnList = true),
            photos = FakeCardPhotos("front", "logo"),
        )
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLogoThatCantBeLoaded_showsTheInitials() { // #151: not an empty brand-colour box
        // The fake says the logo exists, but the app's loader finds no file to decode.
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "broken")), photos = FakeCardPhotos("broken"))

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper() // the loader's result comes on the main looper
            composeRule.onAllNodesWithText("SC").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLogoWhoseFileIsMissing_showsTheInitials() { // #151, as a missing photo (#89)
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "lost")), photos = FakeCardPhotos())

        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SC").assertIsDisplayed()
    }

    @Test
    fun aCardWithAFrontPhoto_notShownOnMyCards_showsTheInitials() { // #184: the user's choice, off by default
        storedPhoto("front")
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), frontPhotoPath = "front"), photos = FakeCardPhotos("front"))

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SC").assertIsDisplayed()
    }

    @Test
    fun aCardWithAFrontPhoto_notShownOnMyCards_showsItsStoresLogo() { // #184
        storedPhoto("front")
        storedPhoto("logo")
        setScreen(
            aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "logo"), frontPhotoPath = "front"),
            photos = FakeCardPhotos("front", "logo"),
        )
        waitForAnImageToLoad()

        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aCardSetToShowItsPhoto_withoutAFrontPhoto_showsTheInitials() { // #184
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), showPhotoOnList = true), photos = FakeCardPhotos())

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SC").assertIsDisplayed()
    }

    @Test
    fun aCardWithOnlyABackPhoto_showsTheInitialsOnItsTile() { // #10: the back is the card screen's
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), backPhotoPath = "back"), photos = FakeCardPhotos("back"))

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SC").assertIsDisplayed()
    }

    @Test
    fun aTileWhoseFrontPhotoFileIsMissing_showsTheInitials() { // #89: not an empty image
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), frontPhotoPath = "lost", showPhotoOnList = true), photos = FakeCardPhotos())

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("SC").assertIsDisplayed()
    }

    @Test
    fun aTileWithoutALabel_showsOnlyTheInitials() { // #183: not the store name
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = null))

        assertThat(textsOnTheTiles()).containsExactly("SC")
    }

    @Test
    fun aLogoTileWithoutALabel_showsNoText() { // #183: the logo says which store it is
        storedPhoto("logo")
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart", logo = "logo"), label = null), photos = FakeCardPhotos("logo"))
        waitForAnImageToLoad() // a logo that failed to load would show the initials by now

        composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).assertIsDisplayed()
        assertThat(textsOnTheTiles()).isEmpty()
    }

    @Test
    fun aPhotoTileWithoutALabel_showsNoText() { // #183: the photo says which store it is
        storedPhoto("front")
        setScreen(aLoyaltyCard(store = aStore(name = "Shell ClubSmart"), label = null, frontPhotoPath = "front", showPhotoOnList = true), photos = FakeCardPhotos("front"))
        waitForAnImageToLoad() // a photo that failed to load would show the initials by now

        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertIsDisplayed()
        assertThat(textsOnTheTiles()).isEmpty()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aPhotoTile_hasTheDarkLabelBand_onlyWithALabel() { // #183: no empty band over an unlabelled photo
        storedPhoto("front")
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), label = "Work", frontPhotoPath = "front", showPhotoOnList = true),
            aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"), label = null, frontPhotoPath = "front", showPhotoOnList = true),
            photos = FakeCardPhotos("front"),
        )
        waitForAnImageToLoad()

        val (labelled, unlabelled) = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map {
            composeRule.onNode(SemanticsMatcher("tile ${it.id}") { node -> node.id == it.id }, useUnmergedTree = true).captureToImage().toPixelMap()
        }
        // Right of the short label, in the band's bottom padding, clear of the rounded corner; against the tile's middle.
        fun bottom(tile: PixelMap) = tile[tile.width * 7 / 8, tile.height - 3]
        fun middle(tile: PixelMap) = tile[tile.width * 7 / 8, tile.height / 2]
        assertThat(colourDistance(bottom(unlabelled), middle(unlabelled))).isLessThan(0.02f) // a scaled picture's edge can differ by a step
        assertThat(bottom(labelled).luminance()).isLessThan(middle(labelled).luminance())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aLogoTile_hasTheDarkLabelBand_underItsLabel_anInitialsTileDoesNot() { // review of #183: the label is over the logo
        storedPhoto("logo")
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple", logo = "logo"), label = "Work"),
            aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "Berry"), label = "Home"),
            photos = FakeCardPhotos("logo"),
        )
        waitForAnImageToLoad()

        val (logoTile, initialsTile) = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map {
            composeRule.onNode(SemanticsMatcher("tile ${it.id}") { node -> node.id == it.id }, useUnmergedTree = true).captureToImage().toPixelMap()
        }
        // Right of the short label, in the band's bottom padding; against the brand colour left of the logo or initials.
        fun bottom(tile: PixelMap) = tile[tile.width * 7 / 8, tile.height - 3]
        fun brandColour(tile: PixelMap) = tile[tile.width / 16, tile.height / 2]
        assertThat(bottom(logoTile).luminance()).isLessThan(brandColour(logoTile).luminance() * 0.6f)
        assertThat(colourDistance(bottom(initialsTile), brandColour(initialsTile))).isLessThan(0.02f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp") // a common phone width
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring: the label's height decides whether the initials fit
    fun aTilesInitials_areCentredInTheTile_withALabelOrWithout() { // #183, as a photo: the label is over the tile
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), label = "Work"), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"), label = null))

        val tiles = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        listOf("A", "B").forEachIndexed { i, initials ->
            val shown = composeRule.onNodeWithText(initials, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertThat(shown.center.y).isWithin(1f).of(tiles[i].center.y)
        }
    }

    @Test
    fun aStoresLogo_isCentredInTheTile_atThreeFifthsOfItsHeight_withALabelOrWithout() { // #151's 60%; #183
        storedPhoto("logo")
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple", logo = "logo"), label = "Work"),
            aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "Berry", logo = "logo"), label = null),
            photos = FakeCardPhotos("logo"),
        )
        waitForAnImageToLoad()

        val tiles = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val logos = composeRule.onAllNodesWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertThat(logos).hasSize(2)
        logos.zip(tiles).forEach { (logo, tile) ->
            assertThat(logo.center.y).isWithin(1f).of(tile.center.y)
            assertThat(logo.height).isWithin(1f).of(tile.height * 0.6f)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun tiles_areInset16dpFromTheScreenEdges_with12dpGaps() { // #108
        val cards = (1..4).map { aLoyaltyCard(id = cardId(it), store = aStore(name = "Store $it")) }
        setScreen(*cards.toTypedArray())

        val tiles = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dp = composeRule.density.density
        val (topLeft, topRight, bottomLeft) = Triple(tiles[0], tiles[1], tiles[2])

        assertThat(topLeft.left / dp).isWithin(1f).of(16f)
        assertThat((screen.right - topRight.right) / dp).isWithin(1f).of(16f)
        assertThat((topRight.left - topLeft.right) / dp).isWithin(1f).of(12f)
        assertThat((bottomLeft.top - topLeft.bottom) / dp).isWithin(1f).of(12f)
    }

    @Test
    fun aPhotoTile_andAPlainTile_haveTheSameSize() { // #108
        storedPhoto("front")
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), frontPhotoPath = "front", showPhotoOnList = true),
            aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")),
            photos = FakeCardPhotos("front"),
        )
        val loader = EntryPointAccessors.fromApplication(composeRule.activity, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }

        val (photo, plain) = composeRule.onAllNodesWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.size }
        composeRule.onNodeWithTag(CARD_TILE_PHOTO_TAG, useUnmergedTree = true).assertIsDisplayed()
        assertThat(photo).isEqualTo(plain)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measuring
    fun atFontScale2_onANarrowScreen_noTextIsClippedOrOverlaps() { // #108; Robolectric's default screen is 320dp wide
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Shell ClubSmart"), label = "Work car"),
            aLoyaltyCard(id = cardId(2), store = aStore(name = "Penny Market")),
            fontScale = 2f,
        )

        // A tile's label ends in "…" when it doesn't fit, and its initials shrink to fit, by design.
        composeRule.assertTextFitsAndDoesNotOverlap(fitsByDesign = setOf("Work car", "Penny Market", "SC", "PM"))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aTile_isInItsStoresColour() { // #178: the Play listing says the cards are tiles in their store's colour
        setScreen(aLoyaltyCard(store = aStore(name = "Test Store", color = "#AA0000")))

        val tile = composeRule.onNodeWithTag(CARD_TILE_TAG, useUnmergedTree = true).captureToImage().toPixelMap()

        // Left of the initials and above the label, clear of the rounded corner.
        assertThat(tile[tile.width / 8, tile.height / 3]).isEqualTo(Color(0xFFAA0000))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aLogoInSingleColour_isDrawnInItsColour_onTheTile() { // #240: a logo in its own brand colour, drawn white
        File(composeRule.activity.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeSolidPng(0xFFAA0000.toInt())
        val store = aStore(color = "#AA0000", logo = "logo", logoTreatment = LogoTreatment(style = LogoStyle.SINGLE_COLOUR, tint = "#FFFFFF"))
        setScreen(aLoyaltyCard(store = store), photos = FakeCardPhotos("logo"))
        waitForAnImageToLoad()
        composeRule.waitForIdle()

        val logo = composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).captureToImage().toPixelMap()

        assertThat(logo[logo.width / 2, logo.height / 2]).isEqualTo(Color.White)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    fun aRecolouredLogo_isDrawnInItsColour_onTheTile_andFollowsANewBrandColour() { // #240
        File(composeRule.activity.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeSolidPng(0xFFAA0000.toInt())
        val store = aStore(color = "#AA0000", logo = "logo", logoTreatment = LogoTreatment(style = LogoStyle.RECOLOUR, tint = "#FFFFFF"))
        val cards = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = store)))
        val photos = FakeCardPhotos("logo")
        setScreen(photos = photos, viewModel = ListViewModel(cards, FakeUserPreferencesRepository(), SavedStateHandle(), photos))
        waitForAnImageToLoad()
        composeRule.waitForIdle()

        // The logo in the brand colour, recoloured white.
        assertThat(logoCentre()).isEqualTo(Color.White)

        // A blue store: its red logo is no longer in the brand colour, so it is drawn as it is.
        cards.cards.value = listOf(aLoyaltyCard(store = store.copy(color = "#0000AA")))
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { logoCentre() == Color(0xFFAA0000) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real drawing, for the pixels
    @Config(qualifiers = "xxhdpi") // a ring a few pixels wide, not one blended by scaling
    fun anOutlinedLogo_isDrawnInARing_onTheTile_andFollowsANewBrandColour() { // #240: black on red, white on dark blue
        File(composeRule.activity.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve("logo").writeSquareOnClearPng(0xFF0000AA.toInt())
        val store = aStore(color = "#FF0000", logo = "logo", logoTreatment = LogoTreatment(outline = true))
        val cards = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = store)))
        val photos = FakeCardPhotos("logo")
        setScreen(photos = photos, viewModel = ListViewModel(cards, FakeUserPreferencesRepository(), SavedStateHandle(), photos))
        waitForAnImageToLoad()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { logoRing() == Color.Black }
        assertThat(logoCentre()).isEqualTo(Color(0xFF0000AA))

        cards.cards.value = listOf(aLoyaltyCard(store = store.copy(color = "#112233")))
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { logoRing() == Color.White }
    }

    private fun logoRing(): Color {
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).captureToImage().toPixelMap().ringPixel()
    }

    private fun logoCentre(): Color {
        composeRule.waitForIdle()
        val logo = composeRule.onNodeWithTag(CARD_TILE_LOGO_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        return logo[logo.width / 2, logo.height / 2]
    }

    @Test
    fun aTile_hasBankCardProportions() {
        setScreen(aLoyaltyCard())

        val tile = composeRule.onNodeWithTag(CARD_TILE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        // ID-1 cards are 85.60 × 53.98 mm; a pixel of rounding either way.
        assertThat(tile.width / tile.height).isWithin(0.02f).of(85.60f / 53.98f)
    }

    @Test
    fun emptyOrMalformedStoreColor_doesNotCrash() { // #14
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "No Colour", color = "")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Bad Colour", color = "not a colour")))

        tile("No Colour").assertIsDisplayed()
        tile("Bad Colour").assertIsDisplayed()
    }

    @Test
    fun addCard_startsAddingACard() {
        var addCardOpened = 0
        setScreen(directions = ListDirections(toCard = { _, _ -> true }, toAddCard = { addCardOpened++ }, toEditCard = { _, _ -> }, toAbout = {}))

        composeRule.onNodeWithContentDescription("Add card").performClick()

        assertThat(addCardOpened).isEqualTo(1)
    }

    @Test
    fun theAboutButton_opensAbout() { // #150
        var aboutOpened = 0
        setScreen(aLoyaltyCard(), directions = ListDirections(toCard = { _, _ -> true }, toAddCard = {}, toEditCard = { _, _ -> }, toAbout = { aboutOpened++ }))

        composeRule.onNodeWithContentDescription("About").performClick()

        assertThat(aboutOpened).isEqualTo(1)
    }

    @Test
    fun noCards_offersToAddTheFirstOne_withoutASearchField() {
        var addCardOpened = 0
        setScreen(directions = ListDirections(toCard = { _, _ -> true }, toAddCard = { addCardOpened++ }, toEditCard = { _, _ -> }, toAbout = {}))

        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Add your first card").performClick()

        assertThat(addCardOpened).isEqualTo(1)
    }

    @Test
    fun deletingTheLastCard_duringASearch_showsTheEmptyState() { // review of #7
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Berry"))))
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("berry")

        repository.cards.value = emptyList()

        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
    }

    @Test
    fun theEmptyState_waitsForTheCardsToLoad() {
        val repository = FakeLoyaltyCardRepository(emptyList())
        repository.loadGate = CompletableDeferred()
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))

        composeRule.onNodeWithText("No cards yet").assertDoesNotExist()
        repository.loadGate!!.complete(Unit)
        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
    }

    @Test
    fun aSearchThatMatchesNothing_isNotTheEmptyState() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("xyz")

        composeRule.onNodeWithText("No card matches “xyz”").assertIsDisplayed()
        composeRule.onNodeWithText("No cards yet").assertDoesNotExist()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).assertIsDisplayed()
    }

    @Test
    fun theLastTile_isNotCoveredByTheAddButtonWhenScrolledToTheEnd() {
        // Zero-padded, so name order is this order.
        val cards = (1..12).map { aLoyaltyCard(id = cardId(it), store = aStore(name = "Store %02d".format(it))) }
        setScreen(*cards.toTypedArray())

        composeRule.onNode(hasScrollAction()).performScrollToIndex(cards.lastIndex)

        val lastTile = tile("Store 12").fetchSemanticsNode().boundsInRoot
        val addButton = composeRule.onNodeWithContentDescription("Add card").fetchSemanticsNode().boundsInRoot
        assertThat(lastTile.overlaps(addButton)).isFalse()
    }

    @Test
    fun searching_showsOnlyTheMatchingCards() {
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("berr")

        tile("Berry").assertIsDisplayed()
        tile("Apple").assertDoesNotExist()
    }

    @Test
    fun aSearchThatMatchesNothing_saysSo() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput(" xyz ")

        composeRule.onNodeWithText("No card matches “xyz”").assertIsDisplayed()
    }

    @Test
    fun aRestoredSearch_doesNotSayNothingMatches_beforeTheCardsHaveLoaded() {
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Berry"))))
        repository.loadGate = CompletableDeferred()
        // The query comes back with the saved state at once; the cards come from Room later.
        val viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(mapOf("query" to "berry")), FakeCardPhotos())
        composeRule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = ListDirections({ _, _ -> true }, {}, { _, _ -> }, toAbout = {}), viewModel = viewModel)
                }
            }
        }

        composeRule.onNodeWithText("No card matches “berry”").assertDoesNotExist()
        repository.loadGate!!.complete(Unit)
        tile("Berry").assertIsDisplayed()
    }

    @Test
    fun longPressingATile_selectsIt_inPlaceOfTheTitleAndTheAddButton() { // #7: multi-select
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")))

        tile("Apple").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        tile("Apple").assert(isSelected())
        tile("Berry").assert(isNotSelected())
        composeRule.onNodeWithText("My Cards").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Add card").assertDoesNotExist()
    }

    @Test
    fun aTile_offersToSelectIt_toTalkBack() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        tile("Apple").assert(
            SemanticsMatcher("long click labelled Select") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Select" }
        )
    }

    @Test
    fun whileSelecting_aTapSelectsTheCard_insteadOfOpeningIt() {
        var opened = 0
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")),
            directions = ListDirections(toCard = { _, _ -> opened++; true }, toAddCard = {}, toEditCard = { _, _ -> }, toAbout = {}),
        )

        tile("Apple").performTouchInput { longClick() }
        tile("Berry").performClick()

        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        tile("Berry").assert(isSelected())
        assertThat(opened).isEqualTo(0)
    }

    @Test
    fun deselectingTheLastSelectedCard_endsTheSelection() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))

        tile("Apple").performTouchInput { longClick() }
        tile("Apple").performClick()

        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add card").assertIsDisplayed()
    }

    @Test
    fun close_endsTheSelection() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))
        tile("Apple").performTouchInput { longClick() }

        composeRule.onNodeWithContentDescription("Close").performClick()

        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        tile("Apple").assert(isNotSelected())
    }

    @Test
    fun back_endsTheSelection_andStaysOnTheList() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))
        tile("Apple").performTouchInput { longClick() }

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        assertThat(composeRule.activity.isFinishing).isFalse()
    }

    @Test
    fun edit_isOfferedForOneSelectedCard_andOpensItsForm_endingTheSelection() {
        var edited: Pair<String, String>? = null
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "Berry")),
            directions = ListDirections(toCard = { _, _ -> true }, toAddCard = {}, toEditCard = { id, storeId -> edited = id to storeId }, toAbout = {}),
        )
        tile("Apple").performTouchInput { longClick() }
        tile("Berry").performClick()
        composeRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
        tile("Apple").performClick()

        composeRule.onNodeWithContentDescription("Edit").performClick()

        assertThat(edited).isEqualTo(cardId(2) to "berry")
        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
    }

    @Test
    fun deletingTheSelection_asksFirst_thenDeletesTheCards() {
        val repository = FakeLoyaltyCardRepository(
            listOf(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")), aLoyaltyCard(id = cardId(3), store = aStore(name = "Cherry")))
        )
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        tile("Apple").performTouchInput { longClick() }
        tile("Cherry").performClick()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNodeWithText("Delete 2 cards?").assertIsDisplayed()
        assertThat(repository.cards.value).hasSize(3)
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeRule.onNodeWithText("Delete 2 cards?").assertDoesNotExist()
        tile("Apple").assertDoesNotExist()
        tile("Cherry").assertDoesNotExist()
        tile("Berry").assert(isNotSelected())
        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
    }

    @Test
    fun deletingOneSelectedCard_asksAboutThatCard() {
        setScreen(aLoyaltyCard(store = aStore(name = "Apple")))
        tile("Apple").performTouchInput { longClick() }

        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Delete this card?").assertIsDisplayed()
    }

    @Test
    fun cancellingTheDelete_keepsTheCardsAndTheSelection() {
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Apple"))))
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        tile("Apple").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(repository.cards.value).hasSize(1)
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun aFailedDelete_saysSo_andDeleteTriesAgain() {
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Apple"))))
        repository.deleteFailure = IllegalStateException("disk I/O error")
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        tile("Apple").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeRule.onNodeWithText("The card couldn't be deleted. Try again.").assertIsDisplayed()
        repository.deleteFailure = null
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText("No cards yet").assertIsDisplayed()
    }

    @Test
    fun aNewSelectionAfterADelete_doesNotReopenTheDialog() {
        setScreen(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry")))
        tile("Apple").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        tile("Berry").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun theDeleteDialog_survivesProcessDeath() { // review of #7: the selection started empty, which closed the dialog
        val restorationTester = StateRestorationTester(composeRule)
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"))))
        val handle = SavedStateHandle()
        var viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), handle, FakeCardPhotos())
        restorationTester.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = ListDirections({ _, _ -> true }, {}, { _, _ -> }, toAbout = {}), viewModel = viewModel)
                }
            }
        }
        tile("Apple").performTouchInput { longClick() }
        tile("Berry").performClick()
        composeRule.onNodeWithContentDescription("Delete").performClick()

        // A new process: a new ViewModel on the saved state, whose cards load only later.
        repository.loadGate = CompletableDeferred()
        viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }), FakeCardPhotos())
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        repository.loadGate!!.complete(Unit)

        composeRule.onNodeWithText("Delete 2 cards?").assertIsDisplayed()
    }

    @Test
    fun whileTheCardsAreDeleting_theDialogCantBeCancelled() { // review of #7: Cancel didn't stop the write
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(store = aStore(name = "Apple"))))
        repository.deleteGate = CompletableDeferred()
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        tile("Apple").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun aFailureLeftWhenTheSelectionEnded_isNotShownByTheNextDelete() { // review of #7
        val repository = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple")), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"))))
        repository.deleteFailure = IllegalStateException("disk I/O error")
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        tile("Apple").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText("The card couldn't be deleted. Try again.").assertIsDisplayed()

        // The selected card goes another way (e.g. deleted elsewhere), which ends the selection and closes the dialog.
        repository.cards.value = repository.cards.value.filterNot { it.id == cardId(1) }
        composeRule.onNodeWithText("My Cards").assertIsDisplayed()
        tile("Berry").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()

        composeRule.onNodeWithText("It is removed from this phone for good.").assertIsDisplayed()
    }

    @Test
    fun theSortMenu_marksTheStoredSort() {
        setScreen(aLoyaltyCard(), preferences = FakeUserPreferencesRepository(CardSort.RECENTLY_USED))

        composeRule.onNodeWithContentDescription("Sort").performClick()

        composeRule.onNode(hasText("Recently used") and isSelected()).assertIsDisplayed()
        composeRule.onNode(hasText("Name") and isNotSelected()).assertIsDisplayed()
        composeRule.onNode(hasText("Most used") and isNotSelected()).assertIsDisplayed()
    }

    @Test
    fun choosingASort_storesIt_andReordersTheTiles() {
        val preferences = FakeUserPreferencesRepository()
        setScreen(
            aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), useCount = 1),
            aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"), useCount = 9),
            preferences = preferences,
        )
        assertThat(left("Apple")).isLessThan(left("Berry"))

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.onNodeWithText("Most used").performClick()

        assertThat(preferences.cardSort.value).isEqualTo(CardSort.MOST_USED)
        composeRule.onNodeWithText("Most used").assertDoesNotExist() // the menu closed
        assertThat(left("Berry")).isLessThan(left("Apple"))
    }

    @Test
    fun returningFromACard_whoseUseMovedItUp_findsItsTileWhereItWasTapped() { // #61
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        val tappedTop = top("Store 15")

        tile("Store 15").performClick()
        repository.setUseCount("Store 15", 12) // the use moves it from 15th to 10th, three rows up
        composeRule.waitForIdle()
        showList.value = true
        composeRule.waitForIdle()

        tile("Store 15").assertIsDisplayed()
        assertThat(top("Store 15")).isEqualTo(tappedTop)
    }

    @Test
    fun returningFromACard_whoseNewPlaceArrivesDuringTheReturn_findsItsTileWhereItWasTapped() { // #61
        // After a few seconds on the card, the list's cards are re-read only when it is shown again.
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        val tappedTop = top("Store 15")
        tile("Store 15").performClick()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        showList.value = true
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        repository.setUseCount("Store 15", 12)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        tile("Store 15").assertIsDisplayed()
        assertThat(top("Store 15")).isEqualTo(tappedTop)
    }

    @Test
    fun aSecondTileTappedWhileTheListLeaves_isNotTheOneHeldOnReturn() { // review of #69: only the first tap opens a card
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        val tappedTop = top("Store 15")

        composeRule.mainClock.autoAdvance = false
        tile("Store 15").performClick()
        tile("Store 14").performClick()
        composeRule.mainClock.autoAdvance = true
        repository.setUseCount("Store 15", 12)
        composeRule.waitForIdle()
        showList.value = true
        composeRule.waitForIdle()

        tile("Store 15").assertIsDisplayed()
        assertThat(top("Store 15")).isEqualTo(tappedTop)
    }

    @Test
    fun returningFromACard_thatMovedToTheTop_showsItsTile() { // #61
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)

        tile("Store 15").performClick()
        repository.setUseCount("Store 15", 100)
        composeRule.waitForIdle()
        showList.value = true
        composeRule.waitForIdle()

        tile("Store 15").assertIsDisplayed()
    }

    @Test
    fun onceBackOnTheList_aLaterReorder_doesNotHoldTheOpenedTileInPlace() { // #61
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        tile("Store 15").performClick()
        composeRule.waitForIdle()
        showList.value = true
        composeRule.waitForIdle()

        repository.setUseCount("Store 15", 100)
        composeRule.waitForIdle()

        // The list keeps its scroll position, as for any reorder, so the tile that moved to the top is out of view.
        tile("Store 15").assertIsNotDisplayed()
    }

    @Test
    fun theList_survivesProcessDeath_withNoCardOpened() { // #61: the opened tile is saved state
        val restorationTester = StateRestorationTester(composeRule)
        val viewModel = ListViewModel(FakeLoyaltyCardRepository(cardsByUse()), FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos())
        restorationTester.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    ListScreen(animatedVisibilityScope = this, directions = ListDirections({ _, _ -> true }, {}, { _, _ -> }, toAbout = {}), viewModel = viewModel)
                }
            }
        }

        restorationTester.emulateSavedInstanceStateRestore()

        tile("Store 01").assertIsDisplayed()
    }

    @Test
    fun returningFromACard_afterProcessDeath_findsItsTileWhereItWasTapped() { // #61
        val restorationTester = StateRestorationTester(composeRule)
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val showList = setListInNavigation(repository, restorationTester)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        val tappedTop = top("Store 15")
        tile("Store 15").performClick()
        composeRule.waitForIdle()

        restorationTester.emulateSavedInstanceStateRestore()
        repository.setUseCount("Store 15", 12)
        showList.value = true
        composeRule.waitForIdle()

        tile("Store 15").assertIsDisplayed()
        assertThat(top("Store 15")).isEqualTo(tappedTop)
    }

    @Test
    fun returningFromACard_onAScreenThatChangedSize_showsItsWholeTile() { // review of #61: the offset is in pixels
        val repository = FakeLoyaltyCardRepository(cardsByUse())
        val density = mutableStateOf<Density?>(null)
        val showList = setListInNavigation(repository, density = density)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(12)
        tile("Store 17").performClick() // a tile in the third visible row
        composeRule.waitForIdle()

        // Larger text and controls, as after a display size change: the same offset in pixels is below the grid.
        density.value = Density(composeRule.density.density * 1.5f)
        showList.value = true
        composeRule.waitForIdle()

        val grid = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        // Bounds in root are clipped to the grid, so the tile's own position and size tell whether it fits.
        val tile = composeRule.onNode(hasTestTag(CARD_TILE_TAG) and hasContentDescription("Store 17"), useUnmergedTree = true).fetchSemanticsNode()
        assertThat(tile.positionInRoot.y).isAtLeast(grid.top)
        assertThat(tile.positionInRoot.y + tile.size.height).isAtMost(grid.bottom)
    }

    /** "Store 01" to "Store 20", most used first. */
    private fun cardsByUse() =
        (1..20).map { aLoyaltyCard(id = cardId(it), store = aStore(name = "Store %02d".format(it)), useCount = 21 - it) }

    private fun FakeLoyaltyCardRepository.setUseCount(storeName: String, useCount: Int) {
        cards.value = cards.value.map { if (it.store.name == storeName) it.copy(useCount = useCount) else it }
    }

    /**
     * The list sorted by Most used, as the app's navigation shows it: opening a card replaces the list, which keeps its
     * saved state, until the returned flag is set again.
     */
    private fun setListInNavigation(
        repository: FakeLoyaltyCardRepository,
        restorationTester: StateRestorationTester? = null,
        density: MutableState<Density?> = mutableStateOf(null),
    ): MutableState<Boolean> {
        val showList = mutableStateOf(true)
        val viewModel = ListViewModel(repository, FakeUserPreferencesRepository(CardSort.MOST_USED), SavedStateHandle(), FakeCardPhotos())
        // Opens a card only from the shown list, as the app's directions do (#69).
        val directions = ListDirections(toCard = { _, _ -> showList.value.also { showList.value = false } }, toAddCard = {}, toEditCard = { _, _ -> }, toAbout = {})
        val setContent: (@Composable () -> Unit) -> Unit = restorationTester?.let { it::setContent } ?: composeRule::setContent
        setContent {
            val savedState = rememberSaveableStateHolder()
            SharedTransitionLayout {
                AnimatedContent(targetState = showList.value, label = "list to card") { list ->
                    if (list) {
                        savedState.SaveableStateProvider("list") {
                            CompositionLocalProvider(LocalDensity provides (density.value ?: LocalDensity.current)) {
                                ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        return showList
    }

    private fun top(store: String) = tile(store).fetchSemanticsNode().boundsInRoot.top

    @Test
    @Config(qualifiers = "+hu")
    fun inHungarian_everyTextOnTheList_isTranslated() { // #72
        val repository = FakeLoyaltyCardRepository(
            listOf(aLoyaltyCard(id = cardId(1), store = aStore(name = "Apple"), label = "Work"), aLoyaltyCard(id = cardId(2), store = aStore(name = "Berry"))),
        )
        setScreen(viewModel = ListViewModel(repository, FakeUserPreferencesRepository(), SavedStateHandle(), FakeCardPhotos()))
        val shown = ShownTexts(composeRule)
        shown.collect()

        composeRule.onNodeWithContentDescription("Rendezés").performClick()
        shown.collect()
        composeRule.onNodeWithText("Név").performClick()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextInput("xyz")
        shown.collect()
        composeRule.onNodeWithTag(CARD_SEARCH_TAG).performTextClearance()
        composeRule.onNodeWithText("Work").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Törlés").performClick()
        shown.collect()
        composeRule.onNodeWithText("Mégse").performClick()
        tile("Berry").performClick()
        composeRule.onNodeWithContentDescription("Törlés").performClick()
        shown.collect()
        repository.deleteFailure = IllegalStateException("disk I/O error")
        composeRule.onNode(hasText("Törlés") and hasAnyAncestor(isDialog())).performClick()
        shown.collect()
        repository.deleteFailure = null
        composeRule.onNode(hasText("Törlés") and hasAnyAncestor(isDialog())).performClick()
        composeRule.onNodeWithText("Még nincs kártyád").assertIsDisplayed()
        shown.collect()

        shown.assertSeen(
            "Kártyáim", "Keresés a kártyák között", "Nincs ilyen kártya: „xyz”", "1 kijelölve", "Törlöd ezt a kártyát?",
            "2 kijelölve", "Törlöd a kijelölt 2 kártyát?", "Nem sikerült törölni a kártyákat. Próbáld újra.", "Kijelölés", "Névjegy",
        )
        shown.assertEveryTextIsTranslated("Apple", "Work", "Berry", "A", "B", "Apple, Work", "xyz")
    }

    private fun setScreen(
        vararg cards: LoyaltyCard,
        directions: ListDirections = ListDirections(toCard = { _, _ -> true }, toAddCard = {}, toEditCard = { _, _ -> }, toAbout = {}),
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        photos: FakeCardPhotos = FakeCardPhotos(),
        viewModel: ListViewModel = ListViewModel(FakeLoyaltyCardRepository(cards.toList()), preferences, SavedStateHandle(), photos),
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        ListScreen(animatedVisibilityScope = this, directions = directions, viewModel = viewModel)
                    }
                }
            }
        }
    }

    /** A decodable photo [name] where the app's image loader looks for card photos. */
    /** Waits for the app's loader to have loaded a picture, so a failed load (which shows the initials) would have shown. */
    private fun waitForAnImageToLoad() {
        val loader = EntryPointAccessors.fromApplication(composeRule.activity, ImageLoaderEntryPoint::class.java).getImageLoader()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { ShadowLooper.idleMainLooper(); (loader.memoryCache?.size ?: 0) > 0 }
    }

    private fun storedPhoto(name: String) =
        File(composeRule.activity.filesDir, CardPhotosModule.PHOTO_DIRECTORY).resolve(name).writeOnePixelPng()

    private fun colourDistance(a: Color, b: Color) = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    /** Every text drawn on the tiles, in the unmerged tree: the initials and the labels. */
    private fun textsOnTheTiles() =
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(CARD_TILE_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** An unlabelled card's tile, found by what TalkBack reads: its store's name (the tile draws only a label, #183). */
    private fun tile(store: String) = composeRule.onNodeWithContentDescription(store)

    /** Two tiles share a row of the grid, so the first one is the one on the left. */
    private fun left(store: String) = tile(store).fetchSemanticsNode().boundsInRoot.left
}
