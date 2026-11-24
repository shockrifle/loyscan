package com.bdaniel.loyscan.widget

import androidx.compose.ui.graphics.Color
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.domain.LoyaltyCard
import com.bdaniel.loyscan.domain.sortedBy
import com.bdaniel.loyscan.ui.contrastingTextColor
import com.bdaniel.loyscan.ui.toBrandColorOrNull

/**
 * One card as the home-screen widget shows it (#11): the brand [color] tile with the store's [initials] (#108) and the
 * card's own [label], if any, and the [cardId] a tap opens. Never the store's name on the tile, as on My cards (#183,
 * #181): [storeName] is for TalkBack ([description]). No photo; the widget mirrors the list's non-photo tile.
 */
data class WidgetTile(
    val cardId: String,
    val color: String,
    val initials: String,
    val storeName: String,
    val label: String?,
    val logo: String? = null,
)

/** The widget's tiles, in the list's [sort] order (#7, [sortedBy]); see [CardWidgetTilesTest]. */
fun List<LoyaltyCard>.toWidgetTiles(sort: CardSort): List<WidgetTile> =
    sortedBy(sort).map { card ->
        WidgetTile(
            cardId = card.id,
            color = card.store.color,
            initials = card.store.initials,
            storeName = card.store.name,
            label = card.label,
            logo = card.store.logo,
        )
    }

/** What TalkBack reads for the tile: "store, label", or the store alone, as the My cards tile (#181). */
fun WidgetTile.description(): String = listOfNotNull(storeName, label).joinToString(", ")

/** The tile's background: its brand colour, or a neutral grey when the brand colour is empty or malformed. */
fun WidgetTile.backgroundColor(): Color = color.toBrandColorOrNull() ?: WIDGET_TILE_FALLBACK

/** Black or white, whichever reads on [backgroundColor]. */
fun WidgetTile.textColor(): Color = backgroundColor().contrastingTextColor()

private val WIDGET_TILE_FALLBACK = Color(0xFF5A5A66)
