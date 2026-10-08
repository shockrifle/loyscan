# An empty or invalid brand color crashes the detail screen

**Status:** Open (latent) · **Issue:** [#14](https://github.com/shockrifle/loyscan/issues/14) · **Since:** 2025-12-12 (`45a33e1`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt:64` and the header `Box` background / text color below it

## What
`DetailScreen` calls `Color(color.toColorInt())` on the route's `color` with no guard. `String.toColorInt()`
throws `IllegalArgumentException` for `""` or any unparseable string. `CardItem` *does* guard
(`isNotEmpty()` → fall back to `surfaceVariant`) but forwards the raw string to `toCard(id, color)`.

## Why it exists
All seed brands have valid `#RRGGBB` colors, and there's no UI to create brands, so the path is unreachable today.

## Risk / impact
A crash on opening any card whose brand has an empty or malformed color — this becomes reachable as soon as
brands come from user input or an import.

## How to pay it down
Parse once into a nullable color (e.g. `runCatching { Color(s.toColorInt()) }.getOrNull()`) shared by both
screens, with the same fallback; or validate at the data boundary (mapper) so `Brand.color` is always valid.
