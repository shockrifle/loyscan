package com.bdaniel.loyscan.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import com.bdaniel.loyscan.openCardIntent
import com.bdaniel.loyscan.ui.theme.Spacing

/**
 * One clickable card-ratio tile per [tiles] entry, each opening its card (#11): the store's initials and the card's label
 * on its brand colour, like the list's plain tile. One column in a narrow widget, two from 240dp (#108, [widgetColumns]),
 * laid out for each of [WIDGET_SIZES]; a tile fills its cell, so a layout shown wider or narrower than it was made for
 * doesn't cut it.
 * From Android 12 the tiles' corners nest in the widget background's rounded corners ([widgetTileCornerRadius], #182);
 * before it, the widget and its tiles are square. Empty until the first card is added.
 */
@Composable
fun CardWidgetContent(tiles: List<WidgetTile>) {
    val context = LocalContext.current
    val width = LocalSize.current.width
    val tileSize = widgetTileSize(width)
    val cornerRadius = widgetTileCornerRadius(widgetBackgroundRadius(context))
    LazyVerticalGrid(
        gridCells = GridCells.Fixed(widgetColumns(width)),
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(WIDGET_HALF_SPACING),
    ) {
        items(tiles, itemId = { it.cardId.hashCode().toLong() }) { tile ->
            Box(modifier = GlanceModifier.padding(WIDGET_HALF_SPACING)) {
                Column(
                    modifier = GlanceModifier
                        // The cell's real width, which can differ from the layout's; the height is the layout's.
                        .fillMaxWidth()
                        .height(tileSize.height)
                        .cornerRadius(cornerRadius)
                        .background(ColorProvider(tile.backgroundColor()))
                        .clickable(actionStartActivity(openCardIntent(context, tile.cardId, tile.color)))
                        .padding(Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = ColorProvider(tile.textColor())
                    Text(text = tile.initials, style = TextStyle(color = color, fontSize = INITIALS_SIZE, fontWeight = FontWeight.Bold))
                    Text(text = tile.label, style = TextStyle(color = color), maxLines = 1)
                }
            }
        }
    }
}

/** The initials on a widget tile: large, but small enough to leave the label room on the smallest tile. */
private val INITIALS_SIZE = 22.sp
