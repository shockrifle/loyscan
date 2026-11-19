package com.bdaniel.loyscan.testing

/** One locale's Play store listing texts (`play/listing/<locale>/`, #75). */
data class Listing(val title: String, val shortDescription: String, val fullDescription: String)

/** A listing file's text as it is pasted into Play Console: `\r\n` read as `\n`, without the file's last newline. */
fun listingText(file: String): String = file.replace("\r\n", "\n").removeSuffix("\n")

/**
 * What is wrong with [listing] for Play (wiki/integrations/google-play.md → Store listing assets): a blank text, a text
 * over Play's limit (title 30, short description 80, full description 4000 characters, an accented letter counting as
 * one), a full description without [notAffiliatedNote] word for word, contact details (an email address, a URL or bare
 * domain, a number of 7 or more digits), an all-caps word or an emoji or symbol (`™` too) in the title, or one of
 * [retailerNames]. A retailer's name matches in any case; one of 4 or more characters also at the start of a longer
 * word ("Tescoban"), a shorter one ("dm") only as a whole word.
 */
fun listingProblems(listing: Listing, notAffiliatedNote: String, retailerNames: List<String>): List<String> {
    val texts = listOf(
        Triple("title", listing.title, 30),
        Triple("short description", listing.shortDescription, 80),
        Triple("full description", listing.fullDescription, 4000),
    )
    val problems = mutableListOf<String>()
    texts.forEach { (what, text, limit) ->
        if (text.isBlank()) {
            problems += "$what: blank"
            return@forEach
        }
        val length = text.codePointCount(0, text.length)
        if (length > limit) problems += "$what: $length characters, Play allows $limit"
        EMAIL.findAll(text).forEach { problems += "$what: an email address (${it.value})" }
        URL.findAll(text).forEach { problems += "$what: a URL (${it.value})" }
        LONG_NUMBER.findAll(text).forEach { problems += "$what: a number of 7 or more digits (${it.value})" }
        retailerNames.distinctBy { it.lowercase() }.filter { mentions(text, it) }
            .forEach { problems += "$what: names a retailer ($it)" }
    }
    if (listing.fullDescription.isNotBlank() && notAffiliatedNote !in listing.fullDescription) {
        problems += "full description: no \"not affiliated\" note"
    }
    ALL_CAPS_WORD.findAll(listing.title).forEach { problems += "title: an all-caps word (${it.value})" }
    listing.title.codePoints().filter(::isEmoji).forEach { problems += "title: an emoji or symbol (${String(Character.toChars(it))})" }
    return problems
}

private fun mentions(text: String, name: String): Boolean {
    val end = if (name.length >= 4) "" else """(?![\p{L}\p{N}])"""
    return Regex("""(?<![\p{L}\p{N}])${Regex.escape(name)}$end""", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

private fun isEmoji(codePoint: Int): Boolean =
    Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt() || codePoint in 0x1F000..0x1FAFF

private val EMAIL = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
/** A link with a scheme or `www.`, or a bare domain: words joined by dots, ending in a lower-case TLD of 2+ letters. */
private val URL = Regex(
    """(?i:https?://|www\.)[^\s]*[^\s.,;:!?)]|(?<![\p{L}\p{N}@.-])[\p{L}\p{N}-]+(\.[\p{L}\p{N}-]+)*\.[a-z]{2,}(?![\p{L}\p{N}])""",
)
private val LONG_NUMBER = Regex("""\d([ -]?\d){6,}""")
private val ALL_CAPS_WORD = Regex("""(?<![\p{L}\p{N}])\p{Lu}{2,}(?![\p{L}\p{N}])""")
