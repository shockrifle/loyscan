package com.bdaniel.loyscan.widget.glance

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import com.bdaniel.loyscan.widget.CardWidgetContent
import com.bdaniel.loyscan.widget.CardWidgetUpdater
import com.bdaniel.loyscan.widget.WIDGET_SIZES
import com.bdaniel.loyscan.widget.WidgetPictures
import com.bdaniel.loyscan.widget.toWidgetTiles
import dagger.hilt.EntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The home-screen widget (#11): the user's cards as tiles, in the list's sort order, each opening its card, with its
 * store's logo when it has one (#181, [WidgetPictures]). The cards and the sort are read live from the repositories, so
 * the widget follows them as they change while its session is active ([CardWidgetUpdater] keeps it in step after
 * that). This is Glance (RemoteViews) runtime glue that runs only on a real home screen — it lives in `widget.glance`
 * and is excluded from the coverage gate; the logic it shows is `toWidgetTiles`, `WidgetPictures` and
 * `CardWidgetContent`, all tested.
 */
class CardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WIDGET_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, CardWidgetEntryPoint::class.java)
        val pictures = entryPoint.widgetPictures()
        val cards = entryPoint.loyaltyCardRepository().getAllWithStore()
        val sort = entryPoint.userPreferencesRepository().cardSort
        val content = combine(cards, sort) { cardsNow, sortNow -> cardsNow.toWidgetTiles(sortNow) }
            .map { tiles -> tiles to pictures.forTiles(tiles) }
        // The first tiles and their pictures are read before the first draw, so a tile doesn't show its initials
        // before its logo or photo (#181, #185).
        val first = content.first()
        provideContent {
            val now by content.collectAsState(initial = first)
            CardWidgetContent(tiles = now.first, pictures = now.second)
        }
    }
}

/** Lets the widget's [GlanceAppWidget] reach the repositories, and the app reach the updater; neither can `@Inject`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardWidgetEntryPoint {
    fun loyaltyCardRepository(): LoyaltyCardRepository
    fun userPreferencesRepository(): UserPreferencesRepository
    fun cardWidgetUpdater(): CardWidgetUpdater
    fun widgetPictures(): WidgetPictures
}
