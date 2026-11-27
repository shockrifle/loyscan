# Barcode type per card, chosen on the card form

**Status:** Partly built (2026-11-27, #187 slice 1: the stored type, the migration, the rule on and off, the type the
card form saves; not yet the selector or the custom-store forms without a type) · amends [[2026-10-22-custom-stores]] · **Date:** 2026-11-20 ·
**Issue:** [#187](https://github.com/shockrifle/loyscan/issues/187)

A typed number's barcode type is saved on the card and chosen on the card form, for every store. A custom store's
form no longer asks for it.

## Context
- A card has no type of its own: `LoyaltyCard.barcodeFormat` is the scan's `scannedFormat`, else the store rule's
  format, else `Store.defaultBarcodeFormat` ([[2026-10-12-data-model-v2]]).
- A custom store has no rule, so "Other card" asks for the type on the store form
  ([[2026-10-22-custom-stores]], option 2a). The owner found the question too technical for most users (2026-11-20).
- A catalogue store may bring out a new card in a type its rule doesn't describe, and there is no way to say so.

## Options considered
1. **Where the type is kept:** (a) an optional per-card override, empty unless the user changes it; (b) saved on
   every card.
2. **A scanned card:** (a) no selector, the scan is drawn as scanned; (b) the selector too, redrawing the scan.
3. **The store's rule when another type is picked:** (a) off, the number drawn as typed; (b) still applied, with the
   "doesn't fit this type" check.
4. **How the app knows the rule is off:** (a) the card's type differs from the rule's; (b) a separate flag.
5. **The custom store's type:** (a) kept as the starting value, no longer asked; (b) dropped.

## Decision
1b, 2a, 3a, 4a, 5a (the owner's choices in a grilling session, 2026-11-20).
- Every saved card stores its type. A migration writes each existing typed card's current type (rule type, else store
  default); scans keep `scannedFormat`.
- The card form shows the type collapsed; for a typed or edited number, tapping it opens a selector of
  `BarcodeFormat.forCustomStores` plus the store's own type when it isn't among them. A scan shows
  "Scanned · <type>" without one while its number is untouched. Editing a scan's number makes it typed; it then starts
  at the scan's type when the selector offers it, else at the store's starting type below. Editing a card shows and
  keeps its saved type.
  *Amended 2026-11-27 (#187 review, the owner's choice):* an edited scan that the store's rule reads starts at the
  rule's type instead. The field shows the core the rule recovered from the scan, not the scanned content, so in the
  scan's own type with the rule off that core would become the barcode: a Tesco card read without its GS1 mark would
  lose the rule's prefix and check digit, and the form would call it valid.
- A new typed card starts at the rule's type, else the store's default. The rule makes the barcode content while the
  card's type equals the rule's; any other type draws the number as typed.
- Neither custom-store form asks for a type; a new custom store's default is Code 128, an existing one keeps its own.

## Why
- The type is a property of the card the user holds, not of the store: the question moves to where the card is, and
  is out of the way (collapsed) for the many who never need it.
- Saving it on every card fixes each barcode at save time, so a later catalogue rule change can't redraw a card that
  works at the till; the migration keeps today's barcodes as they are.
- "A new card" from a known store most likely doesn't follow the old rule, so another type turns the rule off.
  Comparing types needs no extra column, and picking the rule's type again turns it back on.

## Consequences
- A Room schema change with a migration ([[2026-11-05-room-schema-frozen-from-v2]], [[database-schema-changes]]).
  The rule's type is inside the store's JSON `cardNumberRule` column, so the migration has to parse it in Kotlin
  rather than copy a column; a mistake there would change a barcode, so it needs a migration test per case.
- A catalogue rule whose type changes later stops applying to cards saved with the old type; they keep their
  barcode, drawn as typed.
- Editing a custom store's type no longer redraws its typed cards ([[edit-store]] changes with it).
- "Other card" can be reordered so the scan comes first ([[2026-11-20-other-card-scan-first]]).
