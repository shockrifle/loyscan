package com.bdaniel.loyscan.widget

import com.bdaniel.loyscan.data.repository.LoyaltyCardRepository
import com.bdaniel.loyscan.data.repository.UserPreferencesRepository
import com.bdaniel.loyscan.di.WidgetScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the home-screen widget in step with the cards (#11). A Glance widget renders a snapshot: once its session
 * ends it does not follow a `Flow`, so something must redraw it when the data changes. This observes the rendered
 * tiles and refreshes the widget whenever they change — and only then, so an edit a tile doesn't show (a note, a
 * number) doesn't redraw it.
 *
 * [ensureObserving] is called both from [com.bdaniel.loyscan.LoyScanApplication] on start (for a widget placed before
 * the app launched) and from the widget receiver when a widget is added or shown (for one placed while the app runs),
 * so a widget placed mid-session is followed too. It is idempotent, and observes nothing while no widget is placed, so
 * it touches the database only when there is a widget to feed. Cards change only through the running app, so observing
 * while the process is alive is enough. A failure to read the cards or to redraw is logged, not thrown: the widget is
 * an optional extra and must not crash the app.
 */
@Singleton
class CardWidgetUpdater @Inject constructor(
    private val cards: LoyaltyCardRepository,
    private val preferences: UserPreferencesRepository,
    private val refresher: CardWidgetRefresher,
    @WidgetScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @Synchronized
    fun ensureObserving() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!refresher.hasWidget()) return@launch
            combine(cards.getAllWithStore(), preferences.cardSort) { cards, sort -> cards.toWidgetTiles(sort) }
                .distinctUntilChanged()
                .catch { Timber.e(it, "Card widget updater stopped observing the cards") }
                .collect { refreshIfPlaced() }
        }
    }

    private suspend fun refreshIfPlaced() {
        try {
            if (refresher.hasWidget()) refresher.refresh()
        } catch (e: Exception) {
            Timber.e(e, "Couldn't refresh the card widget")
        }
    }
}
