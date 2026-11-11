# Add a UI string

Every text the user sees or hears (labels, content descriptions, click labels, toasts, snackbars) is a string
resource, in English in `app/src/main/res/values/strings.xml` and in Hungarian in
`app/src/main/res/values-hu/strings.xml`. Why English is the default and the tests stay in English:
[[2026-11-09-ui-language]]. Moving the UI to resources is #72, done screen by screen.

## Steps

1. **Add the English string** to `values/strings.xml`. Name it by what it says or where it is
   (`card_list_title`, `delete_card_question`), lower case with underscores.
2. **Add the Hungarian translation** to `values-hu/strings.xml` under the same name. `StringResourcesTest` fails
   until it is there, and it checks the format arguments match.
3. **Use it**: `stringResource(R.string.x)` in a composable. Text used outside composition (a toast shown from a
   click) is read the same way, into a `val` in the composable, and the lambda uses that `val`. Not
   `LocalContext.current.getString(…)`: `LocalContext`'s resources don't follow a configuration change Compose
   handles, and lint fails on it (`LocalContextGetResourceValueCall`, #126).
4. **A count** ("2 selected") is a `<plurals>` with `one` and `other` items in both files, read with
   `pluralStringResource(R.plurals.x, count, count)`. Hungarian has only `one` and `other`. The `one` item may leave
   the number out ("Delete this card?").
5. **A message from a ViewModel** goes to the screen as a resource id (`@StringRes Int`) or as an enum the screen
   maps to one, never as text: a ViewModel has no locale, and the text must follow a language change.
6. **Not translated:** a name that is the same in every language (`app_name`) gets `translatable="false"` in
   `values/` and no Hungarian entry. Store names and aliases from the catalogue are data, not resources.

## Not translated yet

The move is done screen by screen (#72). Until it is finished, a Hungarian phone shows the translated screens (the
card list, the card screen, the store picker and the card form's "Choose store" dialog) in Hungarian and the others in
English. Barcode format names (`BarcodeFormat.displayName`:
"QR code", "EAN-13") are not resources yet, so the card screen says "Megjelenítve: QR code"; they are shown by the card
form too and move with it. The photo descriptions ("Front photo") are shared with the card form, so on a
Hungarian phone the form's photos are already described in Hungarian while its buttons ("Add front photo") are still
English.

## Writing the Hungarian

- Informal register: *te*, not *Ön* ("Törlöd ezt a kártyát?").
- Hungarian quotation marks: „…” (opening low, closing high).
- A format argument stays as it is (`%1$s`, `%d`). Rephrase rather than put an article before a number: "a(z) %d"
  depends on how the number is said.
- Escape an apostrophe (`\'`) and a `@` or `?` at the start of a string, as Android requires.

## Tests

- Screen tests assert English literals: Robolectric runs at `en-rUS` unless `@Config(qualifiers)` says otherwise.
- A test that sets a Hungarian locale (`qualifiers = "+hu"`; the `+` keeps the class's other qualifiers, such as a
  tall screen) asserts Hungarian literals written by hand, never English ones.
- Each translated screen has one such test that walks the screen through its states (menus, dialogs, failures,
  empty state) and checks with `ShownTexts.assertEveryTextIsTranslated` that every text shown is either the
  fixture's data or a `values-hu` value ([[tdd]] → Screen in Hungarian). A new text on a translated screen should
  appear in one of the states it walks; add the state if it doesn't.
- The on-device smoke test (`MinifiedAppSmokeTest`) finds a translated text by its resource name (`string("…")`),
  since the phone may be in either language. A name it looks up goes in `res/raw/smoke_test_keep.xml`.
