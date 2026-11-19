package com.bdaniel.loyscan

import com.bdaniel.loyscan.data.local.db.ShippedCatalogueTest
import com.bdaniel.loyscan.domain.catalogue.StoreCatalogue
import com.bdaniel.loyscan.testing.DEFAULT_STRINGS
import com.bdaniel.loyscan.testing.HUNGARIAN_STRINGS
import com.bdaniel.loyscan.testing.Listing
import com.bdaniel.loyscan.testing.ListingClaim
import com.bdaniel.loyscan.testing.bulletsPerSection
import com.bdaniel.loyscan.testing.claimProblems
import com.bdaniel.loyscan.testing.listingProblems
import com.bdaniel.loyscan.testing.listingText
import com.bdaniel.loyscan.testing.stringValue
import com.bdaniel.loyscan.testing.tests
import com.bdaniel.loyscan.ui.scanner.CameraXScannerTest
import com.bdaniel.loyscan.widget.CardWidgetContentTest
import com.bdaniel.loyscan.widget.CardWidgetInfoTest
import com.bdaniel.loyscan.testing.testsNotNamedIn
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

    @Test
    fun everyClaimOfTheListing_isBackedByNamedTests() { // #178
        assertWithMessage("LISTING_CLAIMS against play/listing/en-US").that(claimProblems(listing("en-US"), LISTING_CLAIMS)).isEmpty()
    }

    @Test
    fun theHungarianListing_hasTheEnglishBullets_sectionBySection() { // #178: so a Hungarian-only claim can't slip past
        assertThat(bulletsPerSection(listing("hu-HU").fullDescription))
            .isEqualTo(bulletsPerSection(listing("en-US").fullDescription))
    }

    @Test
    fun theTestsBehindTheClaims_areNamedOnTheGooglePlayWikiPage() { // #178: the wiki's table can't drift from the list
        val page = GOOGLE_PLAY_WIKI.readText()

        assertWithMessage("tests missing from $GOOGLE_PLAY_WIKI")
            .that(testsNotNamedIn(page, LISTING_CLAIMS.flatMap { it.tests })).isEmpty()
    }

    // ---- The rules, on snippets ----

    @Test
    fun claimsCoveringEveryBullet_haveNoProblems() {
        val claims = listOf(ListingClaim("Scan a card.", listOf("A.a")), ListingClaim("Offline", listOf("A.b")))

        assertThat(claimProblems(CLAIMED_LISTING, claims)).isEmpty()
    }

    @Test
    fun aBulletNoClaimHas_isAProblem() {
        val claims = listOf(ListingClaim("Offline", listOf("A.b")))

        assertThat(claimProblems(CLAIMED_LISTING, claims)).containsExactly("no claim for the bullet: Scan a card.")
    }

    @Test
    fun aClaimTheListingNoLongerMakes_isAProblem() {
        val claims = listOf(
            ListingClaim("Scan a card.", listOf("A.a")),
            ListingClaim("Offline", listOf("A.b")),
            ListingClaim("Sync to the cloud.", listOf("A.c")),
        )

        assertThat(claimProblems(CLAIMED_LISTING, claims)).containsExactly("not in the listing: Sync to the cloud.")
    }

    @Test
    fun aClaimWithoutATest_isAProblem() {
        val claims = listOf(ListingClaim("Scan a card.", emptyList()), ListingClaim("Offline", listOf("A.b")))

        assertThat(claimProblems(CLAIMED_LISTING, claims)).containsExactly("no test for: Scan a card.")
    }

    @Test
    fun bullets_areCountedPerSection() {
        assertThat(bulletsPerSection("Intro.\n\nAdd\n• one\n• two\n\nKeep\n• three\n\nEnd.")).containsExactly(0, 2, 1, 0).inOrder()
    }

    @Test
    fun aTestNotNamedInBackticks_isMissingFromThePage() {
        val page = "| Scan | `ScannerTest.scans_once` |"

        assertThat(testsNotNamedIn(page, listOf("ScannerTest.scans_once", "ScannerTest.scans"))).containsExactly("ScannerTest.scans")
    }

    @Test
    fun aClassesTests_areNamedClassDotMethod() {
        assertThat(StoreListingTest::class.tests(StoreListingTest::aClaimWithoutATest_isAProblem))
            .containsExactly("StoreListingTest.aClaimWithoutATest_isAProblem")
    }


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
    fun aBareDomain_isAProblem() {
        val problems = listingProblems(A_LISTING.copy(shortDescription = "Visit loyscan.hu, e.g. today."), NOTE, RETAILERS)

        assertThat(problems).containsExactly("short description: a URL (loyscan.hu)")
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

        assertThat(problems).containsExactly("title: an emoji or symbol (💳)")
    }

    @Test
    fun aTrademarkSignInTheTitle_isNamedAsASymbol() {
        val problems = listingProblems(A_LISTING.copy(title = "LoyScan™ cards"), NOTE, RETAILERS)

        assertThat(problems).containsExactly("title: an emoji or symbol (™)")
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
        /** The google-play wiki page, whose table names the tests behind each claim. */
        val GOOGLE_PLAY_WIKI = File("../wiki/integrations/google-play.md")

        val NO_NETWORK = AppPermissionsTest::class.tests(AppPermissionsTest::theApp_doesNotAskForNetworkAccess)

        /** Every feature the listing claims, with the tests that keep it true: wiki/integrations/google-play.md. */
        val LISTING_CLAIMS: List<ListingClaim> = listOf(
            ListingClaim(
                "the cashier scans the barcode from your screen",
                DetailScreenTest::class.tests(DetailScreenTest::showsTheCardsBarcode),
            ),
            ListingClaim(
                "Scan the card's barcode with the camera, read it from a picture, or type the number.",
                CameraXScannerTest::class.tests(CameraXScannerTest::aBarcodeInAFrame_isReportedAsScanned) +
                    ScannerScreenTest::class.tests(ScannerScreenTest::aPictureWithABarcode_isHandedOnAsAScan) +
                    AppNavigationTest::class.tests(AppNavigationTest::addingACardByHand_savesIt_opensIt_andBackReturnsToTheListWithIt),
            ),
            ListingClaim(
                "Pick the store from the built-in list of Hungarian stores, or add your own with a name, colour and barcode type.",
                ShippedCatalogueTest::class.tests(
                    ShippedCatalogueTest::shippedCatalogue_loadsIntoTheDatabase,
                    ShippedCatalogueTest::everyShippedStore_isAHungarianStore,
                ) +
                    StorePickerScreenTest::class.tests(StorePickerScreenTest::tappingAStore_picksIt) +
                    CustomStoreScreenTest::class.tests(
                        CustomStoreScreenTest::creatingAStore_savesItWithTheChosenColourAndFormat_andOpensTheScannerForIt,
                    ),
            ),
            ListingClaim(
                "For most listed stores, LoyScan checks the number's length and format as you type, and its check digit where the card has one.",
                ShippedCatalogueTest::class.tests(
                    ShippedCatalogueTest::mostShippedStores_haveANumberRule,
                    ShippedCatalogueTest::eachRule_turnsThePrintedNumberIntoItsBarcode,
                    ShippedCatalogueTest::superShop_verifiesALuhnCheckDigit,
                ) +
                    CardFormViewModelTest::class.tests(CardFormViewModelTest::anInvalidNumber_saysWhy_andCanStillBeSavedAnyway) +
                    CardFormScreenTest::class.tests(
                        CardFormScreenTest::inHungarian_aNumberThatStartsWrong_saysSo,
                        CardFormScreenTest::aNumberTheStoresBarcodeCantHold_saysSo_andOffersSaveAnyway,
                        CardFormScreenTest::aWrongCheckDigit_saysSo_andOffersSaveAnyway,
                    ),
            ),
            ListingClaim(
                "Keep photos of the card's front and back, and give a store a logo of your own.",
                CardFormScreenTest::class.tests(CardFormScreenTest::addingAFrontPhoto_picksAPicture_showsIt_andSavesItWithTheCard) +
                    CardFormViewModelTest::class.tests(
                        CardFormViewModelTest::editingACard_startsWithItsPhotos_andAPickedBackPhotoReplacesItsOwnOnSave,
                    ) +
                    DetailScreenTest::class.tests(DetailScreenTest::aCardWithPhotos_showsThemUnderTheNumber) +
                    StoreLogoScreenTest::class.tests(StoreLogoScreenTest::choosingAnImage_opensASquareCrop_andCropSetsTheLogo) +
                    ListScreenTest::class.tests(ListScreenTest::aCardWhoseStoreHasALogo_showsItOnItsTile_inPlaceOfTheInitials),
            ),
            ListingClaim(
                "Your cards are tiles in their store's colour. Search or sort them, and tap one to open it.",
                ListScreenTest::class.tests(
                    ListScreenTest::aTile_isInItsStoresColour,
                    ListScreenTest::searching_showsOnlyTheMatchingCards,
                    ListScreenTest::choosingASort_storesIt_andReordersTheTiles,
                ) + AppNavigationTest::class.tests(AppNavigationTest::tappingACard_opensItsDetail_andBackReturnsToTheList),
            ),
            ListingClaim(
                "The card opens at full brightness, with the barcode and its number. Double-tap the barcode to show it full screen.",
                DetailScreenTest::class.tests(
                    DetailScreenTest::turnsTheScreenToFullBrightness,
                    DetailScreenTest::showsTheCardsBarcode,
                    DetailScreenTest::showsTheCardLabelAndNumber,
                    DetailScreenTest::doubleTappingTheBarcode_opensScanMode_whichKeepsTheScreenOn,
                    DetailScreenTest::scanMode_showsTheBarcode_overTheWholeScreen,
                ),
            ),
            ListingClaim(
                "The home-screen widget opens a card with one tap.",
                CardWidgetInfoTest::class.tests(CardWidgetInfoTest::theWidget_isOfferedToTheLauncher) +
                    CardWidgetContentTest::class.tests(CardWidgetContentTest::content_showsOneClickableTilePerCard_withItsInitialsAndLabel) +
                    WidgetTapOpensCardTest::class.tests(WidgetTapOpensCardTest::aColdOpenCardIntent_opensThatCardsDetail_fromADeadProcess),
            ),
            // An account, ads and analytics all need the network, which the app can't use.
            ListingClaim("No account, no ads, no analytics.", NO_NETWORK),
            ListingClaim("Offline, with no account and no ads.", NO_NETWORK),
            ListingClaim("LoyScan itself never goes online: your cards, notes and photos stay on the phone.", NO_NETWORK),
            ListingClaim(
                "Cards aren't backed up to Google's cloud. They are copied only when you transfer your data to a new phone.",
                BackupRulesTest::class.tests(
                    BackupRulesTest::cloudBackup_includesNothing,
                    BackupRulesTest::cloudBackup_excludesEveryDataDomain,
                    BackupRulesTest::deviceTransfer_carriesDatabasePrefsAndPhotos,
                ),
            ),
            ListingClaim(
                "In English and Hungarian.",
                StringResourcesTest::class.tests(StringResourcesTest::theAppsHungarianStrings_translateEveryDefaultString),
            ),
            ListingClaim(
                "LoyScan is not affiliated with or endorsed by the retailers whose cards it stores.",
                StoreListingTest::class.tests(StoreListingTest::everyLocale_followsTheListingRules) +
                    AppNavigationTest::class.tests(AppNavigationTest::about_opensFromTheList_saysLoyScanIsNotAffiliated_andBackReturnsToTheList),
            ),
        )

        val CLAIMED_LISTING = Listing(
            title = "Cards",
            shortDescription = "Your cards. Offline.",
            fullDescription = "Keep your cards.\n\nAdd\n• Scan a card.",
        )


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
