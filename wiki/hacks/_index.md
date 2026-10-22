# Hacks

Short-lived workarounds: `<slug>.md`. Same skeleton as tech debt — see [[CLAUDE]]. A hack that outlives its
"short-lived" intent should be re-filed as tech debt. When removed, mark it `Removed (<sha>)` and **move** it
below.

## Active

None.

## Removed

- [[list-route-shadows-kotlin-list]] — route `object List` hid `kotlin.collections.List`; renamed to `CardList` (#17)
- [[scanner-result-dialog-until-add-card-flow]] — a scan was only shown in a dialog, and "Enter manually" went back;
  replaced by the card form (#39)
- [[line-based-sql-seed-parser]] — the seed SQL had to be one statement per line; replaced by the catalogue loader and
  a Kotlin demo seed (#18)
