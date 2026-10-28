package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.testing.SOME_INSTANT
import com.bdaniel.loyscan.testing.aLoyaltyCard
import com.bdaniel.loyscan.testing.aStore
import com.bdaniel.loyscan.testing.cardId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardSearchTest {

    private val apple = aLoyaltyCard(id = cardId(1), store = aStore(id = "apple", name = "Apple Market", aliases = listOf("AMK")))
    private val berry = aLoyaltyCard(id = cardId(2), store = aStore(id = "berry", name = "berry shop"), label = "Family")
    private val cafe = aLoyaltyCard(id = cardId(3), store = aStore(id = "cafe", name = "Café Ödön"), note = "Gold tier")

    private fun List<LoyaltyCard>.ids() = map { it.id }

    @Test
    fun byName_sortsByStoreName_ignoringCaseAndAccents() {
        val ada = aLoyaltyCard(id = cardId(4), store = aStore(id = "ada", name = "Ádám"))

        assertThat(listOf(cafe, berry, ada, apple).sortedBy(CardSort.NAME).ids())
            .containsExactly(cardId(4), cardId(1), cardId(2), cardId(3)).inOrder()
    }

    @Test
    fun byName_sortsCardsOfTheSameStoreByLabel_unlabelledFirst() {
        val store = aStore(name = "Shop")
        val work = aLoyaltyCard(id = cardId(1), store = store, label = "Work")
        val home = aLoyaltyCard(id = cardId(2), store = store, label = "home")
        val unlabelled = aLoyaltyCard(id = cardId(3), store = store, label = null)

        assertThat(listOf(work, home, unlabelled).sortedBy(CardSort.NAME).ids())
            .containsExactly(cardId(3), cardId(2), cardId(1)).inOrder()
    }

    @Test
    fun mostUsed_sortsByUseCountDescending_thenByName() {
        val a = apple.copy(useCount = 3)
        val b = berry.copy(useCount = 7)
        val c = cafe.copy(useCount = 3)

        assertThat(listOf(c, a, b).sortedBy(CardSort.MOST_USED).ids())
            .containsExactly(cardId(2), cardId(1), cardId(3)).inOrder()
    }

    @Test
    fun recentlyUsed_sortsByLastUseDescending_neverUsedLast_thenByName() {
        val a = apple.copy(lastUsedAt = SOME_INSTANT)
        val b = berry.copy(lastUsedAt = null)
        val c = cafe.copy(lastUsedAt = SOME_INSTANT.plusSeconds(60))
        val d = aLoyaltyCard(id = cardId(4), store = aStore(name = "Zed"), lastUsedAt = SOME_INSTANT)

        assertThat(listOf(b, d, a, c).sortedBy(CardSort.RECENTLY_USED).ids())
            .containsExactly(cardId(3), cardId(1), cardId(4), cardId(2)).inOrder()
    }

    @Test
    fun blankQuery_returnsEveryCard() {
        assertThat(listOf(apple, berry, cafe).search("  ")).containsExactly(apple, berry, cafe).inOrder()
    }

    @Test
    fun query_matchesTheStoreName_ignoringCaseAndAccents() {
        assertThat(listOf(apple, berry, cafe).search("ODON")).containsExactly(cafe)
    }

    @Test
    fun query_matchesAStoreAlias() {
        assertThat(listOf(apple, berry, cafe).search("amk")).containsExactly(apple)
    }

    @Test
    fun query_matchesTheLabel() {
        assertThat(listOf(apple, berry, cafe).search("famil")).containsExactly(berry)
    }

    @Test
    fun query_matchesTheNote() {
        assertThat(listOf(apple, berry, cafe).search("gold")).containsExactly(cafe)
    }

    @Test
    fun query_doesNotMatchTheNumber() {
        val card = apple.copy(number = "987654")

        assertThat(listOf(card).search("9876")).isEmpty()
    }

    @Test
    fun query_keepsTheOrderItIsGiven() {
        val market = berry.copy(label = "Market")

        assertThat(listOf(market, apple).search("market").ids()).containsExactly(cardId(2), cardId(1)).inOrder()
    }
}
