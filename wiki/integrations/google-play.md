# Google Play (Play Console)

Where LoyScan is published. There is **no API integration**: no service account, no upload from CI. The owner
uploads each AAB by hand in Play Console ([[release]], #74), and pastes the store listing texts in by hand too (below).
The privacy policy's text is written (below), but not yet published; the data safety form, screenshots and feature
graphic are still open in #75.

## Contract

- **Play App Signing.** Google holds the app signing key and re-signs what it delivers to phones. We sign uploads with
  an **upload key**, which Play can reset when it leaks or is lost ([[leaked-signing-secret]]). For a new app, the key
  that signs the first uploaded AAB becomes the upload key (Play App Signing is the default for new apps; Google's
  help: https://support.google.com/googleplay/android-developer/answer/9842756). Why: [[2026-11-17-release-signing-on-ci]].
- **What we upload**: the signed AAB from the draft GitHub release of the tag. Play needs an AAB, not an APK, for a
  new app.
- **`versionCode` must grow** with every upload, on every track; `X*10000 + Y*100 + Z` does that as long as the version
  grows ([[2026-11-17-release-versioning]]). An AAB with a code Play has already seen is refused.

## Store listing assets

What Play shows on the listing but the app doesn't ship lives in `play/` at the repo root, outside every Gradle source
set: the store icon, the listing texts and the privacy policy. Where the screenshots and feature graphic go is decided with them (#75).

- **Listing texts** `play/listing/<locale>/title.txt`, `short_description.txt`, `full_description.txt`, for `en-US` and
  `hu-HU` (Play's locale codes; Fastlane's file names, so a tool that uploads them could read the folder as it is,
  though none does). Plain text, LF line endings (`.gitattributes`), one trailing newline that isn't part of the text.
  The full description uses "•" bullets and sentence-case headings, no Markdown. **To change a text**: edit the file,
  run `StoreListingTest`, then paste it into Play Console's main store listing, once per language. Rules, each checked by `StoreListingTest` (plain JUnit, rules in `testing/StoreListing.kt`, tested on
  snippets first):
  - exactly these two locales, each with all three files;
  - Play's limits, counted in characters (an accented letter is one): title 30, short description 80, full
    description 4000;
  - the full description contains the app's `about_not_affiliated` string in its language word for word, on one line
    ([[2026-11-12-no-bundled-store-logos]], [[about]]);
  - no email address, URL (with a scheme, `www.`, or a bare domain like `example.hu`) or number of 7 or more digits:
    contact details live in Play Console only, and a URL would need to be one chosen to be public (the privacy
    policy's, later);
  - no retailer named: no catalogue store name or alias from `stores.json`, in any case, a name of 4 or more letters also
    at the start of a longer word ("Lidlben"). Stricter than the logo decision needs: the owner chose on 2026-11-19 to
    keep the listing clear of retailers' trademarks. Hungarian accented suffixes ("Tescóban") slip past it; "spare"
    trips on "SPAR", rephrase it;
  - no all-caps word, emoji or other symbol (`™` too) in the title. Play's Metadata policy also bans repeated
    keywords, rankings or price and promotion claims ("#1", "free") and testimonials in every text; those are checked
    by reading.
  - the Hungarian texts are Hungarian: they differ from the English and contain hand-picked words.
- **What the texts claim**, and the tests that keep it true (#178). `LISTING_CLAIMS` in `StoreListingTest` holds each
  claim, word for word as the English listing says it, with its tests as Kotlin function references, and this table
  mirrors it. `StoreListingTest` fails when a bullet of the English full description is no claim, when a claim's text is
  gone from the listing, when the Hungarian full description has a different number of bullets in any section, or when
  a test in the list isn't named on this page. A deleted or renamed test doesn't compile. What it doesn't check: that
  the tests assert what the claim says (changing what a test checks still means reading the claim); a claim in a line
  that isn't a bullet (the intro, the closing lines) or in the short description, which are listed by hand; a Hungarian
  bullet that says something else than its English one; a row of this table whose test left the list. Changing a feature the
  listing claims means changing the listing in the same PR.

  | Claim (en-US) | Tests |
  |---|---|
  | "the cashier scans the barcode from your screen" | `DetailScreenTest.showsTheCardsBarcode` |
  | Scan with the camera, read it from a picture, or type the number | `CameraXScannerTest.aBarcodeInAFrame_isReportedAsScanned`, `ScannerScreenTest.aPictureWithABarcode_isHandedOnAsAScan`, `AppNavigationTest.addingACardByHand_savesIt_opensIt_andBackReturnsToTheListWithIt` |
  | The built-in list of Hungarian stores, or your own with a name, colour and barcode type | `ShippedCatalogueTest.shippedCatalogue_loadsIntoTheDatabase`, `ShippedCatalogueTest.everyShippedStore_isAHungarianStore`, `StorePickerScreenTest.tappingAStore_picksIt`, `CustomStoreScreenTest.creatingAStore_savesItWithTheChosenColourAndFormat_andOpensTheScannerForIt` |
  | For most listed stores, length and format as you type, the check digit where the card has one | `ShippedCatalogueTest.mostShippedStores_haveANumberRule` (22 of 23 stores on 2026-11-19), `ShippedCatalogueTest.eachRule_turnsThePrintedNumberIntoItsBarcode`, `ShippedCatalogueTest.superShop_verifiesALuhnCheckDigit`, `CardFormViewModelTest.anInvalidNumber_saysWhy_andCanStillBeSavedAnyway` (too short, as typed), `CardFormScreenTest.inHungarian_aNumberThatStartsWrong_saysSo`, `CardFormScreenTest.aNumberTheStoresBarcodeCantHold_saysSo_andOffersSaveAnyway`, `CardFormScreenTest.aWrongCheckDigit_saysSo_andOffersSaveAnyway` ([[add-card]]) |
  | Photos of the card's front and back; a store logo of your own | `CardFormScreenTest.addingAFrontPhoto_picksAPicture_showsIt_andSavesItWithTheCard`, `CardFormViewModelTest.editingACard_startsWithItsPhotos_andAPickedBackPhotoReplacesItsOwnOnSave`, `DetailScreenTest.aCardWithPhotos_showsThemUnderTheNumber`, `StoreLogoScreenTest.choosingAnImage_opensASquareCrop_andCropSetsTheLogo`, `ListScreenTest.aCardWhoseStoreHasALogo_showsItOnItsTile_inPlaceOfTheInitials` |
  | Tiles in their store's colour; search, sort, tap to open | `ListScreenTest.aTile_isInItsStoresColour`, `ListScreenTest.searching_showsOnlyTheMatchingCards`, `ListScreenTest.choosingASort_storesIt_andReordersTheTiles`, `AppNavigationTest.tappingACard_opensItsDetail_andBackReturnsToTheList` |
  | Full brightness, the barcode and its number; a double-tap shows the barcode full screen | `DetailScreenTest.turnsTheScreenToFullBrightness`, `DetailScreenTest.showsTheCardsBarcode`, `DetailScreenTest.showsTheCardLabelAndNumber`, `DetailScreenTest.doubleTappingTheBarcode_opensScanMode_whichKeepsTheScreenOn`, `DetailScreenTest.scanMode_showsTheBarcode_overTheWholeScreen` |
  | The home-screen widget opens a card with one tap ([[card-widget]]) | `CardWidgetInfoTest.theWidget_isOfferedToTheLauncher`, `CardWidgetContentTest.content_showsOneClickableTilePerCard_withItsInitialsAndLabel`, `WidgetTapOpensCardTest.aColdOpenCardIntent_opensThatCardsDetail_fromADeadProcess` |
  | No account, no ads, no analytics; the short description's "no account and no ads"; LoyScan never goes online, cards, notes and photos stay on the phone | `AppPermissionsTest.theApp_doesNotAskForNetworkAccess`: no `INTERNET` permission, and an account, ads and analytics all need the network. "Find a logo online" opens the browser, another app ([[store-logo]]) |
  | Not in Google's cloud backup; copied only by a transfer to a new phone | `BackupRulesTest.cloudBackup_includesNothing`, `BackupRulesTest.cloudBackup_excludesEveryDataDomain`, `BackupRulesTest.deviceTransfer_carriesDatabasePrefsAndPhotos` (#71; the device check is #104) |
  | In English and Hungarian | `StringResourcesTest.theAppsHungarianStrings_translateEveryDefaultString` |
  | Not affiliated with or endorsed by the retailers | `StoreListingTest.everyLocale_followsTheListingRules`, `AppNavigationTest.about_opensFromTheList_saysLoyScanIsNotAffiliated_andBackReturnsToTheList` ([[about]]) |
- **Store icon** `play/store-icon.png`: 512×512, 32-bit PNG (RGBA), at most 1024 KB, a full opaque square. Play draws
  the rounded corners and the shadow itself, so none are drawn in (Play Console help, "Graphic assets"). It is the
  launcher icon's background and foreground layers, the visible 72dp centre of the 108dp layer scaled to 512px. That
  crop is our choice (not checked against Android Studio's Image Asset export): it is the most a launcher shows, so the
  icon looks the same on the listing as on a phone. `StoreIconTest` renders the layers named in `mipmap-anydpi/ic_launcher.xml`
  and fails when the PNG differs from them, or when it misses Play's spec or hand-worked pixels (the crop and scale).
  **After a launcher icon change**: run `StoreIconTest`, then copy `app/build/store-icon/store-icon.png` (written on
  every run) over `play/store-icon.png`.

## Privacy policy

`play/privacy-policy/en-US.md` and `hu-HU.md`: what LoyScan keeps, the camera, no internet, backup and the widget, in
Markdown (LF line endings, `.gitattributes`). Every statement is a `- ` bullet on one line; the other lines (title,
date, intro, the note on Android's and Play's own diagnostics, the closing line) are written out in `PrivacyPolicyTest`,
so a sentence can't be added without a test or a change there. The date is in words ("19 November 2026"): an ISO date
looks like a phone number to the contact-details rule. Rules, each checked by `PrivacyPolicyTest` (plain JUnit, helpers
in `testing/PrivacyPolicy.kt` and `testing/StoreListing.kt`, tested on snippets first):

- exactly these two files, each with its six headings, written out per language;
- every English bullet is a claim of `POLICY_CLAIMS`, word for word, with its tests as function references, and every
  test is named in this section;
- the Hungarian file has as many bullets under each heading as the English one, and is in Hungarian. What a Hungarian
  bullet says is checked by reading: the owner reads it before it is published;
- no email address, URL or number of 7 or more digits (`personalDetails`, shared with the listing).

**To change it**: edit both files, change the date in both and in `PrivacyPolicyTest`, update `POLICY_CLAIMS` and the
table, run `PrivacyPolicyTest`, then republish it. A change to what the app stores, sends or backs up means changing the
policy in the same PR.

**Not done yet** (#75): where it is published and its URL (Play Console's privacy policy field), the contact the policy
must name (Play's User Data policy asks for one; the no-contact-details rule will then need to allow that one chosen
address), whether the app links to it too, and the data safety form, which must match it.

| Claim (en-US) | Tests |
|---|---|
| What LoyScan keeps: cards, with when they were added, last opened and how often | `RoomLoyaltyCardRepositoryTest.add_storesTheCard_andItReadsBackWithItsStore`, `RoomLoyaltyCardRepositoryTest.recordUse_countsAUseAtTheClocksTime` |
| Photos and logos, location and metadata removed | `FileCardPhotosTest.stored_dropsTheLocationAndTheOtherExif`, `FileCardPhotosTest.cropLogo_dropsThePicturesMetadata` |
| Stores you add, how you sort | `RoomStoreRepositoryTest.add_insertsTheStore_andGetAllEmitsIt`, `ListScreenTest.choosingASort_storesIt_andReordersTheTiles` |
| All of it in LoyScan's private storage | `DatabaseModuleTest.theDatabase_isKeptInTheAppsPrivateStorage`, `CardPhotosModuleTest.thePhotos_areKeptInTheAppsPrivateFiles`, `PreferencesModuleTest.theUserPreferences_areKeptInTheAppsPrivateFiles_underAStableName` |
| Deleting a card deletes its photos | `RoomLoyaltyCardRepositoryTest.delete_deletesTheCardsPhotos`, `AppNavigationTest.deletingACardFromTheList_deletesItsPhotoFiles` |
| The camera reads barcodes, only in the scanner, only once allowed | `ScannerScreenTest.permissionNotAskedYet_isRequestedOnEntry`, `ScannerScreenTest.permissionDenied_explainsWhyAndAsksAgain`, `CameraXScannerTest.aBarcodeInAFrame_isReportedAsScanned`, `CameraXScannerTest.leaving_closesTheCamera` |
| The camera app saves a card photo into LoyScan's storage | `CardFormScreenTest.takingAPhoto_opensTheCameraAppOnACapture_andShowsThePhotoTaken`, `CardPhotosModuleTest.aCapture_isSharedThroughTheAppsFileProvider_forTheCameraAppToWriteIntoThePhotoDirectory` (CI only), `CardPhotosModuleTest.thePhotos_areKeptInTheAppsPrivateFiles` |
| A picked or shared picture is read on the phone | `ScannerScreenTest.aPictureWithABarcode_isHandedOnAsAScan`, `ShareToLoyScanTest.sharingAPictureWithABarcode_opensThePicker_andPickingAStoreOpensTheFormWithIt` |
| No internet access of its own; no account, ads, analytics or crash reporting of its own; no server, no company gets the cards | `AppPermissionsTest.theApp_doesNotAskForNetworkAccess`, `AppPermissionsTest.thePermissionList_comesFromTheMergedManifest` |
| "Find a logo online" is a Google image search in the browser | `LogoSearchTest.logoSearchUri_isAGoogleImageSearchForTheStoresLogo`, `LogoSearchTest.aLogoSearch_opensTheSearchInTheBrowser` ([[store-logo]]) |
| Nothing goes to Google's cloud backup | `BackupRulesTest.theManifest_allowsBackup_andPointsAndroidAtBothRulesFiles`, `BackupRulesTest.cloudBackup_includesNothing`, `BackupRulesTest.cloudBackup_excludesEveryDataDomain`, `BackupRulesTest.legacyRules_areIncludeOnlyAndEveryIncludeIsDeviceToDeviceOnly` |
| A transfer to a new phone copies cards, photos and settings | `BackupRulesTest.deviceTransfer_carriesDatabasePrefsAndPhotos`, `BackupRulesTest.legacyRules_transferEveryDomainToANewPhone` (the device check is #104) |
| The widget shows the cards' names on the home screen | `CardWidgetContentTest.content_showsOneClickableTilePerCard_withItsInitialsAndLabel` ([[card-widget]]) |

## Quirks

- **R8 mapping.** AGP puts the R8 mapping inside the AAB (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`),
  so Play deobfuscates crash reports without a separate upload (seen in our AAB, 2026-11-17). It also means the AAB is as revealing as `mapping.txt`: keep both private. The `mapping.txt` on the draft release is for
  retracing by hand ([[release]] → Where release files are kept).
- **Self-signed certificates.** Every upload key is self-signed, so `jarsigner -verify -strict` fails on any of our
  AABs; Play doesn't care. Our checks use `jarsigner -verify` and compare the signer's SHA-256
  (`check-release-build.sh`, [[ci]]).
