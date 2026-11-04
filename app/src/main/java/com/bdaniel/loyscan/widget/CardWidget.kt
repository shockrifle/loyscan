package com.bdaniel.loyscan.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.openCardIntent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The home-screen widget (#11): the user's cards as tiles, in the list's sort order, each opening its card. A tile is
 * the card's brand-colour box with its list label; no photo in this slice ([WidgetTile]). The cards and the sort are
 * read live from the repositories, so the widget follows them as they change. Rendering is Glance (RemoteViews) glue;
 * the logic it shows is [toWidgetTiles], unit-tested, and the content is [CardWidgetContent].
 */
class CardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, CardWidgetEntryPoint::class.java)
        val cards = entryPoint.loyaltyCardRepository().getAllWithStore()
        val sort = entryPoint.userPreferencesRepository().cardSort
        provideContent {
            val cardsNow by cards.collectAsState(initial = emptyList())
            val sortNow by sort.collectAsState(initial = CardSort.NAME)
            CardWidgetContent(cardsNow.toWidgetTiles(sortNow))
        }
    }
}

/** Lets the widget's [GlanceAppWidget] reach the repositories; a receiver can't be a `@HiltViewModel`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardWidgetEntryPoint {
    fun loyaltyCardRepository(): LoyaltyCardRepository
    fun userPreferencesRepository(): UserPreferencesRepository
}

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
