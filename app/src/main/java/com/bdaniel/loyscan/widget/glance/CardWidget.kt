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
import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.widget.CardWidgetContent
import com.bdaniel.loyscan.widget.CardWidgetUpdater
import com.bdaniel.loyscan.widget.toWidgetTiles
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The home-screen widget (#11): the user's cards as tiles, in the list's sort order, each opening its card. The cards
 * and the sort are read live from the repositories, so the widget follows them as they change while its session is
 * active ([CardWidgetUpdater] keeps it in step after that). This is Glance (RemoteViews) runtime glue that runs only
 * on a real home screen — it lives in `widget.glance` and is excluded from the coverage gate; the logic it shows is
 * `toWidgetTiles` and `CardWidgetContent`, both tested.
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

/** Lets the widget's [GlanceAppWidget] reach the repositories, and the app reach the updater; neither can `@Inject`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CardWidgetEntryPoint {
    fun loyaltyCardRepository(): LoyaltyCardRepository
    fun userPreferencesRepository(): UserPreferencesRepository
    fun cardWidgetUpdater(): CardWidgetUpdater
}
