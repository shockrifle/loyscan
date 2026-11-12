package com.bdaniel.loyscan.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.theme.CARD_CORNER_FRACTION
import com.bdaniel.loyscan.ui.theme.Spacing

/**
 * The padding around the widget's grid and around each tile (#108). Glance has no spacing between grid items, so two
 * tiles' padding make the 8dp gap, and the grid's and a tile's the 8dp edge.
 */
val WIDGET_PADDING = Spacing.xs

/** From this width the widget shows two columns, so a tile stays at least ~110dp wide, about a narrow list tile. */
private val TWO_COLUMNS_FROM = 240.dp

/** How many columns of tiles a widget [width] wide shows: one below 240dp, two from 240dp. */
fun widgetColumns(width: Dp): Int = if (width < TWO_COLUMNS_FROM) 1 else 2

/**
 * A tile's size in a widget [width] wide: its cell less the padding, at the card's ratio. Glance has no `aspectRatio`, so
 * the height is computed. Zero while the widget's size isn't known yet, never negative.
 */
fun widgetTileSize(width: Dp): DpSize {
    val cell = (width - WIDGET_PADDING * 2) / widgetColumns(width)
    val tileWidth = (cell - WIDGET_PADDING * 2).coerceAtLeast(0.dp)
    return DpSize(tileWidth, tileWidth / CARD_ASPECT_RATIO)
}

/** A tile's corner radius: [CARD_CORNER_FRACTION] of its short side, like the app's `CardShape`. */
fun widgetTileCornerRadius(tile: DpSize): Dp = tile.height * CARD_CORNER_FRACTION

/** The height of a widget [width] wide that holds exactly one row of tiles: what it may be resized down to. */
fun widgetHeightForOneTile(width: Dp): Dp = widgetTileSize(width).height + WIDGET_PADDING * 4
