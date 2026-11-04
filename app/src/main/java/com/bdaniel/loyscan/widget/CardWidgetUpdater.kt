package com.bdaniel.loyscan.widget

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in step with the cards (#11). A Glance widget renders a snapshot: once its session
 * ends it does not follow a `Flow`, so something must redraw it when the data changes. This observes the rendered
 * tiles and refreshes the widget whenever they change — and only then, so an edit a tile doesn't show (a note, a
 * number) doesn't redraw it.
 *
 * Started from [com.bdaniel.loyscan.LoyScanApplication] on an app-wide scope, guarded by [CardWidgetRefresher.hasWidget]
 * so it doesn't touch the database when no widget is placed (the common case, and every test that boots the app).
 * Cards change only through the running app, so observing while the process is alive is enough; adding a widget
 * renders the current cards through `provideGlance` on its own.
 */
@Singleton
class CardWidgetUpdater @Inject constructor(
    private val cards: LoyaltyCardRepository,
    private val preferences: UserPreferencesRepository,
    private val refresher: CardWidgetRefresher,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            if (!refresher.hasWidget()) return@launch
            combine(cards.getAllWithStore(), preferences.cardSort) { cards, sort -> cards.toWidgetTiles(sort) }
                .distinctUntilChanged()
                .collect { refresher.refresh() }
        }
    }
}
