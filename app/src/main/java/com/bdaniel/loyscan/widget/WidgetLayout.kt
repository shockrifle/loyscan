package com.bdaniel.loyscan.widget

import android.content.Context
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.theme.Spacing

/** The space between the widget's edge and its tiles, and between two tiles (#182; 8dp until then). */
val WIDGET_SPACING = Spacing.xs

/**
 * The padding around the widget's grid and around each tile. Glance has no spacing between grid items, so two tiles'
 * padding make the gap, and the grid's and a tile's the edge: each is [WIDGET_SPACING].
 */
val WIDGET_HALF_SPACING = WIDGET_SPACING / 2

/** From this width the widget shows two columns, so a tile stays at least ~110dp wide, about a narrow list tile. */
private val TWO_COLUMNS_FROM = 240.dp

/** How many columns of tiles a widget [width] wide shows: one below 240dp, two from 240dp. */
fun widgetColumns(width: Dp): Int = if (width < TWO_COLUMNS_FROM) 1 else 2

/**
 * A tile's size in a widget [width] wide: its cell less the padding, at the card's ratio. Glance has no `aspectRatio`, so
 * the height is computed. Zero while the widget's size isn't known yet, never negative.
 */
fun widgetTileSize(width: Dp): DpSize {
    val cell = (width - WIDGET_HALF_SPACING * 2) / widgetColumns(width)
    val tileWidth = (cell - WIDGET_HALF_SPACING * 2).coerceAtLeast(0.dp)
    return DpSize(tileWidth, tileWidth / CARD_ASPECT_RATIO)
}

/**
 * The corner radius of the widget's background, which the home screen rounds from Android 12
 * (`system_app_widget_background_radius`, 16dp in AOSP; a device maker can change it). Null before Android 12, whose
 * widgets have square corners.
 */
fun widgetBackgroundRadius(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Dp? {
    if (sdkInt < Build.VERSION_CODES.S) return null
    val resources = context.resources
    return (resources.getDimension(android.R.dimen.system_app_widget_background_radius) / resources.displayMetrics.density).dp
}

/**
 * A tile's corner radius (#182): the widget background's radius less the [WIDGET_SPACING] between the widget's edge
 * and the tile, so their corners nest concentrically. Square without a background radius (before Android 12).
 */
fun widgetTileCornerRadius(backgroundRadius: Dp?): Dp =
    if (backgroundRadius == null) 0.dp else (backgroundRadius - WIDGET_SPACING).coerceAtLeast(0.dp)

/** The height of a widget [width] wide that holds exactly one row of tiles: what it may be resized down to. */
fun widgetHeightForOneTile(width: Dp): Dp = widgetTileSize(width).height + WIDGET_SPACING * 2

/** The height every layout size gives: no more than the smallest the widget can be resized to. */
private val LAYOUT_HEIGHT = 40.dp

/**
 * The sizes the widget is laid out for (`SizeMode.Responsive`, #108). From Android 12 the home screen shows the largest
 * one that fits the widget's real size; their height is the smallest a widget can be, so the width alone decides. The
 * widths step so finely that a tile, filling its cell's real width with the height worked out for the layout's width,
 * stays within 10% of the card's ratio up to 440dp (16 sizes, the most a widget may give). On a device, `SizeMode.Exact`
 * showed a landscape layout squeezed into the portrait widget: the launcher's reported sizes didn't fit the real one.
 */
val WIDGET_SIZES: Set<DpSize> =
    listOf(110, 120, 131, 143, 156, 171, 187, 205, 224, 240, 262, 286, 313, 342, 374, 410)
        .map { DpSize(it.dp, LAYOUT_HEIGHT) }
        .toSet()

