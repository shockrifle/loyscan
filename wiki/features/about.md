# About

The About screen shows the app's name, its version, and a note that LoyScan is not affiliated with or endorsed by the
retailers whose cards it stores, and that their names and marks belong to their owners. The app shows retailers' names
and brand colours to say whose card a card is, so the note is required before the first release
([[2026-11-12-no-bundled-store-logos]]). Issue [#150](https://github.com/shockrifle/loyscan/issues/150). The Play
listing's own note is #75.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `ListScreen.kt` (`ListScreen`'s top bar): the Info (i) icon in the card list's top bar, "About", → `ListDirections.toAbout` →
  `navigate(About)` while the list is current (`Navigation.kt:35`).
- `AboutScreen.kt`: the screen; route `About`, `AboutDirections.toBack` → `navigateUp()` while About is current
  (`Navigation.kt:40-48`); registered in `AppNavigation` (`MainActivity.kt:152`).

## Behavior

- A Back arrow and the title "About", then "LoyScan" (`app_name`, not translated), "Version <versionName>"
  (`BuildConfig.VERSION_NAME`: `loyscan.versionName` in `gradle.properties`, [[2026-11-17-release-versioning]]), and the note. The version is a parameter defaulting to
  the build's, so a screen test passes its own.
- The page scrolls, so at a large font scale on a short phone the whole note can still be read.
- Static: no ViewModel, no state. Like every screen except the card, it stays portrait on a phone
  ([[2026-10-28-portrait-on-phones]]).
- Texts are string resources in English and Hungarian (`about`, `about_version`, `about_not_affiliated`;
  [[add-a-string]]). Hungarian note: "A LoyScan nem áll kapcsolatban az általa tárolt kártyák kereskedőivel, és azok nem
  támogatják az alkalmazást. A nevük és a védjegyeik a tulajdonosaikat illetik." The owner chose this wording on
  2026-11-17.
- **The entry sits beside the Settings gear**, which is still a no-op ([[card-list]]). The owner chose this on
  2026-11-17, over replacing the gear or adding an overflow menu, so the gear stays for a settings screen later.

## Tests

- `AboutScreenTest`: the name, version and note are shown; Back calls `toBack`; nothing clips at 2× font on a 320dp
  screen; at 2× font on a 240dp-tall screen the note scrolls fully into view; in Hungarian every text is translated.
- `ListScreenTest.theAboutButton_opensAbout`, and "Névjegy" in `inHungarian_everyTextOnTheList_isTranslated`.
- `AboutDirectionsTest`: About opens over the list and Back returns to it; a second tap on the list's button opens one
  About screen (#69).
- End to end: `AppNavigationTest.about_opensFromTheList_saysLoyScanIsNotAffiliated_andBackReturnsToTheList`.
