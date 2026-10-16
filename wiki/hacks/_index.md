# Hacks

Short-lived workarounds: `<slug>.md`. Same skeleton as tech debt — see [[CLAUDE]]. A hack that outlives its
"short-lived" intent should be re-filed as tech debt. When removed, mark it `Removed (<sha>)` and **move** it
below.

## Active

- [[list-route-shadows-kotlin-list]] — route `object List` hides `kotlin.collections.List`; types are inferred instead
- [[scanner-result-dialog-until-add-card-flow]] — "Add card" opens the scanner directly and a scan is only shown (#39)

## Removed

- [[line-based-sql-seed-parser]] — the seed SQL had to be one statement per line; replaced by the catalogue loader and
  a Kotlin demo seed (#18)
