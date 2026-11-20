# Custom stores: saved on their own screen, with a format for typed numbers

**Status:** Active · **Date:** 2026-10-22 · **Issue:** [#6](https://github.com/shockrifle/loyscan/issues/6) (step 2),
[#47](https://github.com/shockrifle/loyscan/issues/47)

## Context
Step 2 of #6: "Other card" in the store picker, a store that isn't in the catalogue, with a name and a colour. Step 4
asks for a barcode format "selectable for custom stores", and #47 asks which formats that picker lists. A card has no
format field of its own: a scan keeps its `scannedFormat`, and a typed number is drawn in the store rule's format or,
without a rule, in `Store.defaultBarcodeFormat` (`LoyaltyCard.barcodeFormat`, [[2026-10-12-data-model-v2]]). A custom
store has no rule, so the format of its typed numbers is a property of the store. The `stores` table already has
`isCustom` and `defaultBarcodeFormat`, so no schema change is needed.

## Options considered
1. **When the store is written:** (a) on its own screen, before the scanner; (b) only with its first card, the name
   and colour travelling through the scanner and form routes.
2. **Where the format is picked:** (a) on the custom-store screen, as the store's default; (b) on the card form, as
   #6's step 4 words it, which would still have to set the store's default and so change its other cards too.
3. **Which formats:** (a) the formats ZXing core writes as themselves for a typed number; (b) every `BarcodeFormat`.

## Decision
1a, 2a, 3a (user's choice, 2026-10-22).
- "Other card" opens `CustomStoreScreen` (name, required and trimmed; one of eight colour swatches, the first picked
  by default; the format, Code 128 by default). "Create" saves the store (UUID id, `isCustom = true`, no rule, no
  aliases or countries) through `StoreRepository.add`. The scanner for it then *replaces* the screen, so back returns
  to the picker, where the new store is listed. From there the card is added like for any store.
- The picker lists `BarcodeFormat.forCustomStores`: every format without the GS1 mark, i.e. Aztec, Codabar, Code 39,
  Code 93, Code 128, Data Matrix, EAN-8, EAN-13, ITF, PDF417, QR code, UPC-A, UPC-E.

## Why
Writing the store first reuses the whole flow unchanged: the scanner, the form, its rule check and its "It looks like
a card from:" hints, which matter most here because a custom store has no rule
([[2026-10-22-store-suggestion-trigger]]). The cost is a store left in the picker when the user gives up before
saving a card. The format belongs to the store, so it is picked where the store is made.

A typed number isn't GS1 data. `GS1_128` puts an FNC1 in front of any content (`BarcodeEncoder.withFnc1For`), which
would mark a plain number as GS1 and make a GS1 till misread it. The GS1 Data Matrix and QR formats fall back without
the mark for non-GS1 content, so they would only be named wrongly. DataBar has no ZXing writer and would be drawn as
something else. A card whose barcode carries the GS1 mark keeps it by being scanned, since a scan keeps its own format.
Every listed format draws as itself (`BarcodeEncoderTest.everyFormatACustomStoreCanPick_isDrawnAsItself`), though the
number still has to fit it. One that doesn't fit falls back to Code 128, then QR; an EAN or UPC number one digit short
would get a check digit ZXing computes, i.e. become another number.

## Consequences
- *Amended 2026-11-20 (proposed, not built):* the type moves to the card form ([[2026-11-20-barcode-type-per-card]],
  #187) and "Other card" scans before the store is made ([[2026-11-20-other-card-scan-first]], #188).
- A custom store abandoned before its first card stays in the picker until the user deletes it. Since #53 a custom
  store's name, colour and format can be edited from the picker, and the store deleted with its cards
  ([[edit-store]]); changing the format redraws its typed cards, not its scans.
- A typed number the picked format can't hold as it is (a wrong EAN-13 check digit, or one digit short) would be
  drawn in Code 128 or QR, or completed with a computed check digit ([[zxing-core]], `BarcodeEncoder.holdsAsIs`). The card form says "This isn't a valid <format> number." and offers "Save anyway", for any
  store without a rule (also `hu-libri`, EAN-13). Found in review; `CardFormViewModelTest.aTypedNumberTheFormatOf…`.
- Tests: `CustomStoreViewModelTest`, `CustomStoreScreenTest`, `AddCardDirectionsTest`, `BarcodeFormatTest`,
  `BarcodeEncoderTest`, `RoomStoreRepositoryTest`, and `AppNavigationTest.addingACardForANewCustomStore_…`.
