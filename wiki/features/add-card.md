# Add card

The list's "Add card" button starts adding a card: pick the store, scan its barcode (or enter the number by hand),
check the number and give the card a label, then save it. The new card opens. Issue
[#6](https://github.com/shockrifle/loyscan/issues/6); this page describes the slice that exists (catalogue and other
stores already in the database, and custom stores). Everything runs offline on the local database.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`, `StorePickerViewModel.kt`; search in `domain/StoreSearch.kt`.
- `CustomStoreScreen.kt`, `CustomStoreViewModel.kt` ("Other card"); formats in `BarcodeFormat.forCustomStores`.
- [[barcode-scanner]] (`ScannerScreen.kt`), unchanged except for where its directions lead.
- `CardFormScreen.kt`, `CardFormViewModel.kt` (`CardFormState`).
- Routes and directions in `Navigation.kt`: `StorePicker`, `CustomStore`, `Scanner(storeId)`,
  `CardForm(storeId, scannedContent?, scannedFormat?)`; `ListDirections.toAddCard` starts the flow. Saving uses
  `LoyaltyCardRepository.add`.

## Behaviour

- **Store picker.** Every store in the database (catalogue and custom), sorted by
  name. The search field narrows it to stores whose name or an alias contains the text, ignoring case and accents
  (`StoreSearch.search`: NFD, combining marks removed), so "mobelix" finds "Möbelix". Sorting uses the same key. With
  no match the screen says "No store matches “…”". Tapping a store opens the scanner for it. "Other card", first in
  the list and shown whatever the search, opens the custom-store form. A custom store's row has an Edit button
  ([[edit-store]]).
- **Custom store** ([[2026-10-22-custom-stores]]): a name (required, trimmed), one of eight colours (the first picked
  by default) and the barcode format for the store's typed numbers (Code 128 by default; the 13 formats of
  `BarcodeFormat.forCustomStores`, no GS1 or DataBar, #47). A scan keeps its own format. "Create" saves the store once
  (`StoreRepository.add`: UUID id, `isCustom = true`, no rule); a failed save says "The store couldn't be saved. Try
  again." and leaves the form open, as a failed card save does. The scanner for the new store then replaces the form,
  so back returns to the picker, which now lists the store. The rest of the flow is the same as for any store; with no
  rule, any number is valid and a scan another store's rule reads gets that store as a hint. The fields survive
  process death.
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
    use, wrong start, check digit). An empty field can't be saved. A store without a rule accepts any number its
    barcode format can hold as it is; one it can't (`BarcodeEncoder.holdsAsIs`: it would fall back, or an EAN/UPC
    number one digit short would get a computed check digit) gets "This isn't a valid <format>
    number." (`NumberCheck.DoesNotFitFormat`; found in review of the custom-store slice).
  - untouched scan: `CardNumberEngine.match`; if the rule can't have produced the content, "This barcode doesn't
    look like a <store> card." `RuleMatch.formatMatches` is ignored, so a scan whose symbology differs from the
    rule's isn't flagged; a GS1 scan and a plain rule of the same symbology agree anyway (#47). A store without a rule
    accepts any scan.
  - **Another store** (step 5 of #6, [[2026-10-22-store-suggestion-trigger]]): for an untouched scan, the form lists
    other stores under "It looks like a card from:": every store whose rule reads the scan when the picked store has
    no rule or its rule rejects the scan; the stores whose rule names the scan's GS1 format when the scan has the
    GS1 mark and the picked rule names the same symbology without it; none otherwise (the picked rule reads it in the
    exact format, without the mark the rule has, or in another symbology), and none for a typed number. Ranked by `matchingScan` in `domain/StoreSearch.kt`: exact format, then the same
    symbology with or without the GS1 mark, then the rest, each by name. The exact format ranks first because
    SuperShop's rule (any 16 digits, Code 128) also reads a Tesco GS1-128 barcode (found in review;
    `ShippedCatalogueTest.aTescoScan_offersTescoFirst`). Every match is offered, so the user chooses when several
    rules read the same number. Stores without a rule are never offered. Tapping one makes the form that store's
    (`CardFormViewModel.switchStore`): the scan and the label stay, the check runs against the new rule, and the card
    is saved for it. The chosen store is kept in the `SavedStateHandle`, so back still returns to the picker and the
    choice survives process death. The form reads every store for this (`StoreRepository.getAll`).
  - **"Save anyway":** with a problem shown, the button says "Save anyway" and saves the card as it is.
  - The field isn't reformatted while typing. When the card will show the number differently (the rule's display
    pattern), the field says "Shown as 1234 5674" under it.
- **Label** is optional; blank is stored as `null`.
- **Save** writes the card once (a second tap does nothing): a new UUID, `createdAt` = `updatedAt` = now from the
  injected `Clock`, no use yet. Then the card's detail opens in place of the flow (`popUpTo<List>()`), so back from it
  returns to the list. That opening isn't counted as a use (`Detail.countUse = false`): it is the end of adding the
  card, not showing it at a till.
- **A failed save** (`LoyaltyCardRepository.add` throws, e.g. the database is full or can't be written) leaves the
  form open with "The card couldn't be saved. Try again." above the button, which saves again. Cancellation isn't
  caught. Before #49 the failure crashed the app. Every exception is caught, so a bug that makes every save fail
  shows the message instead of crashing; it is logged with Timber, which logs nothing in release builds. 
- **A store deleted while the form is open** (a custom store, [[edit-store]]; #51): once the stores have loaded and
  none has the form's id, the form says "This store is no longer available." with no fields and no Save
  (`CardFormState.storeGone`). Tests: `CardFormViewModelTest.aStoreRemovedWhileTheFormIsOpen_…`,
  `CardFormScreenTest.aStoreRemovedWhileTheFormIsOpen_…`. The store picker sits below the form on the back stack, so
  today only a stale route reaches this.
- Label, typed number and a switched store are kept in the `SavedStateHandle`, so they survive process death.
- The same form edits a card (`CardForm.cardId`, title "Edit card"): [[edit-card]].

## Tests

- `domain/StoreSearchTest`: matching (name, alias, case, accents) and order; stores whose rule reads a scan, incl. a
  GS1 scan against a plain rule and the reverse (#47).
- `StorePickerViewModelTest`, `StorePickerScreenTest` (incl. "Other card").
- `CustomStoreViewModelTest` (defaults, a blank name, what is saved, one save only, a failed save, process death),
  `CustomStoreScreenTest`; the format list in `domain/BarcodeFormatTest` and
  `BarcodeEncoderTest.everyFormatACustomStoreCanPick_isDrawnAsItself`; `RoomStoreRepositoryTest.add_…`.
- `CardFormViewModelTest`: checks for typed numbers and scans (incl. the GS1 case), editing a scan, which scans are
  read-only, what is saved (typed, untouched scan, edited scan), one save only, a failed save and saving again, an unknown
  store, restore after process death, the stores offered for a scan, and switching to one (kept after process death). The card saved is built from the same draft the state shows, so the two can't disagree. `CardFormScreenTest`: messages, "Save anyway", the scan line, another store offered and switched to, save → opens the card, a failed save says so.
- `AddCardDirectionsTest`: where each step's directions lead, incl. GS1 separators surviving the route.
- `DetailViewModelTest.opening_aCardJustAdded_countsNoUse`.
- `AppNavigationTest`: picker → scanner → back, adding a card by hand end to end, and for a new custom store.

## Not implemented

From #6: "frequently added" stores for the user's country, and "add another". A custom store abandoned before its
first card stays in the picker until it is deleted there ([[edit-store]]). A GS1 Aztec or PDF417 scan loses its GS1
mark when saved (#48).
