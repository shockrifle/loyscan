package com.bdaniel.loyscan.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage

/**
 * The texts a screen has shown in a test, gathered with [collect] as the test walks the screen through its states:
 * every node's text, content description and action labels (click, long click, custom actions), in every window
 * (dialogs and menus too). [assertEveryTextIsTranslated] then checks them against `values-hu` (#72).
 */
class ShownTexts(private val rule: ComposeTestRule) {

    val seen = mutableSetOf<String>()

    /** Adds what the screen shows now. */
    fun collect() {
        rule.waitForIdle()
        rule.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .forEach { node ->
                val config = node.config
                config.getOrNull(SemanticsProperties.Text)?.forEach { seen += it.text }
                config.getOrNull(SemanticsProperties.ContentDescription)?.let { seen += it }
                config.getOrNull(SemanticsActions.OnClick)?.label?.let { seen += it }
                config.getOrNull(SemanticsActions.OnLongClick)?.label?.let { seen += it }
                config.getOrNull(SemanticsActions.CustomActions)?.forEach { seen += it.label }
            }
    }

    /**
     * Every text seen is either [userData] (store names, labels, numbers: not translated) or a Hungarian string from
     * `values-hu/strings.xml`, its format arguments matching anything. An English text left in the code fails here.
     */
    fun assertEveryTextIsTranslated(vararg userData: String) {
        val hungarian = stringValues(HUNGARIAN_STRINGS.readText()).map { value ->
            Regex(value.split(FORMAT_ARGUMENT).joinToString(".+") { Regex.escape(it) })
        }
        val texts = seen.filter { it.isNotBlank() }
        assertWithMessage("texts collected from the screen").that(texts).isNotEmpty()
        val untranslated = texts.filter { text -> text !in userData && hungarian.none { it.matches(text) } }
        assertWithMessage("texts that are neither user data nor in values-hu").that(untranslated).isEmpty()
    }

    /** The screen showed [texts], written by hand in the test. */
    fun assertSeen(vararg texts: String) {
        assertThat(seen).containsAtLeastElementsIn(texts)
    }

    private companion object {
        val FORMAT_ARGUMENT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
    }
}
