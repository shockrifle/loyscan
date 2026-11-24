package com.bdaniel.loyscan.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bdaniel.loyscan.R
import com.bdaniel.loyscan.openCardIntent
import com.bdaniel.loyscan.ui.theme.Spacing

/**
 * One clickable card-ratio tile per [tiles] entry, each opening its card (#11), on its brand colour: the store's logo
 * from [logos] fitted whole inside [WIDGET_LOGO_MARGIN], else the store's initials (#108, #181), and the card's label, if
 * any, never the store's name. TalkBack reads a tile as "store, label" (#181). One column in a narrow widget, two from
 * 240dp (#108, [widgetColumns]), laid out for each of [WIDGET_SIZES]; a tile fills its cell, so a layout shown wider or
 * narrower than it was made for doesn't cut it.
 * From Android 12 the tiles' corners nest in the widget background's rounded corners ([widgetTileCornerRadius], #182);
 * before it, the widget and its tiles are square. Empty until the first card is added.
 */
@Composable
fun CardWidgetContent(tiles: List<WidgetTile>, logos: Map<String, Bitmap> = emptyMap()) {
    val context = LocalContext.current
    val width = LocalSize.current.width
    val tileSize = widgetTileSize(width)
    val backgroundRadius = widgetBackgroundRadius()
    val cornerRadius = widgetTileCornerRadius(backgroundRadius)
    LazyVerticalGrid(
        gridCells = GridCells.Fixed(widgetColumns(width)),
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(backgroundRadius ?: 0.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(WIDGET_HALF_SPACING),
    ) {
        items(tiles, itemId = { it.cardId.hashCode().toLong() }) { tile ->
            Box(modifier = GlanceModifier.padding(WIDGET_HALF_SPACING)) {
                val face = GlanceModifier
                    // The cell's real width, which can differ from the layout's; the height is the layout's.
                    .fillMaxWidth()
                    .height(tileSize.height)
                    .cornerRadius(cornerRadius)
                    .background(ColorProvider(tile.backgroundColor()))
                    .clickable(actionStartActivity(openCardIntent(context, tile.cardId, tile.color)))
                    .semantics { contentDescription = tile.description() }
                val logo = tile.logo?.let(logos::get)
                if (logo != null) LogoTile(tile, logo, face) else InitialsTile(tile, face)
            }
        }
    }
}

/**
 * The store's [logo] fitted whole inside [WIDGET_LOGO_MARGIN] (#181), and the card's label, if any, at the bottom edge
 * over the logo, in white on a scrim in the My cards tile's colour (`res/drawable/widget_label_scrim.xml`) behind the
 * label alone, fading from clear at its top to dark at the tile's edge. Straight on the brand colour the label was hard to read
 * over a logo, and a solid band with a fade above it covered too much of it (device checks, 2026-11-24).
 */
@Composable
private fun LogoTile(tile: WidgetTile, logo: Bitmap, modifier: GlanceModifier) {
    Box(modifier = modifier) {
        Image(
            provider = ImageProvider(logo),
            contentDescription = null, // The tile is described as a whole.
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize().padding(WIDGET_LOGO_MARGIN).semantics { testTag = WIDGET_TILE_LOGO_TAG },
        )
        tile.label?.let { label ->
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                // The scrim is the label box's own background, so it ends just above the label: clear at the box's
                // top, at full strength at the tile's edge.
                Box(
                    modifier = GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_label_scrim))
                        .padding(Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label, style = TextStyle(color = ColorProvider(Color.White)), maxLines = 1)
                }
            }
        }
    }
}

/** The store's initials and, under them, the card's label, if any (#108). */
@Composable
private fun InitialsTile(tile: WidgetTile, modifier: GlanceModifier) {
    Column(
        modifier = modifier.padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = ColorProvider(tile.textColor())
        Text(text = tile.initials, style = TextStyle(color = color, fontSize = INITIALS_SIZE, fontWeight = FontWeight.Bold))
        tile.label?.let { Text(text = it, style = TextStyle(color = color), maxLines = 1) }
    }
}

/** The initials on a widget tile: large, but small enough to leave the label room on the smallest tile. */
private val INITIALS_SIZE = 22.sp

/** A tile's store logo (#181). */
const val WIDGET_TILE_LOGO_TAG = "widgetTileLogo"
