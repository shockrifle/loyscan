package com.bdaniel.loyscan

import com.bdaniel.loyscan.domain.catalogue.StoreCatalogue
import com.bdaniel.loyscan.testing.DEFAULT_STRINGS
import com.bdaniel.loyscan.testing.HUNGARIAN_STRINGS
import com.bdaniel.loyscan.testing.Listing
import com.bdaniel.loyscan.testing.listingProblems
import com.bdaniel.loyscan.testing.listingText
import com.bdaniel.loyscan.testing.stringValue
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The Play store listing texts in `play/listing/<locale>/` (#75): what the owner pastes into Play Console. Each locale
 * has a title, a short and a full description within Play's limits; the full description carries the app's own
 * "not affiliated" note; and no text holds contact details or names a catalogue retailer. The rules are checked on
 * small snippets first, then on the real files. wiki/integrations/google-play.md → Store listing assets.
 */
class StoreListingTest {

    // ---- The real listing ----

    @Test
    fun theListing_hasExactlyTheEnglishAndHungarianLocales_eachWithItsThreeTexts() {
        val locales = LISTING.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.sorted()

        assertWithMessage("locale folders in $LISTING").that(locales).containsExactly("en-US", "hu-HU")
        LOCALES.keys.forEach { locale ->
            TEXT_FILES.forEach { name ->
                assertWithMessage("$locale/$name").that(File(LISTING, "$locale/$name").isFile).isTrue()
            }
        }
    }

    @Test
    fun everyLocale_followsTheListingRules() {
        val problems = LOCALES.flatMap { (locale, strings) ->
            val note = checkNotNull(stringValue(strings.readText(), "about_not_affiliated")) { "no note in $strings" }
            listingProblems(listing(locale), notAffiliatedNote = note, retailerNames = RETAILER_NAMES).map { "$locale $it" }
        }

        assertWithMessage("play/listing").that(problems).isEmpty()
    }

    @Test
    fun theHungarianListing_isInHungarian() {
        val english = listing("en-US")
        val hungarian = listing("hu-HU")

        assertThat(hungarian.fullDescription).isNotEqualTo(english.fullDescription)
        assertThat(hungarian.title).contains("kártyák")
        assertThat(hungarian.shortDescription).contains("kártyáid")
        assertThat(hungarian.fullDescription).contains("A kasszánál")
    }

    @Test
    fun theRetailerNames_comeFromTheShippedCatalogue() {
        // A control: an empty list would let every retailer through.
        assertThat(RETAILER_NAMES).containsAtLeast("Tesco", "Clubcard", "dm", "SPAR")
    }

    // ---- The rules, on snippets ----

    @Test
    fun aListingWithinTheRules_hasNoProblems() {
        assertThat(listingProblems(A_LISTING, NOTE, RETAILERS)).isEmpty()
    }

    @Test
    fun aBlankText_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "  "), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: blank")
    }

    @Test
    fun aTitleOf31Characters_isAProblem() {
        val title = "LoyScan cards for every pocket!" // 31

        val problems = listingProblems(A_LISTING.copy(title = title), NOTE, RETAILERS)

        assertThat(problems).containsExactly("title: 31 characters, Play allows 30")
    }

    @Test
    fun aShortDescriptionOf81Characters_isAProblem() {
        val short = "Keep each of your loyalty cards on your phone and show them at the till in a tap." // 81

        val problems = listingProblems(A_LISTING.copy(shortDescription = short), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: 81 characters, Play allows 80")
    }

    @Test
    fun aFullDescriptionOver4000Characters_isAProblem() {
        val full = "a".repeat(4001 - NOTE.length - 1) + " " + NOTE // 4001

        val problems = listingProblems(A_LISTING.copy(fullDescription = full), NOTE, RETAILERS)

        assertThat(problems).containsExactly("full description: 4001 characters, Play allows 4000")
    }

    @Test
    fun anAccentedLetter_countsAsOneCharacter() {
        val title = "Őrző kártyák és ünnepi ízek ők" // 30 letters, 39 bytes in UTF-8

        val problems = listingProblems(A_LISTING.copy(title = title), NOTE, RETAILERS)

        assertThat(problems).isEmpty()
    }

    @Test
    fun aFullDescriptionWithoutTheNote_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(fullDescription = "Keep your cards on your phone."), NOTE, RETAILERS)

        assertThat(problems).containsExactly("full description: no \"not affiliated\" note")
    }

    @Test
    fun anEmailAddress_isAProblem() {
        val full = "Write to someone@example.com.\n\n$NOTE"

        val problems = listingProblems(A_LISTING.copy(fullDescription = full), NOTE, RETAILERS)

        assertThat(problems).containsExactly("full description: an email address (someone@example.com)")
    }

    @Test
    fun aUrl_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "See www.example.com"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: a URL (www.example.com)")
    }

    @Test
    fun aLinkWithItsScheme_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "See https://example.org/x"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: a URL (https://example.org/x)")
    }

    @Test
    fun aPhoneNumber_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "Call +36 30-123 4567"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: a number of 7 or more digits (36 30-123 4567)")
    }

    @Test
    fun shortNumbers_areNotAProblem() {
        val short = "Reads EAN-13, Code 128 and 2 more of 12 formats in 2026."

        val problems = listingProblems(A_LISTING.copy(shortDescription = short), NOTE, RETAILERS)

        assertThat(problems).isEmpty()
    }

    @Test
    fun anAllCapsWordInTheTitle_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(title = "LoyScan: FREE cards"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("title: an all-caps word (FREE)")
    }

    @Test
    fun anEmojiInTheTitle_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(title = "LoyScan 💳 cards"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("title: an emoji (💳)")
    }

    @Test
    fun aRetailersName_isAProblem_inAnyCase() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "Works with your SHOPRITE card"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: names a retailer (Shoprite)")
    }

    @Test
    fun aRetailersName_withAHungarianSuffix_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "Fizess a Shopriteban is"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: names a retailer (Shoprite)")
    }

    @Test
    fun aShortRetailerName_isAProblemOnlyAsAWholeWord() {
        val clean = listingProblems(A_LISTING.copy(shortDescription = "Adam's cards, kept handy"), NOTE, RETAILERS)
        val named = listingProblems(A_LISTING.copy(shortDescription = "Your am card, kept handy"), NOTE, RETAILERS)

        assertThat(clean).isEmpty()
        assertThat(named).containsExactly("short description: names a retailer (am)")
    }

    @Test
    fun aListingText_dropsItsLastNewline_andReadsCrlfAsOneLineBreak() {
        assertThat(listingText("Line one\r\nLine two\r\n")).isEqualTo("Line one\nLine two")
    }

    private companion object {
        /** The listing folder, from the module directory (the test's working directory). */
        val LISTING = File("../play/listing")
        val LOCALES = mapOf("en-US" to DEFAULT_STRINGS, "hu-HU" to HUNGARIAN_STRINGS)
        val TEXT_FILES = listOf("title.txt", "short_description.txt", "full_description.txt")

        val RETAILER_NAMES: List<String> =
            StoreCatalogue.parse(File("src/main/assets/catalogue/stores.json").readText())
                .flatMap { listOf(it.name) + it.aliases }

        fun listing(locale: String) = Listing(
            title = listingText(File(LISTING, "$locale/title.txt").readText()),
            shortDescription = listingText(File(LISTING, "$locale/short_description.txt").readText()),
            fullDescription = listingText(File(LISTING, "$locale/full_description.txt").readText()),
        )

        /** Made-up retailers: never a catalogue store, so the snippets don't depend on the catalogue. */
        val RETAILERS = listOf("Shoprite", "am")
        const val NOTE = "Not affiliated with the retailers."
        val A_LISTING = Listing(
            title = "LoyScan: Loyalty Cards",
            shortDescription = "Your loyalty cards on your phone.",
            fullDescription = "Keep your cards on your phone.\n\n$NOTE",
        )
    }
}
