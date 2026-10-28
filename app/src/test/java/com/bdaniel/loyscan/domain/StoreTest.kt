package com.bdaniel.loyscan.domain

import com.bdaniel.loyscan.testing.aStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StoreTest {

    @Test
    fun initials_ofATwoWordName_areTheFirstLetterOfEach() {
        assertThat(aStore(name = "Shell ClubSmart").initials).isEqualTo("SC")
    }

    @Test
    fun initials_ofAOneWordName_areItsFirstLetter_inUpperCase() {
        assertThat(aStore(name = "dm").initials).isEqualTo("D")
        assertThat(aStore(name = "mömax").initials).isEqualTo("M")
    }

    @Test
    fun initials_takeAtMostTwoWords_andIgnoreExtraSpaces() {
        assertThat(aStore(name = "  Corner   Shop Loyalty Club ").initials).isEqualTo("CS")
    }

    @Test
    fun initials_skipWhatIsNotALetterOrDigit() {
        assertThat(aStore(name = "(Best) - 4you").initials).isEqualTo("B4")
    }

    @Test
    fun initials_ofABlankName_areEmpty() {
        assertThat(aStore(name = " ").initials).isEmpty()
    }
}
