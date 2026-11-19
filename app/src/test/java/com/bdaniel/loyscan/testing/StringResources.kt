package com.bdaniel.loyscan.testing

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** The app's default (English) strings file and its Hungarian translation, read from the module directory. */
val DEFAULT_STRINGS = File("src/main/res/values/strings.xml")
val HUNGARIAN_STRINGS = File("src/main/res/values-hu/strings.xml")

/**
 * What is wrong with [translated] as a translation of [default] (both the text of a `strings.xml`): a translatable
 * string or plural without a translation, a translation of nothing the default translates, a string translated as a
 * plural or the other way round, a plural without `one` and `other` (Hungarian's quantities), or other format
 * arguments. A plural's `other` takes the default `other`'s arguments; its `one` may leave the count out.
 */
fun translationProblems(default: String, translated: String): List<String> {
    val defaults = parseResources(default).filterValues { it.translatable }
    val translations = parseResources(translated)
    val problems = mutableListOf<String>()
    defaults.forEach { (name, english) ->
        val translation = translations[name]
        when {
            translation == null -> problems += "$name: no translation"
            english.plural != translation.plural ->
                problems += "$name: a ${if (english.plural) "plural" else "string"} in the default strings, not a " +
                    if (english.plural) "string" else "plural"
            !english.plural -> {
                val expected = formatArguments(english.items.getValue(""))
                val actual = formatArguments(translation.items.getValue(""))
                if (actual != expected) problems += "$name: format arguments $actual, expected $expected"
            }
            else -> {
                val quantities = translation.items.keys.sorted()
                if (!quantities.containsAll(HUNGARIAN_QUANTITIES)) {
                    problems += "$name: quantities $quantities, expected $HUNGARIAN_QUANTITIES"
                }
                val expected = formatArguments(english.items["other"].orEmpty())
                translation.items.forEach { (quantity, text) ->
                    val actual = formatArguments(text)
                    when {
                        quantity == "other" && actual != expected ->
                            problems += "$name (other): format arguments $actual, expected $expected"
                        quantity != "other" && !expected.containsAll(actual) ->
                            problems += "$name ($quantity): format arguments $actual, expected some of $expected"
                    }
                }
            }
        }
    }
    (translations.keys - defaults.keys).forEach { problems += "$it: not in the default strings, or not translatable" }
    return problems
}

/** Every text in a `strings.xml` (each string, each plural item), as Android shows it: `'` is `'`. */
fun stringValues(xml: String): List<String> =
    parseResources(xml).values.flatMap { it.items.values }.map(::unescape)

/** The `<string>` called [name] in a `strings.xml`, as Android shows it (`\'` is `'`), or null when there is none. */
fun stringValue(xml: String, name: String): String? =
    parseResources(xml)[name]?.takeUnless { it.plural }?.items?.get("")?.let(::unescape)

/** A resource text as Android shows it: a backslash escape (`\'`) is the character it escapes. */
private fun unescape(text: String): String = text.replace(Regex("""\\(.)""")) { match -> match.groupValues[1] }

/** A `<string>` (its text under the key "") or a `<plurals>` (its items by quantity). */
private class Resource(val plural: Boolean, val translatable: Boolean, val items: Map<String, String>)

private fun parseResources(xml: String): Map<String, Resource> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    val children = document.documentElement.childNodes
    return (0 until children.length).map { children.item(it) }.filterIsInstance<Element>().associate { element ->
        val translatable = element.getAttribute("translatable") != "false"
        element.getAttribute("name") to when (element.tagName) {
            "plurals" -> {
                val items = element.getElementsByTagName("item")
                Resource(
                    plural = true,
                    translatable = translatable,
                    items = (0 until items.length).map { items.item(it) as Element }
                        .associate { it.getAttribute("quantity") to it.textContent },
                )
            }
            else -> Resource(plural = false, translatable = translatable, items = mapOf("" to element.textContent))
        }
    }
}

/** The format arguments in [text], `%1$s`, `%d`, sorted; `%%` is a literal percent sign, not an argument. */
private fun formatArguments(text: String): List<String> =
    Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""").findAll(text).map { it.value }.filter { it != "%%" }
        .toList().sorted()

private val HUNGARIAN_QUANTITIES = listOf("one", "other")
