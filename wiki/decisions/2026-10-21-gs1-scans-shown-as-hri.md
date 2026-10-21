# GS1 scans are shown as printed under the barcode

**Status:** Active · **Date:** 2026-10-21 · **Issue:** [#42](https://github.com/shockrifle/loyscan/issues/42)

## Context
A scan is stored exactly as read (`LoyaltyCard.number`, [[2026-10-12-data-model-v2]]). For GS1 data, zxing-cpp returns
the FNC1 between two fields as GS (U+001D), a control character ([[zxing-cpp]] → Contract). When no card-number rule
recovers a printed number from the scan, the display number was the content itself, invisible character included.

## Options considered
1. Show each run of control characters as a space: `01095011010209171012AB 21XYZ`.
2. Drop them: the fields run together.
3. Show the GS1 human-readable interpretation (HRI), as printed under GS1 barcodes: `(01)09501101020917(10)12AB(21)XYZ`.
   Needs to know where each Application Identifier (AI) and its value end.

## Decision
Option 3 (product choice, 2026-10-21) for scans whose format always holds GS1 data: `GS1_128` and the DataBars
(`BarcodeFormat.isGs1`). `Gs1.humanReadable` (`app/src/main/java/com/bdaniel/loyscan/domain/Gs1.kt`) splits the
content into fields. Content it can't split, and content of other formats, falls back to option 1, so no control
character reaches the screen. Storage is unchanged: the GS stays in `number`, and the barcode is drawn from it with
FNC1 ([[zxing-core]] → Quirks).

A rule that matches still wins: its display number is the printed card number, which is what a cashier keys in.

## Why
The HRI is what the card shows under its barcode, so a cashier can compare it with the card, and the brackets make
the field boundaries visible (the user's choice over a plain space).

## Consequences
- An AI's length follows from its first two digits. A field ends after its predefined length (GS1's table of
  predefined-length AIs, e.g. `01`: 14 digits) or else at the next GS or the end of the data. The prefix table is
  taken from ZXing core 3.5.3's `FieldParser`. An unassigned prefix makes the whole content fall back to option 1;
  new GS1 AIs with a new prefix need a line in `AI_LENGTH`.
- A scanned DataBar card without a matching rule now reads `(01)09012345678901`, not `0109012345678901`.
- GS1 Data Matrix and QR scans fall back to option 1, since their GS1 mark isn't stored (#45).
- Tests: `Gs1Test`, `LoyaltyCardTest.scannedGs1Card_displaysTheContentAsPrintedUnderGs1Barcodes`,
  `scannedDataBarCard_isGs1Data_too`, `scannedContentWithControlCharacters_displaysEachRunAsASpace`.
