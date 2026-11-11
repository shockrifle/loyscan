# English default UI, translated to Hungarian; tests stay in English

**Status:** Active · **Date:** 2026-11-09 · **Issue:** #72

## Context

LoyScan is for Hungarian stores, but its UI was English only, with every string hardcoded in Kotlin (#72). Before the
first release the UI moves to string resources and gets a Hungarian translation. The move is done screen by screen,
so for a while a Hungarian phone shows some screens translated and others in English.

## Options considered

- **Default language.** English in `values/` with Hungarian in `values-hu/`, or Hungarian as the default.
- **Register.** Informal (*te*: "Törlöd ezt a kártyát?") or formal (*Ön*: "Törli ezt a kártyát?").
- **Tests.** Look every string up by resource id (`context.getString(R.string.…)`), or keep the English literals and
  rely on the test locale being English.

## Decision

- English is the default (`values/strings.xml`), Hungarian the translation (`values-hu/strings.xml`). Every
  translatable string has a Hungarian translation: `StringResourcesTest` fails otherwise. `app_name` is
  `translatable="false"`.
- Hungarian uses the informal register (user's choice, 2026-11-09).
- JVM tests keep their English literals. Robolectric runs every test at `en-rUS` unless the test's `@Config(qualifiers)`
  sets another locale, whatever the machine's locale is. Hungarian is checked by tests that set `qualifiers = "+hu"`.
- Store names and aliases from the catalogue are proper names and are not translated.
- Barcode format names are translated only where the name has a Hungarian form: "QR code" → "QR-kód" (and
  "GS1 QR-kód"). EAN-13, Code 128, Data Matrix, PDF417 and the others are symbology names, shown as they are (user's
  choice, 2026-11-11, when confirming slice 2 of #72).

## Why

- An English default keeps the app usable on a phone in any other language, and keeps the code, tests and wiki in one
  language.
- The informal register is what Hungarian consumer apps mostly use, and it is shorter on small buttons (inferred; the
  user chose it when confirming the plan for #72).
- English literals in tests read as the behaviour they check ("Delete this card?"), and a resource lookup would compute
  the expected value the way the code does, which makes a missing or wrong string invisible to the test
  ([[tdd]] → Tests that can't fail). The locale is already pinned by Robolectric, so the literals don't depend on the
  machine.

## Consequences

- Adding a string means adding it to both files: [[add-a-string]].
- A test that sets a non-English locale must not assert on English literals.
