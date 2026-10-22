package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoreSearchTest {

    private val apple = aStore(id = "apple", name = "Apple Market", aliases = listOf("AM"))
    private val berry = aStore(id = "berry", name = "berry shop")
    private val cafe = aStore(id = "cafe", name = "Café Ödön", aliases = listOf("Kávézó"))

    @Test
    fun blankQuery_returnsEveryStoreByName() {
        assertThat(listOf(cafe, berry, apple).search("  ").map { it.id }).containsExactly("apple", "berry", "cafe").inOrder()
    }

    @Test
    fun query_matchesAnyPartOfTheName_ignoringCase() {
        assertThat(listOf(apple, berry, cafe).search("MARK")).containsExactly(apple)
    }

    @Test
    fun query_matchesAnAlias() {
        assertThat(listOf(apple, berry, cafe).search("am")).containsExactly(apple)
    }

    @Test
    fun query_ignoresAccents_inTheQueryAndInTheStore() {
        assertThat(listOf(apple, berry, cafe).search("odon")).containsExactly(cafe)
        assertThat(listOf(apple, berry, cafe).search("kavezo")).containsExactly(cafe)
        assertThat(listOf(apple, berry, cafe).search("bérry")).containsExactly(berry)
    }

    @Test
    fun query_thatMatchesNothing_returnsNoStores() {
        assertThat(listOf(apple, berry, cafe).search("xyz")).isEmpty()
    }

    @Test
    fun results_areSortedByName_ignoringCaseAndAccents() {
        val ab = aStore(id = "ab", name = "Ábc")
        val ac = aStore(id = "ac", name = "acd")
        val aa = aStore(id = "aa", name = "Aaa")

        assertThat(listOf(ac, ab, aa).search("").map { it.id }).containsExactly("aa", "ab", "ac").inOrder()
    }
}
