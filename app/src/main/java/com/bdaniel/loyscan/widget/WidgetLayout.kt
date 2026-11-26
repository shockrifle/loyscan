package com.bdaniel.loyscan.widget

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.WIDGET_CORNER_RADIUS

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

/** Below this width a tile is square (#216): at the card's ratio it would be too low for a logo and its label. */
private val SQUARE_TILES_BELOW = 120.dp

/** A widget tile's shape (#216): square when narrow, else the card's. */
enum class WidgetTileShape { SQUARE, CARD }

/** The shape of a tile in a widget [width] wide: square when the tile is narrower than 120dp, else the card's. */
fun widgetTileShape(width: Dp): WidgetTileShape =
    if (tileWidth(width) < SQUARE_TILES_BELOW) WidgetTileShape.SQUARE else WidgetTileShape.CARD

/**
 * A tile's size in a widget [width] wide: its cell less the padding, square or at the card's ratio ([widgetTileShape]).
 * On a home screen the tile's height comes from its real width (`res/layout/widget_tile.xml`, #216); this is the size
 * a widget exactly [width] wide gives it. Zero while the widget's size isn't known yet, never negative.
 */
fun widgetTileSize(width: Dp): DpSize {
    val tileWidth = tileWidth(width)
    val height = if (widgetTileShape(width) == WidgetTileShape.SQUARE) tileWidth else tileWidth / CARD_ASPECT_RATIO
    return DpSize(tileWidth, height)
}

private fun tileWidth(width: Dp): Dp {
    val cell = (width - WIDGET_HALF_SPACING * 2) / widgetColumns(width)
    return (cell - WIDGET_HALF_SPACING * 2).coerceAtLeast(0.dp)
}

/**
 * The corner radius the widget rounds its background with: [WIDGET_CORNER_RADIUS] from Android 12, null before it, whose
 * widgets have square corners (Glance can't round a view there).
 */
fun widgetBackgroundRadius(sdkInt: Int = Build.VERSION.SDK_INT): Dp? =
    if (sdkInt < Build.VERSION_CODES.S) null else WIDGET_CORNER_RADIUS

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
 * one that fits the widget's real size; their height is the smallest a widget can be, so the width alone decides. A
 * layout chooses the columns and the tiles' shape; a tile's size comes from its real width (#216), so the widths are
 * the switch points: one column of square tiles from 110dp, of card tiles from 128dp, two columns of square tiles from
 * 240dp, of card tiles from 252dp. On a device, `SizeMode.Exact` showed a landscape layout squeezed into the portrait
 * widget: the launcher's reported sizes didn't fit the real one.
 */
val WIDGET_SIZES: Set<DpSize> =
    listOf(110, 128, 240, 252)
        .map { DpSize(it.dp, LAYOUT_HEIGHT) }
        .toSet()

/** The margin between a tile's edge and its store's logo (#181): the logo is fitted whole inside it. */
val WIDGET_LOGO_MARGIN = Spacing.s

/**
 * The tile a logo's box is worked out from: the widest of the layouts before #216 (one column at 224dp), kept so a
 * decoded logo keeps its size. A one-column tile can be up to 232dp wide, where a logo is drawn up to 7% larger than
 * it was decoded.
 */
private val LOGO_BOX_TILE_WIDTH = 216.dp

/**
 * The box a logo is decoded to fit (#181): a 216dp card-ratio tile less [WIDGET_LOGO_MARGIN] on every side, so one
 * bitmap serves every tile.
 */
fun widgetLogoBox(): DpSize = DpSize(
    LOGO_BOX_TILE_WIDTH - WIDGET_LOGO_MARGIN * 2,
    LOGO_BOX_TILE_WIDTH / CARD_ASPECT_RATIO - WIDGET_LOGO_MARGIN * 2,
)

/** [widgetLogoBox] in pixels at [density], rounded down: the size [WidgetPictures] decodes a logo to fit. */
fun widgetLogoBoxPx(density: Float): IntSize {
    val box = widgetLogoBox()
    return IntSize((box.width.value * density).toInt(), (box.height.value * density).toInt())
}
