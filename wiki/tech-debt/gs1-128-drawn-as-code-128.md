# GS1-128 barcodes are drawn as plain Code 128

**Status:** Open · **Issue:** [#33](https://github.com/shockrifle/loyscan/issues/33) · **Since:** 2026-10-13 (`ee569f1`) · **Where:**
`hu-coop` in `app/src/main/assets/catalogue/stores.json`

## What
Scanned cards are no longer affected. `BarcodeFormat.GS1_128` exists and is drawn as Code 128 with a leading FNC1, the
character that tells the scanner the data is GS1 (Application Identifiers) ([[zxing-core]]). A Code 128 scan marked
GS1 is read as `GS1_128` ([[zxing-cpp]]), so a scanned GS1-128 card keeps its FNC1 (#33, 2026-10-20).

What is left is the catalogue: `hu-coop` is catalogued as plain `CODE_128`, so a typed Coop number is drawn without the
FNC1, although its content starts with Application Identifier `90`.

## Why it exists
The enum follows ZXing's names ([[2026-10-09-card-number-rule-engine]]), and GS1-128 isn't a separate ZXing format
(ZXing writes it as Code 128 with an FNC1). The first catalogue entries had to map it to something, and `GS1_128` was
only added once scans needed it (#33).

## Risk / impact
A till that parses GS1 may reject or misread a Coop barcode without FNC1. It is unconfirmed: plain Code 128 may scan
fine at Coop tills.

A Coop card whose barcode really is GS1-128 is scanned as `GS1_128`. `CardNumberEngine.match` still recovers its core
from the content, but reports `formatMatches = false` against `hu-coop`'s `CODE_128` rule; the add-card flow (#6)
must not treat that as a different store. The same holds for a GS1 QR or Data Matrix scan against a plain QR or
Data Matrix rule (#45).

Tesco's real Clubcard barcode is GS1-128 with AI `97` (seen 2026-10-19, #31). A scanned one is stored and drawn as
`GS1_128`, and so is a typed one: `hu-tesco`'s rule is `GS1_128` (#43, 2026-10-20).

Other GS1 carriers keep their mark since #45 (2026-10-21): a GS1 Data Matrix or QR scan is stored as
`GS1_DATA_MATRIX` / `GS1_QR_CODE` and drawn in GS1 mode, and a DataBar falls back to GS1-128
([[2026-10-21-gs1-mark-as-barcode-format]]).

## How to pay it down
Test at a Coop till ([#31](https://github.com/shockrifle/loyscan/issues/31)). If plain Code 128 doesn't scan, switch
`hu-coop`'s rule and default format to `GS1_128`. If it does, `hu-coop` stays `CODE_128` and this page is resolved.
