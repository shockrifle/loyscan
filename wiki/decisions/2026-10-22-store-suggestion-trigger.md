# When the card form offers another store for a scan

**Status:** Active · **Date:** 2026-10-22 · **Issue:** [#6](https://github.com/shockrifle/loyscan/issues/6) (step 5),
[#47](https://github.com/shockrifle/loyscan/issues/47)

## Context
Step 5 of #6: "if the scanned content matches another store's rule better, suggest it". The user picks a store
before scanning, so a scan can belong to another store. `CardNumberEngine.match` says whether a rule can have
produced the scan, and `formatMatches` says whether the rule's symbology agrees (since #47 the GS1 mark is ignored
there, [[2026-10-21-gs1-mark-as-barcode-format]]). The shipped catalogue has loose rules: SuperShop reads any 16
digits in Code 128, so it also reads a Tesco GS1-128 barcode; Auchan reads any valid EAN-13 (#32); four stores have
no rule at all ([[store-catalogue]]).

## Options considered
1. **Only when the picked store's rule rejects the scan.** First choice. The review found it saves a Tesco scan as
   SuperShop without a hint when SuperShop is picked.
2. **Also when the picked rule reads the scan only by symbology** (Code 128 against GS1-128): offer the stores
   whose rule names the scan's exact format.
3. **Also when the picked store has no rule:** offer every store whose rule reads the scan.
4. **Also when a more specific rule reads it** (one with a required start, against Auchan's catch-all EAN-13 rule).

## Decision
Options 1, 2 and 3 (user's choice, 2026-10-22, after the review). `suggestedStores` in
`app/src/main/java/com/bdaniel/loyscan/CardFormViewModel.kt`:
- picked store has no rule, or its rule rejects the scan → every other store whose rule reads it;
- its rule reads the scan, the scan has the GS1 mark and the rule names the same symbology without it → the stores
  whose rule names the scan's exact (GS1) format;
- otherwise none: its rule reads the scan in the exact format, without the GS1 mark the rule has (a plain read of a
  Tesco card), or in another symbology; or the number is typed.

Option 2 covers only the direction where the scan carries the mark. The reverse offered SuperShop for a Tesco card
read as plain Code 128 under the picked Tesco, which #47 rules out (found in the second review round).

Every matching store is offered, ranked by `matchingScan` (`domain/StoreSearch.kt`): exact format, then the same
symbology, then the rest, each by name. The user picks; nothing is switched automatically. Picking one makes the form
that store's and keeps the scan and the label ([[add-card]]).

## Why
The exact format is the strongest evidence a scan gives: the GS1 mark is printed on purpose, while a missing one
may only mean the scanner read it as plain Code 128, and the catalogue's
only GS1 store (Tesco) has a prefix and a check digit that SuperShop's rule lacks. A store without a rule says nothing
about a scan, so any store that does read it is a better match. Option 4 was left out: it needs a notion of how
specific a rule is, and the case it catches (Auchan) goes away once Auchan's prefix is confirmed (#31, #32). Offering
every match instead of the best one avoids guessing between stores whose rules read the same number.

## Consequences
- With Auchan picked, a dm, Penny or Rossmann scan is still saved as Auchan without a hint, until #32's last criterion.
- A store without a rule gets false hints from the catch-all rules: a card whose length fits is offered SuperShop
  (16 digits), Praktiker (9–10), Shell ClubSmart (17) or mömax (10-digit ITF), with no problem shown. The user picks,
  so nothing is switched; confirming those stores' prefixes (#31) narrows it. Found in review.
- A typed number never gets a suggestion.
- Tests: `CardFormViewModelTest` (each case above), `StoreSearchTest` (ranking),
  `ShippedCatalogueTest.aTescoScan_offersTescoFirst`, `CardFormScreenTest`.
