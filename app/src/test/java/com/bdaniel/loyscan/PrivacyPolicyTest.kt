package com.bdaniel.loyscan

import com.bdaniel.loyscan.data.photos.FileCardPhotosTest
import com.bdaniel.loyscan.data.repository.RoomLoyaltyCardRepositoryTest
import com.bdaniel.loyscan.data.repository.RoomStoreRepositoryTest
import com.bdaniel.loyscan.di.CardPhotosModuleTest
import com.bdaniel.loyscan.di.DatabaseModuleTest
import com.bdaniel.loyscan.di.PreferencesModuleTest
import com.bdaniel.loyscan.testing.ListingClaim
import com.bdaniel.loyscan.testing.POLICY_BULLET
import com.bdaniel.loyscan.testing.bulletsPerHeading
import com.bdaniel.loyscan.testing.claimProblems
import com.bdaniel.loyscan.testing.headings
import com.bdaniel.loyscan.testing.linesOutsideBullets
import com.bdaniel.loyscan.testing.personalDetails
import com.bdaniel.loyscan.testing.policyText
import com.bdaniel.loyscan.testing.section
import com.bdaniel.loyscan.testing.tests
import com.bdaniel.loyscan.testing.testsNotNamedIn
import com.bdaniel.loyscan.ui.LogoSearchTest
import com.bdaniel.loyscan.ui.scanner.CameraXScannerTest
import com.bdaniel.loyscan.widget.CardWidgetContentTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The privacy policy in `play/privacy-policy/<locale>.md` (#75), as it will be published. Each language has the same
 * topics; every statement is a bullet, and every bullet is a claim backed by named tests ([POLICY_CLAIMS]); any other
 * line is one written out below, so a sentence can't be added without a test or a change here; the text holds no
 * contact details. The rules are checked on snippets first. wiki/integrations/google-play.md → Privacy policy.
 */
class PrivacyPolicyTest {

    // ---- The real policy ----

    @Test
    fun thePolicy_hasExactlyTheEnglishAndHungarianVersions() {
        val files = POLICY.listFiles().orEmpty().map { it.name }

        assertWithMessage("files in $POLICY").that(files).containsExactly("en-US.md", "hu-HU.md")
    }

    @Test
    fun thePolicy_hasItsTopics_inEachLanguage() {
        assertThat(headings(policy("en-US"))).containsExactly(
            "What LoyScan keeps",
            "The camera and your pictures",
            "No internet, no tracking",
            "Backup and a new phone",
            "Who sees your cards",
            "Changes to this policy",
        ).inOrder()
        assertThat(headings(policy("hu-HU"))).containsExactly(
            "Mit tárol a LoyScan",
            "A kamera és a képeid",
            "Nincs internet, nincs követés",
            "Biztonsági mentés és új telefon",
            "Ki látja a kártyáidat",
            "A tájékoztató változásai",
        ).inOrder()
    }

    @Test
    fun everyStatementOfThePolicy_isABulletBackedByNamedTests() {
        assertWithMessage("POLICY_CLAIMS against play/privacy-policy/en-US.md")
            .that(claimProblems(policy("en-US"), POLICY_CLAIMS, POLICY_BULLET)).isEmpty()
    }

    @Test
    fun thePolicy_saysNothingOutsideItsBullets_butItsTitleDateIntroAndNotes() {
        OTHER_LINES.forEach { (locale, lines) ->
            assertWithMessage("$locale lines that aren't a heading or bullet")
                .that(linesOutsideBullets(policy(locale))).containsExactlyElementsIn(lines).inOrder()
        }
    }

    @Test
    fun theHungarianPolicy_hasTheEnglishBullets_underEachHeading() {
        assertThat(bulletsPerHeading(policy("hu-HU"))).isEqualTo(bulletsPerHeading(policy("en-US")))
    }

    @Test
    fun thePolicy_containsNothingPersonal() { // no contact details yet: they are chosen with the host (#75)
        OTHER_LINES.keys.forEach { locale ->
            assertWithMessage("play/privacy-policy/$locale.md").that(personalDetails(policy(locale))).isEmpty()
        }
    }

    @Test
    fun theHungarianPolicy_isInHungarian() {
        val hungarian = policy("hu-HU")

        assertThat(hungarian).isNotEqualTo(policy("en-US"))
        assertThat(hungarian).contains("adatvédelmi tájékoztató")
        assertThat(hungarian).contains("a telefonodon")
        assertThat(hungarian).contains("biztonsági mentés")
    }

    @Test
    fun theTestsBehindThePolicy_areNamedInThePrivacyPolicySectionOfTheGooglePlayWikiPage() {
        val policySection = section(GOOGLE_PLAY_WIKI.readText().replace("\r\n", "\n"), "## Privacy policy")

        assertWithMessage("tests missing from $GOOGLE_PLAY_WIKI → Privacy policy")
            .that(testsNotNamedIn(policySection, POLICY_CLAIMS.flatMap { it.tests })).isEmpty()
    }

    // ---- The rules, on snippets ----

    @Test
    fun headings_areTheSecondLevelOnes() {
        assertThat(headings(A_POLICY)).containsExactly("Kept", "Shared").inOrder()
    }

    @Test
    fun bullets_areCountedUnderEachHeading() {
        assertThat(bulletsPerHeading(A_POLICY)).containsExactly(0, 2, 1).inOrder()
    }

    @Test
    fun theLinesOutsideBullets_areTheTitleAndThePlainLines() {
        assertThat(linesOutsideBullets(A_POLICY)).containsExactly("# Policy", "Intro.", "A note.").inOrder()
    }

    @Test
    fun aSection_runsFromItsHeadingToTheNextOfItsLevel() {
        val page = "# Page\n## Listing\n`A.a`\n### Detail\n`A.b`\n## Privacy policy\n`A.c`\n### Table\n`A.d`\n## Quirks\n`A.e`"

        assertThat(section(page, "## Privacy policy")).isEqualTo("## Privacy policy\n`A.c`\n### Table\n`A.d`")
        assertThat(section(page, "## Quirks")).isEqualTo("## Quirks\n`A.e`")
    }

    @Test
    fun aPolicyFile_isReadWithLfLineEndings() {
        assertThat(policyText("# Policy\r\n\r\n- One.\r\n")).isEqualTo("# Policy\n\n- One.\n")
    }

    private companion object {
        /** The policy folder, from the module directory (the test's working directory). */
        val POLICY = File("../play/privacy-policy")
        val GOOGLE_PLAY_WIKI = File("../wiki/integrations/google-play.md")

        fun policy(locale: String) = policyText(File(POLICY, "$locale.md").readText())

        /** Each language's lines that aren't a heading or a bullet, written out: they make no claim a test backs. */
        val OTHER_LINES = mapOf(
            "en-US" to listOf(
                "# LoyScan privacy policy",
                "Last updated: 19 November 2026",
                "LoyScan is an Android app that keeps your loyalty cards on your phone. This policy says what it does with your data.",
                "Android and Google Play may collect their own diagnostics, such as crash reports, as your phone's settings allow; LoyScan adds nothing to them.",
                "If LoyScan starts handling your data differently, this policy is updated first, with a new date at the top.",
            ),
            "hu-HU" to listOf(
                "# A LoyScan adatvédelmi tájékoztatója",
                "Frissítve: 2026. november 19.",
                "A LoyScan egy Android-alkalmazás, amely a telefonodon tárolja a hűségkártyáidat. Ez a tájékoztató leírja, mit kezd az adataiddal.",
                "Az Android és a Google Play a telefonod beállításai szerint gyűjthet saját diagnosztikai adatokat, például hibajelentéseket; a LoyScan ezekhez semmit sem tesz hozzá.",
                "Ha a LoyScan másképp kezdi kezelni az adataidat, előbb ez a tájékoztató frissül, új dátummal az elején.",
            ),
        )

        /** No INTERNET permission: LoyScan can't send anything, so no account, ads, analytics, crash reports or server. */
        val NO_NETWORK = AppPermissionsTest::class.tests(
            AppPermissionsTest::theApp_doesNotAskForNetworkAccess,
            AppPermissionsTest::thePermissionList_comesFromTheMergedManifest,
        )

        val PRIVATE_STORAGE = DatabaseModuleTest::class.tests(DatabaseModuleTest::theDatabase_isKeptInTheAppsPrivateStorage) +
            CardPhotosModuleTest::class.tests(CardPhotosModuleTest::thePhotos_areKeptInTheAppsPrivateFiles) +
            PreferencesModuleTest::class.tests(PreferencesModuleTest::theUserPreferences_areKeptInTheAppsPrivateFiles_underAStableName)

        /** Every bullet of the English policy, word for word, with the tests that keep it true. */
        val POLICY_CLAIMS = listOf(
            ListingClaim(
                "Your cards: the store, the number and barcode type, an optional name and note, and when you added each card, when you last opened it and how often.",
                RoomLoyaltyCardRepositoryTest::class.tests(
                    RoomLoyaltyCardRepositoryTest::add_storesTheCard_andItReadsBackWithItsStore,
                    RoomLoyaltyCardRepositoryTest::recordUse_countsAUseAtTheClocksTime,
                ),
            ),
            ListingClaim(
                "Photos of a card's front and back, and store logos you choose, with the picture's location and other metadata removed.",
                FileCardPhotosTest::class.tests(
                    FileCardPhotosTest::stored_dropsTheLocationAndTheOtherExif,
                    FileCardPhotosTest::cropLogo_dropsThePicturesMetadata,
                ),
            ),
            ListingClaim(
                "The stores you add yourself, and how you sort your cards.",
                RoomStoreRepositoryTest::class.tests(RoomStoreRepositoryTest::add_insertsTheStore_andGetAllEmitsIt) +
                    ListScreenTest::class.tests(ListScreenTest::choosingASort_storesIt_andReordersTheTiles),
            ),
            ListingClaim("All of it is kept in LoyScan's private storage on your phone.", PRIVATE_STORAGE),
            ListingClaim(
                "Deleting a card deletes its photos.",
                RoomLoyaltyCardRepositoryTest::class.tests(RoomLoyaltyCardRepositoryTest::delete_deletesTheCardsPhotos) +
                    AppNavigationTest::class.tests(AppNavigationTest::deletingACardFromTheList_deletesItsPhotoFiles),
            ),
            ListingClaim(
                "LoyScan uses the camera to read a card's barcode, only while the scanner is open and only after you allow it.",
                ScannerScreenTest::class.tests(
                    ScannerScreenTest::permissionNotAskedYet_isRequestedOnEntry,
                    ScannerScreenTest::permissionDenied_explainsWhyAndAsksAgain,
                ) + CameraXScannerTest::class.tests(
                    CameraXScannerTest::aBarcodeInAFrame_isReportedAsScanned,
                    CameraXScannerTest::leaving_closesTheCamera,
                ),
            ),
            ListingClaim(
                "A photo of a card is taken with your camera app, which saves it straight into LoyScan's private storage.",
                CardFormScreenTest::class.tests(CardFormScreenTest::takingAPhoto_opensTheCameraAppOnACapture_andShowsThePhotoTaken) +
                    CardPhotosModuleTest::class.tests(
                        CardPhotosModuleTest::aCapture_isSharedThroughTheAppsFileProvider_forTheCameraAppToWriteIntoThePhotoDirectory,
                        CardPhotosModuleTest::thePhotos_areKeptInTheAppsPrivateFiles,
                    ),
            ),
            ListingClaim(
                "A picture you pick or share to LoyScan is read for a barcode on your phone.",
                ScannerScreenTest::class.tests(ScannerScreenTest::aPictureWithABarcode_isHandedOnAsAScan) +
                    ShareToLoyScanTest::class.tests(
                        ShareToLoyScanTest::sharingAPictureWithABarcode_opensThePicker_andPickingAStoreOpensTheFormWithIt,
                    ) + NO_NETWORK,
            ),
            ListingClaim(
                "LoyScan has no internet access: it doesn't ask Android for it, so it can't send anything anywhere.",
                NO_NETWORK,
            ),
            ListingClaim("There is no account, no ads, no analytics and no crash reporting of LoyScan's own.", NO_NETWORK),
            ListingClaim(
                "\"Find a logo online\" opens your browser on a Google image search for the store's name. That search is between you and Google, under Google's privacy policy.",
                LogoSearchTest::class.tests(
                    LogoSearchTest::logoSearchUri_isAGoogleImageSearchForTheStoresLogo,
                    LogoSearchTest::aLogoSearch_opensTheSearchInTheBrowser,
                ),
            ),
            ListingClaim(
                "Nothing from LoyScan goes to Google's cloud backup.",
                BackupRulesTest::class.tests(
                    BackupRulesTest::theManifest_allowsBackup_andPointsAndroidAtBothRulesFiles,
                    BackupRulesTest::cloudBackup_includesNothing,
                    BackupRulesTest::cloudBackup_excludesEveryDataDomain,
                    BackupRulesTest::legacyRules_areIncludeOnlyAndEveryIncludeIsDeviceToDeviceOnly,
                ),
            ),
            ListingClaim(
                "When you transfer your data to a new phone, your cards, photos and settings are copied to it.",
                BackupRulesTest::class.tests(
                    BackupRulesTest::theManifest_allowsBackup_andPointsAndroidAtBothRulesFiles,
                    BackupRulesTest::deviceTransfer_carriesDatabasePrefsAndPhotos,
                    BackupRulesTest::legacyRules_transferEveryDomainToANewPhone,
                ),
            ),
            ListingClaim(
                "LoyScan doesn't send your cards to anyone: there is no LoyScan server, and no company gets them.",
                NO_NETWORK,
            ),
            ListingClaim(
                "If you add the home-screen widget, it shows your cards' names on your home screen.",
                CardWidgetContentTest::class.tests(CardWidgetContentTest::content_showsOneClickableTilePerCard_withItsInitialsAndLabel),
            ),
        )

        val A_POLICY = "# Policy\n\nIntro.\n\n## Kept\n\n- Cards.\n- Photos.\n\n## Shared\n\n- Nothing.\n\nA note.\n"
    }
}
