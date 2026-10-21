# The GS1 mark of a Data Matrix or QR scan is a barcode format

**Status:** Active · **Date:** 2026-10-21 · **Issue:** [#45](https://github.com/shockrifle/loyscan/issues/45)

## Context
The scanner marks GS1 data (`ScannedBarcode.gs1`, #41): a symbology with a leading FNC1, which the decoded text
doesn't show ([[zxing-cpp]] → Contract). A card stores only the content and `scannedFormat`. A GS1 Code 128 already
became its own format, `GS1_128` (#33), but a GS1 Data Matrix or GS1 QR saved as plain `DATA_MATRIX` / `QR_CODE` would
be drawn without the FNC1, so a GS1-parsing till reads `]d1` / `]Q1` instead of `]d2` / `]Q3`.

## Options considered
1. **A column on the card**, `scannedGs1: Boolean`, read by the encoder next to the format. Needs a schema change
   (DB v2 → v3).
2. **More formats**, `GS1_DATA_MATRIX` and `GS1_QR_CODE`, like `GS1_128`. No schema change: Room stores the enum by
   name.

## Decision
Option 2 (product choice, 2026-10-21). The scanner maps a Data Matrix or QR marked GS1 to its `GS1_` format
(`scannedBarcode` in `app/src/main/java/com/bdaniel/loyscan/ui/scanner/ZxingCppFormats.kt`), and `BarcodeEncoder` draws
it in GS1 mode ([[zxing-core]] → Quirks). In the same change, GS1 data that has to fall back (every DataBar, which
ZXing can't write) is drawn as GS1-128, then GS1 QR, instead of plain Code 128, then QR, if the content is a GS1
element string; otherwise without the mark (found in review). GS1 QR as the second step was
also the user's choice.

## Why
One field says how a card is drawn: `barcodeFormat`. `BarcodeFormat.isGs1` then covers every GS1 card, so the
`(01)…(10)…` display number ([[2026-10-21-gs1-scans-shown-as-hri]]) and the GS1 fallback chain apply to them without
another field. A flag would also mean nothing on most formats (EAN-13, ITF, …).

## Consequences
- A GS1 Aztec or PDF417 scan keeps its plain format: ZXing core 3.5.3 can't write either in GS1 mode, so a saved one
  loses the mark. `ScannedBarcode.gs1` still says what was scanned.
- A format picker (custom stores, #6) lists formats from this enum. Whether it offers the `GS1_` ones is not decided.
- `CardNumberEngine.match` compares formats exactly, so a GS1 QR or Data Matrix scan now reports
  `formatMatches = false` against a `QR_CODE` / `DATA_MATRIX` rule (e.g. `hu-ikea-family`), as a GS1-128 scan already
  did against a `CODE_128` rule ([[gs1-128-drawn-as-code-128]] → Risk). The add-card flow (#6) must not read that as
  another store. Found in review.
- Tests: `BarcodeEncoderTest.gs1DataMatrixAndQr_areDrawnInGs1Mode`, `dataBar_isDrawnAsGs1_128`,
  `gs1ContentCode128CannotHold_fallsBackToGs1Qr`, `ZxingCppFormatsTest.scannedBarcode_dataMatrixAndQrMarkedGs1_areTheirGs1Formats`,
  `LoyaltyCardTest.scannedGs1DataMatrixOrQrCard_isGs1Data_too`.
