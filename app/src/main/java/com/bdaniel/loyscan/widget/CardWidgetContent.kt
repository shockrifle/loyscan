package com.bdaniel.loyscan.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bdaniel.loyscan.openCardIntent

/** One clickable tile per [tiles] entry, each opening its card (#11). Empty until the first card is added. */
@Composable
fun CardWidgetContent(tiles: List<WidgetTile>) {
    val context = LocalContext.current
    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
        items(tiles, itemId = { it.cardId.hashCode().toLong() }) { tile ->
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .background(ColorProvider(tile.backgroundColor()))
                    .clickable(actionStartActivity(openCardIntent(context, tile.cardId, tile.color))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text = tile.label, style = TextStyle(color = ColorProvider(tile.textColor())))
            }
        }
    }
}
