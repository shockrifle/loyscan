# "Other card": scan first, then the store, then the card

**Status:** Proposed (not built yet) · amends [[2026-10-22-custom-stores]],
[[2026-11-13-user-supplied-store-logos]], [[2026-10-22-store-suggestion-trigger]] · **Date:** 2026-11-20 ·
**Issue:** [#188](https://github.com/shockrifle/loyscan/issues/188)

"Other card" opens the scanner first, then a new-store form (name, logo, colour), then the card form. Catalogue stores
keep store → scanner → form.

## Context
- Today "Other card" saves the store first, on a form that asks for the barcode type, then scans
  ([[2026-10-22-custom-stores]], option 1a). With the type moved to the card
  ([[2026-11-20-barcode-type-per-card]]) the store form no longer needs it, and scanning first lets the scan inform
  the store's creation.
- The new-store form had no logo slot because the store has no id before Create
  ([[2026-11-13-user-supplied-store-logos]]).

## Options considered
1. **A scan a catalogue store's rule reads:** (a) a suggestion on the new-store form; (b) a question before it;
   (c) none until the card form.
2. **The logo before Create:** (a) a temporary file named on the form; (b) a draft store saved when the form opens.
3. **Back:** (a) each step replaces the one before, Back goes to the picker; (b) Back from the store form rescans.
4. **Duplicate names:** (a) allowed silently; (b) a hint; (c) refused.

## Decision
1a, 2a, 3a, 4b (the owner's choices in a grilling session, 2026-11-20).
- Scanner → new-store form → card form, each replacing the one before; Back from the form or that card form returns
  to the picker. A shared picture with a barcode (#77) skips the scanner; Enter manually or a picture without one
  reaches the new-store form, then the card form with an empty number.
- The form shows the scan ("Scanned · <number> · <type>", or "Number entered by hand"). A scan a catalogue rule reads
  shows "This looks like a <store> card" with **Use <store>**; a name matching a store's name or alias (ignoring case
  and accents, as the picker's search) shows a hint with **Use <store>**. The name hint fires when the name or an alias *equals* it after the
  search's normalisation (`StoreSearch.searchKey`), not when it merely contains it, which would hint at most stores
  for one letter. Create still works.
- **Use <store>** replaces the form with that store's card form, with the scan, or an empty number after Enter
  manually.
- The card form reached this way doesn't suggest again the stores passed over on the form.
- The logo slot offers Choose image and Find a logo online (no card photos exist yet) and the same square crop. The
  logo is a temporary file named in the form's saved state, saved with the store on Create; Back, replacing it or
  **Use <store>** deletes it at once (the store keeps its own logo), and the photo orphan sweep catches the rest.

## Why
- Most users know their card, not its barcode type: scanning first asks the easy question first and lets the scan
  catch a store the catalogue already has before a duplicate custom store is made.
- A temporary file reuses the staging the logo screen already has; a draft store would show in the picker and need
  its own cleanup.
- Replacing steps matches the scanner today: a scan is handed on once, so there is nothing to go back to.

## Consequences
- A custom store is still saved before its first card, so one abandoned on the card form stays in the picker
  ([[edit-store]] deletes it).
