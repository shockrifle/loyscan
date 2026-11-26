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

    /** A fake [CardWidgetRefresher]: counts refreshes, says whether a widget is placed, can throw once. */
    private class FakeRefresher(var hasWidget: Boolean = true) : CardWidgetRefresher {
        var refreshes = 0
            private set
        var failNextRefresh = false
        override suspend fun hasWidget() = hasWidget
        override suspend fun refresh() {
            if (failNextRefresh) { failNextRefresh = false; throw RuntimeException("boom") }
            refreshes++
        }
    }

    @Test
    fun refresh_runsWhenACardChangesTheTiles_whileAWidgetIsPlaced() = runTest {
        val cards = FakeLoyaltyCardRepository()
        val refresher = FakeRefresher(hasWidget = true)

        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        runCurrent()
        val afterFirstRender = refresher.refreshes

        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
        runCurrent()

        assertThat(refresher.refreshes).isGreaterThan(afterFirstRender)
    }

    @Test
    fun refresh_runsWhenAStoresLogoIsSet_changed_orRemoved() = runTest { // #181: the tiles show it
        val card = aLoyaltyCard(id = cardId(1), label = "Tesco")
        val cards = FakeLoyaltyCardRepository(listOf(card))
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        runCurrent()

        for (logo in listOf("logo-1", "logo-2", null)) {
            val before = refresher.refreshes
            cards.cards.value = listOf(card.copy(store = card.store.copy(logo = logo)))
            runCurrent()

            assertThat(refresher.refreshes).isGreaterThan(before)
        }
    }

    @Test
    fun refresh_runsWhenTheSortChanges() = runTest {
        val cards = FakeLoyaltyCardRepository(
            listOf(aLoyaltyCard(id = cardId(1), label = "A", useCount = 1), aLoyaltyCard(id = cardId(2), label = "B", useCount = 9))
        )
        val preferences = FakeUserPreferencesRepository(CardSort.NAME)
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, preferences, refresher, backgroundScope).ensureObserving()
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
        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        runCurrent()
        val before = refresher.refreshes

        // A field a tile doesn't show (the note) changes; the tiles are unchanged, so no widget refresh.
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco", note = "changed"))
        runCurrent()

        assertThat(refresher.refreshes).isEqualTo(before)
    }

    @Test
    fun refresh_neverRunsAndNoCardsAreRead_whenNoWidgetIsPlaced() = runTest {
        val cards = FakeLoyaltyCardRepository()
        val refresher = FakeRefresher(hasWidget = false)

        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
        runCurrent()

        assertThat(refresher.refreshes).isEqualTo(0)
    }

    @Test
    fun observing_startsAfterAWidgetIsPlacedWhileTheAppIsAlreadyRunning() { // #11: the receiver re-triggers it
        runTest {
            val cards = FakeLoyaltyCardRepository()
            val refresher = FakeRefresher(hasWidget = false) // no widget when the app started
            val updater = CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope)

            updater.ensureObserving()
            runCurrent() // nothing observed yet

            refresher.hasWidget = true // the user places a widget; the receiver calls ensureObserving again
            updater.ensureObserving()
            runCurrent()
            cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
            runCurrent()

            assertThat(refresher.refreshes).isGreaterThan(0)
        }
    }

    @Test
    fun aRefreshFailure_isLoggedNotThrown_andObservationContinues() { // #11: the widget must not crash the app
        runTest {
            val cards = FakeLoyaltyCardRepository()
            val refresher = FakeRefresher(hasWidget = true).apply { failNextRefresh = true }

            CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
            runCurrent() // the first refresh throws and is swallowed

            cards.cards.value = listOf(aLoyaltyCard(id = cardId(1), label = "Tesco"))
            runCurrent()

            // The thrown refresh didn't end the observation: a later change still refreshes.
            assertThat(refresher.refreshes).isGreaterThan(0)
        }
    }

    @Test
    fun refresh_runsWhenACardsShowPhotoOnWidgetIsToggled() = runTest { // #185: the tile shows the photo
        val card = aLoyaltyCard(id = cardId(1), label = "Tesco", frontPhotoPath = "front")
        val cards = FakeLoyaltyCardRepository(listOf(card))
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        runCurrent()

        for (show in listOf(true, false)) {
            val before = refresher.refreshes
            cards.cards.value = listOf(card.copy(showPhotoOnWidget = show))
            runCurrent()

            assertThat(refresher.refreshes).isGreaterThan(before)
        }
    }

    @Test
    fun refresh_doesNotRun_whenTheSwitchChangesOnACardWithNoFrontPhoto_orOnlyMyCardsChanges() = runTest { // #185
        val noPhoto = aLoyaltyCard(id = cardId(1), label = "Tesco")
        val withPhoto = aLoyaltyCard(id = cardId(2), label = "Aldi", frontPhotoPath = "front")
        val cards = FakeLoyaltyCardRepository(listOf(noPhoto, withPhoto))
        val refresher = FakeRefresher(hasWidget = true)
        CardWidgetUpdater(cards, FakeUserPreferencesRepository(), refresher, backgroundScope).ensureObserving()
        runCurrent()
        val before = refresher.refreshes

        cards.cards.value = listOf(noPhoto.copy(showPhotoOnWidget = true), withPhoto.copy(showPhotoOnList = true))
        runCurrent()

        assertThat(refresher.refreshes).isEqualTo(before)
    }
}
