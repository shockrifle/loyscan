# Add card

The list's "Add card" button starts adding a card: pick the store, scan its barcode (or enter the number by hand),
check the number and give the card a label, then save it. The new card opens. Issue
[#6](https://github.com/shockrifle/loyscan/issues/6); this page describes the slice that exists (catalogue and other
stores already in the database). Everything runs offline on the local database.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`, `StorePickerViewModel.kt`; search in `domain/StoreSearch.kt`.
- [[barcode-scanner]] (`ScannerScreen.kt`), unchanged except for where its directions lead.
- `CardFormScreen.kt`, `CardFormViewModel.kt` (`CardFormState`).
- Routes and directions in `Navigation.kt`: `StorePicker`, `Scanner(storeId)`,
  `CardForm(storeId, scannedContent?, scannedFormat?)`; `ListDirections.toAddCard` starts the flow. Saving uses
  `LoyaltyCardRepository.add`.

## Behaviour

- **Store picker.** Every store in the database (catalogue, custom and, in debug builds, demo stores), sorted by
  name. The search field narrows it to stores whose name or an alias contains the text, ignoring case and accents
  (`StoreSearch.search`: NFD, combining marks removed), so "mobelix" finds "Möbelix". Sorting uses the same key. With
  no match the screen says "No store matches “…”". Tapping a store opens the scanner for it.
- **Scanner → form.** A scan, or "Enter manually", *replaces* the scanner with the form (`popUpTo<Scanner>
  { inclusive = true }`), so back from the form returns to the picker, not to a scanner that has already handed on
  its one scan.
- **Number field.** A scan fills it with the number the card will show (`LoyaltyCard.displayNumber` of the scan:
  the core the store's rule recovers, formatted; otherwise the scanned content as text), and the form says
  "Scanned · <format>". While the field is untouched the card is saved as the scan: `number` = the scanned content
  verbatim, `scannedFormat` = its format ([[2026-10-12-data-model-v2]]: a scan is drawn exactly as scanned). Any edit
  makes it a typed number. That is only safe when the field holds something the user can edit into a number: the
  core the rule reads back, or the content as it is. A scan shown otherwise (GS1 data as printed, `(01)…(10)…`, or
  control characters as spaces) is **read-only**, with "Type the number instead", which empties the field; editing
  that rendering would have saved it, brackets and all, as the barcode (found in review). A typed number: `scannedFormat` is dropped and the barcode is built from the typed number by the store's
  rule, the same rule #8 asks of editing. A typed number is stored as typed (trimmed). For a typed number the form
  says "Barcode: <format>" (the rule's, else the store's default).
- **Live check** against the store's card-number rule ([[2026-10-09-card-number-rule-engine]]), on every change:
  - typed: `CardNumberEngine.validate`; each error has a message (too short, too long, characters the store doesn't
    use, wrong start, check digit). An empty field can't be saved.
  - untouched scan: `CardNumberEngine.match`; if the rule can't have produced the content, "This barcode doesn't
    look like a <store> card." `RuleMatch.formatMatches` is ignored, so a GS1 scan of a store whose rule names the
    plain symbology isn't flagged (#47). A store without a rule accepts any scan.
  - **"Save anyway":** with a problem shown, the button says "Save anyway" and saves the card as it is. Suggesting
    another store whose rule matches the scan isn't implemented.
  - The field isn't reformatted while typing. When the card will show the number differently (the rule's display
    pattern), the field says "Shown as 1234 5674" under it.
- **Label** is optional; blank is stored as `null`.
- **Save** writes the card once (a second tap does nothing): a new UUID, `createdAt` = `updatedAt` = now from the
  injected `Clock`, no use yet. Then the card's detail opens in place of the flow (`popUpTo<List>()`), so back from it
  returns to the list. That opening isn't counted as a use (`Detail.countUse = false`): it is the end of adding the
  card, not showing it at a till.
- **A failed save** (`LoyaltyCardRepository.add` throws, e.g. a foreign-key error if the store was removed while the
  form was open) is logged and leaves the form open with "The card couldn't be saved. Try again." above the button,
  which saves again. Cancellation isn't caught. Before #49 the failure crashed the app.
- Label and typed number are kept in the `SavedStateHandle`, so they survive process death.

## Tests

- `domain/StoreSearchTest`: matching (name, alias, case, accents) and order.
- `StorePickerViewModelTest`, `StorePickerScreenTest`.
- `CardFormViewModelTest`: checks for typed numbers and scans (incl. the GS1 case), editing a scan, which scans are
  read-only, what is saved (typed, untouched scan, edited scan), one save only, a failed save and saving again, an unknown
  store, restore after process death. The card saved is built from the same draft the state shows, so the two can't disagree. `CardFormScreenTest`: messages, "Save anyway", the scan line, save → opens the card, a failed save says so.
- `AddCardDirectionsTest`: where each step's directions lead, incl. GS1 separators surviving the route.
- `DetailViewModelTest.opening_aCardJustAdded_countsNoUse`.
- `AppNavigationTest`: picker → scanner → back, and adding a card by hand end to end.

## Not implemented

From #6: "frequently added" stores for the user's country, custom stores (and picking a barcode format for them),
suggesting another store when a scan matches it better, and "add another". A GS1 Aztec or PDF417 scan loses its GS1
mark when saved (#48).
