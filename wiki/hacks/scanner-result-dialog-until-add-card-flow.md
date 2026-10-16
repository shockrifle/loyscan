# The scanner only shows its result, until the add-card flow exists

**Status:** Active · **Issue:** [#39](https://github.com/shockrifle/loyscan/issues/39) · **Since:** 2026-10-16 (#5) ·
**Where:** `scannerDirections()` in `app/src/main/java/com/bdaniel/loyscan/Navigation.kt`, `ScanResultDialog.kt`

## What
The list's "Add card" button opens the [[barcode-scanner]] directly. A scan replaces the scanner with a `ScanResult`
dialog over the list that shows the format and the content, and saves nothing. "Enter manually" goes back to the
list.

## Why it exists
The scanner (#5) came before the add-card flow (#6) it belongs to, and needed a way in so it can be checked on a real
device. The user chose this over a debug-only entry or none (2026-10-16).

## Risk / impact
A user can scan but not save a card, and "Enter manually" looks like it does nothing. Fine for development builds;
not for a release.

## How to pay it down
In #6: point `ListDirections.toScanner` (or its successor) at the store picker, send `ScannerDirections.onScanned` to
the edit form with the scan, make `toManualEntry` open the form with an empty number, and delete `ScanResult`,
`ScanResultDialog` and `ScanResultDialogTest`. `ScannerDirectionsTest` then tests the new targets.
