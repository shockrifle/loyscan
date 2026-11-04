package com.bdaniel.loyscan.widget

import com.bdaniel.loyscan.domain.CardSort
import com.bdaniel.loyscan.testing.FakeLoyaltyCardRepository
import com.bdaniel.loyscan.testing.FakeUserPreferencesRepository
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The widget's live-update trigger (#11): it refreshes the widget when the tiles change, and only then. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardWidgetUpdaterTest {

    /** A fake [CardWidgetRefresher]: counts refreshes, and says whether a widget is placed. */
    private class FakeRefresher(private val hasWidget: Boolean) : CardWidgetRefresher {
        var refreshes = 0
            private set
        override suspend fun hasWidget() = hasWidget
        override suspend fun refresh() { refreshes++ }
    }

    @Test
    fun refresh_runsWhenACardChangesTheTiles_whileAWidgetIsPlaced() = runTest {
        val cards = FakeLoyaltyCardRepository()
        val refresher = FakeRefresher(hasWidget = true)

        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher).start(backgroundScope)
        runCurrent()
        val afterFirstRender = refresher.refreshes

        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
        runCurrent()

        assertThat(refresher.refreshes).isGreaterThan(afterFirstRender)
    }

    @Test
    fun refresh_runsWhenTheSortChanges() = runTest {
        val cards = FakeLoyaltyCardRepository(
            listOf(aLoyaltyCard(id = cardId(1), label = "A", useCount = 1), aLoyaltyCard(id = cardId(2), label = "B", useCount = 9))
        )
        val preferences = FakeUserPreferencesRepository(CardSort.NAME)
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, preferences, refresher).start(backgroundScope)
        runCurrent()
        val before = refresher.refreshes

        preferences.cardSort.value = CardSort.MOST_USED
        runCurrent()

        assertThat(refresher.refreshes).isGreaterThan(before)
    }

    @Test
    fun refresh_doesNotRunForAChangeThatLeavesTheTilesTheSame() = runTest {
        val cards = FakeLoyaltyCardRepository(listOf(aLoyaltyCard(id = cardId(1), label = "Tesco")))
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher).start(backgroundScope)
        runCurrent()
        val before = refresher.refreshes

        // A field a tile doesn't show (the note) changes; the tiles are unchanged, so no widget refresh.
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco", note = "changed"))
        runCurrent()

        assertThat(refresher.refreshes).isEqualTo(before)
    }

    @Test
    fun refresh_neverRunsWhenNoWidgetIsPlaced() = runTest {
        val cards = FakeLoyaltyCardRepository()
        val refresher = FakeRefresher(hasWidget = false)

        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher).start(backgroundScope)
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
        runCurrent()

        assertThat(refresher.refreshes).isEqualTo(0)
    }
}
