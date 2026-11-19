# Google Play (Play Console)

Where LoyScan is published. There is **no API integration**: no service account, no upload from CI. The owner
uploads each AAB by hand in Play Console ([[release]], #74), and pastes the store listing texts in by hand too (below).
The privacy policy, data safety form, screenshots and feature graphic are still open in #75.

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
set: the store icon and the listing texts. Where the screenshots and feature graphic go is decided with them (#75).

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
- **What the texts claim**, and the tests that keep it true: no `INTERNET` permission, "the app itself never goes online"
  (`AppPermissionsTest`; "Find a logo online" opens the browser, another app, [[store-logo]]); cards aren't in Google's
  cloud backup and are copied only by a device-to-device transfer (`BackupRulesTest`, #71; the device check is #104); a
  number check as you type: length and format for most catalogue stores, the check digit only where the store's rule
  has one, so the texts say "most" and "where the card has one" ([[add-card]], `stores.json`); a home-screen widget
  ([[card-widget]]). Changing one of these means changing the listing in the same PR.
- **Store icon** `play/store-icon.png`: 512×512, 32-bit PNG (RGBA), at most 1024 KB, a full opaque square. Play draws
  the rounded corners and the shadow itself, so none are drawn in (Play Console help, "Graphic assets"). It is the
  launcher icon's background and foreground layers, the visible 72dp centre of the 108dp layer scaled to 512px. That
  crop is our choice (not checked against Android Studio's Image Asset export): it is the most a launcher shows, so the
  icon looks the same on the listing as on a phone. `StoreIconTest` renders the layers named in `mipmap-anydpi/ic_launcher.xml`
  and fails when the PNG differs from them, or when it misses Play's spec or hand-worked pixels (the crop and scale).
  **After a launcher icon change**: run `StoreIconTest`, then copy `app/build/store-icon/store-icon.png` (written on
  every run) over `play/store-icon.png`.

## Quirks

- **R8 mapping.** AGP puts the R8 mapping inside the AAB (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`),
  so Play deobfuscates crash reports without a separate upload (seen in our AAB, 2026-11-17). It also means the AAB is as revealing as `mapping.txt`: keep both private. The `mapping.txt` on the draft release is for
  retracing by hand ([[release]] → Where release files are kept).
- **Self-signed certificates.** Every upload key is self-signed, so `jarsigner -verify -strict` fails on any of our
  AABs; Play doesn't care. Our checks use `jarsigner -verify` and compare the signer's SHA-256
  (`check-release-build.sh`, [[ci]]).
