# An empty or invalid brand color crashes the detail screen

**Status:** Resolved 2026-10-09 (`12357a3`, #14) · **Issue:** [#14](https://github.com/shockrifle/loyscan/issues/14) · **Since:** 2025-12-12 (`45a33e1`) · **Where:**
`DetailScreen`'s `Color(color.toColorInt())` and the header `Box` background / text color below it, in
`app/src/main/java/com/bdaniel/loyscan/DetailScreen.kt` (as of `45a33e1`)

**Resolved:** both screens now parse with `String.toBrandColorOrNull()`
(`app/src/main/java/com/bdaniel/loyscan/ui/BrandColor.kt`) and fall back to `surfaceVariant`. It was written test-first:
`DetailScreenTest.emptyStoreColor_doesNotCrash` / `malformedStoreColor_doesNotCrash` and
`ListScreenTest.emptyOrMalformedStoreColor_doesNotCrash` (named `…BrandColor…` until brands became stores) failed before the fix, with `StringIndexOutOfBoundsException`
for `""` and `IllegalArgumentException: Unknown color` for a malformed string. The list had the same bug for
malformed colours: its guard only checked `isNotEmpty()`.

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
