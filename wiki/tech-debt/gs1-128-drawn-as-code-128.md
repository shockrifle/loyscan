# GS1-128 barcodes are drawn as plain Code 128

**Status:** Open · **Issue:** [#33](https://github.com/shockrifle/loyscan/issues/33) · **Since:** 2026-10-13 (`ee569f1`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/domain/BarcodeFormat.kt`, `hu-coop` in `app/src/main/assets/catalogue/stores.json`

## What
`BarcodeFormat` has no `GS1_128`. A GS1-128 barcode is Code 128 with a leading FNC1 character that tells the
scanner the data is GS1 (Application Identifiers). A card catalogued as GS1-128 is stored as `CODE_128` and will
be drawn without the FNC1 ([[zxing-core]]).

## Why it exists
The enum follows ZXing's names ([[2026-10-09-card-number-rule-engine]]), and GS1-128 isn't a separate ZXing format
(ZXing writes it as Code 128 with an FNC1 hint). The first catalogue entries had to map it to something.

## Risk / impact
Coop's barcode content starts with `90` (GS1 Application Identifier "internal company use"). A till that parses
GS1 may reject or misread a barcode without FNC1. It is unconfirmed: plain Code 128 may scan fine at Coop tills.

Tesco too: a real Clubcard's barcode decodes as GS1-128 with AI `97` (seen 2026-10-19, #31). The scanner marks such a
scan as GS1 (`ScannedBarcode.gs1`, #41), but a card has nowhere to keep the mark yet: once saved (#6) it would be plain
`CODE_128` with the same digits, drawn without the FNC1.

## How to pay it down
Test at a Coop till ([#31](https://github.com/shockrifle/loyscan/issues/31)). If plain Code 128 doesn't scan, add
`GS1_128`, draw it with a leading FNC1 (ZXing's `EncodeHintType.GS1_FORMAT` in `BarcodeEncoder`), and switch
`hu-coop` to it. A Coop till that reads plain Code 128 settles only `hu-coop`: a Code 128 scan marked GS1
(`ScannedBarcode.gs1`, e.g. a Tesco Clubcard) must still be stored and drawn as GS1-128, so `GS1_128` is needed
either way (#33's last criterion).
