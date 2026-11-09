package com.bdaniel.loyscan

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.bdaniel.loyscan.testing.DEFAULT_STRINGS
import com.bdaniel.loyscan.testing.HUNGARIAN_STRINGS
import com.bdaniel.loyscan.testing.translationProblems
import org.junit.Test

/**
 * Every UI string has a Hungarian translation (#72): `values-hu` translates each default string and plural, with the
 * same format arguments, and holds nothing the default lacks. English stays the default. The rules are checked on
 * small XML snippets first, then on the app's own files.
 */
class StringResourcesTest {

    @Test
    fun theAppsHungarianStrings_translateEveryDefaultString() {
        val problems = translationProblems(
            default = DEFAULT_STRINGS.readText(),
            translated = HUNGARIAN_STRINGS.readText(),
        )

        assertWithMessage("values-hu/strings.xml against values/strings.xml").that(problems).isEmpty()
    }

    @Test
    fun aCompleteTranslation_hasNoProblems() {
        val problems = translationProblems(
            default = resources("""<string name="title">My Cards</string><plurals name="selected"><item quantity="one">%d selected</item><item quantity="other">%d selected</item></plurals>"""),
            translated = resources("""<string name="title">Kártyáim</string><plurals name="selected"><item quantity="one">%d kijelölve</item><item quantity="other">%d kijelölve</item></plurals>"""),
        )

        assertThat(problems).isEmpty()
    }

    @Test
    fun aStringWithoutATranslation_isAProblem() {
        val problems = translationProblems(
            default = resources("""<string name="title">My Cards</string><string name="search">Search cards</string>"""),
            translated = resources("""<string name="title">Kártyáim</string>"""),
        )

        assertThat(problems).containsExactly("search: no translation")
    }

    @Test
    fun aPluralWithoutATranslation_isAProblem() {
        val problems = translationProblems(
            default = resources("""<plurals name="selected"><item quantity="other">%d selected</item></plurals>"""),
            translated = resources(""),
        )

        assertThat(problems).containsExactly("selected: no translation")
    }

    @Test
    fun anUntranslatableString_needsNoTranslation() {
        val problems = translationProblems(
            default = resources("""<string name="app_name" translatable="false">LoyScan</string>"""),
            translated = resources(""),
        )

        assertThat(problems).isEmpty()
    }

    @Test
    fun aTranslationOfAnUntranslatableString_isAProblem() {
        val problems = translationProblems(
            default = resources("""<string name="app_name" translatable="false">LoyScan</string>"""),
            translated = resources("""<string name="app_name">LoyScan</string>"""),
        )

        assertThat(problems).containsExactly("app_name: not in the default strings, or not translatable")
    }

    @Test
    fun aTranslationOfNoDefaultString_isAProblem() {
        val problems = translationProblems(
            default = resources(""),
            translated = resources("""<string name="gone">Régi</string>"""),
        )

        assertThat(problems).containsExactly("gone: not in the default strings, or not translatable")
    }

    @Test
    fun aTranslationWithOtherFormatArguments_isAProblem() {
        val problems = translationProblems(
            default = resources("""<string name="no_match">No card matches “%1${'$'}s”</string>"""),
            translated = resources("""<string name="no_match">Nincs találat: %1${'$'}d</string>"""),
        )

        assertThat(problems).containsExactly("no_match: format arguments [%1\$d], expected [%1\$s]")
    }

    @Test
    fun aTranslationMissingAFormatArgument_isAProblem() {
        val problems = translationProblems(
            default = resources("""<string name="shown_as">Shown as %1${'$'}s</string>"""),
            translated = resources("""<string name="shown_as">Más formában</string>"""),
        )

        assertThat(problems).containsExactly("shown_as: format arguments [], expected [%1\$s]")
    }

    @Test
    fun aPluralTranslatedAsAString_isAProblem() {
        val problems = translationProblems(
            default = resources("""<plurals name="selected"><item quantity="other">%d selected</item></plurals>"""),
            translated = resources("""<string name="selected">%d kijelölve</string>"""),
        )

        assertThat(problems).containsExactly("selected: a plural in the default strings, not a string")
    }

    @Test
    fun aHungarianPluralWithoutOneOrOther_isAProblem() {
        val problems = translationProblems(
            default = resources("""<plurals name="selected"><item quantity="one">%d selected</item><item quantity="other">%d selected</item></plurals>"""),
            translated = resources("""<plurals name="selected"><item quantity="other">%d kijelölve</item></plurals>"""),
        )

        assertThat(problems).containsExactly("selected: quantities [other], expected [one, other]")
    }

    @Test
    fun aPluralsOtherWithOtherFormatArguments_isAProblem() {
        val problems = translationProblems(
            default = resources("""<plurals name="delete"><item quantity="one">Delete this card?</item><item quantity="other">Delete %d cards?</item></plurals>"""),
            translated = resources("""<plurals name="delete"><item quantity="one">Törlöd ezt a kártyát?</item><item quantity="other">Törlöd a kártyákat?</item></plurals>"""),
        )

        assertThat(problems).containsExactly("delete (other): format arguments [], expected [%d]")
    }

    @Test
    fun aPluralsOne_mayLeaveOutTheCount_butNotAddAnArgument() {
        val leftOut = translationProblems(
            default = resources("""<plurals name="delete"><item quantity="one">Delete %d card?</item><item quantity="other">Delete %d cards?</item></plurals>"""),
            translated = resources("""<plurals name="delete"><item quantity="one">Törlöd ezt a kártyát?</item><item quantity="other">Törölsz %d kártyát?</item></plurals>"""),
        )
        val added = translationProblems(
            default = resources("""<plurals name="delete"><item quantity="one">Delete this card?</item><item quantity="other">Delete %d cards?</item></plurals>"""),
            translated = resources("""<plurals name="delete"><item quantity="one">Törlöd ezt a(z) %s kártyát?</item><item quantity="other">Törölsz %d kártyát?</item></plurals>"""),
        )

        assertThat(leftOut).isEmpty()
        assertThat(added).containsExactly("delete (one): format arguments [%s], expected some of [%d]")
    }

    private fun resources(body: String) = """<?xml version="1.0" encoding="utf-8"?><resources>$body</resources>"""
}
