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
must not treat that as a different store.

Tesco's real Clubcard barcode is GS1-128 with AI `97` (seen 2026-10-19, #31). A scanned one is stored and drawn as
`GS1_128`; a typed one can't be built by a rule yet ([#43](https://github.com/shockrifle/loyscan/issues/43)).

The mark of other GS1 carriers isn't kept: `LoyaltyCard` has no GS1 field, so a scanned GS1 Data Matrix or GS1 QR
would be saved and drawn without GS1 mode, and a DataBar as plain Code 128 ([[zxing-cpp]] → Contract).

## How to pay it down
Test at a Coop till ([#31](https://github.com/shockrifle/loyscan/issues/31)). If plain Code 128 doesn't scan, switch
`hu-coop`'s rule and default format to `GS1_128`. If it does, `hu-coop` stays `CODE_128` and this page is resolved.
