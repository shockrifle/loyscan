package com.bdaniel.loyscan.widget

import androidx.compose.ui.graphics.Color
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant

/**
 * The widget's tiles: one per card, in the list's sort order, each with the colour and the name a tile shows.
 * Robolectric, because the colour helpers parse `#RRGGBB` through `android.graphics.Color` ([backgroundColor]).
 */
@RunWith(AndroidJUnit4::class)
class CardWidgetTilesTest {

    @Test
    fun toWidgetTiles_followsTheGivenSortOrder() {
        val seldom = aLoyaltyCard(id = cardId(1), label = "Seldom", useCount = 1)
        val often = aLoyaltyCard(id = cardId(2), label = "Often", useCount = 9)

        val tiles = listOf(seldom, often).toWidgetTiles(CardSort.MOST_USED)

        assertThat(tiles.map { it.cardId }).containsExactly(cardId(2), cardId(1)).inOrder()
    }

    @Test
    fun toWidgetTiles_carryTheStoresInitials() { // #108: a tile shows them, like the list's plain tile
        val card = aLoyaltyCard(store = aStore(name = "Shell ClubSmart"))

        assertThat(listOf(card).toWidgetTiles(CardSort.NAME).single().initials).isEqualTo("SC")
    }

    @Test
    fun toWidgetTiles_labelIsTheCardsLabel_andNoneWhenItHasNone() { // #181: a tile never shows the store name (#183)
        val labelled = aLoyaltyCard(id = cardId(1), label = "My Tesco")
        val unlabelled = aLoyaltyCard(id = cardId(2), label = null, store = aStore(name = "Tesco"))

        val tiles = listOf(labelled, unlabelled).toWidgetTiles(CardSort.NAME)

        assertThat(tiles.single { it.cardId == cardId(1) }.label).isEqualTo("My Tesco")
        assertThat(tiles.single { it.cardId == cardId(2) }.label).isNull()
    }

    @Test
    fun toWidgetTiles_carryTheStoreName_forTalkBack() { // #181
        val card = aLoyaltyCard(label = "Gold", store = aStore(name = "Tesco"))

        assertThat(listOf(card).toWidgetTiles(CardSort.NAME).single().storeName).isEqualTo("Tesco")
    }

    @Test
    fun toWidgetTiles_carryTheStoresLogo_orNoneWithoutOne() { // #181
        val withLogo = aLoyaltyCard(id = cardId(1), store = aStore(id = "a", logo = "logo-1"))
        val withoutLogo = aLoyaltyCard(id = cardId(2), store = aStore(id = "b", logo = null))

        val tiles = listOf(withLogo, withoutLogo).toWidgetTiles(CardSort.NAME)

        assertThat(tiles.single { it.cardId == cardId(1) }.logo).isEqualTo("logo-1")
        assertThat(tiles.single { it.cardId == cardId(2) }.logo).isNull()
    }

    @Test
    fun toWidgetTiles_carriesTheStoresBrandColour() {
        val card = aLoyaltyCard(store = aStore(color = "#112233"))

        val tiles = listOf(card).toWidgetTiles(CardSort.NAME)

        assertThat(tiles.single().color).isEqualTo("#112233")
    }

    @Test
    fun backgroundColor_isTheBrandColour_orAFallbackWhenItIsEmptyOrMalformed() {
        assertThat(WidgetTile("c", "#112233", "A", "A", null).backgroundColor()).isEqualTo(Color(0xFF112233))
        assertThat(WidgetTile("c", "", "A", "A", null).backgroundColor()).isEqualTo(Color(0xFF5A5A66))
        assertThat(WidgetTile("c", "not a colour", "A", "A", null).backgroundColor()).isEqualTo(Color(0xFF5A5A66))
    }

    @Test
    fun textColor_contrastsWithTheBackground() {
        assertThat(WidgetTile("c", "#000000", "A", "A", null).textColor()).isEqualTo(Color.White)
        assertThat(WidgetTile("c", "#FFFFFF", "A", "A", null).textColor()).isEqualTo(Color.Black)
    }

    @Test
    fun toWidgetTiles_recentlyUsedPutsTheMostRecentFirst_andNeverUsedLast() {
        val old = aLoyaltyCard(id = cardId(1), label = "Old", lastUsedAt = Instant.parse("2026-01-01T00:00:00Z"))
        val recent = aLoyaltyCard(id = cardId(2), label = "Recent", lastUsedAt = Instant.parse("2026-02-01T00:00:00Z"))
        val never = aLoyaltyCard(id = cardId(3), label = "Never", lastUsedAt = null)

        val tiles = listOf(old, never, recent).toWidgetTiles(CardSort.RECENTLY_USED)

        assertThat(tiles.map { it.cardId }).containsExactly(cardId(2), cardId(1), cardId(3)).inOrder()
    }
}
