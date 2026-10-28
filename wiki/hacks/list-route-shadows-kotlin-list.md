# The `List` route shadows `kotlin.collections.List`

**Status:** Removed (`5cb58eb`, 2026-10-22, #17) · **Issue:** [#17](https://github.com/shockrifle/loyscan/issues/17) · **Since:** 2025-12-09 (`a33481a`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:10-11`

**Removed:** 2026-10-22 (`5cb58eb`). The route is now `object CardList`
(`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:10-11`), so `List<…>` means the collection again in the root
package. `ListViewModel.cards` has its explicit `StateFlow<List<LoyaltyCard>>` type back, and the
`kotlin.collections.List<…>` spellings in `CardFormViewModel`, `CustomStoreViewModel` and `ui/scanner/` are plain
`List<…>`. What it was:

## What
The start-destination route is `@Serializable object List` in package `com.bdaniel.loyscan`. Inside that package,
an unqualified `List<…>` resolves to the route object, not the collection type, and fails to compile.

## Why it exists
`a33481a` introduced the route and, in the same commit, removed the explicit type
`StateFlow<List<LoyaltyCard>>` from `ListViewModel.cards`, relying on inference instead
(`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt:16`). Dropping the annotation is the workaround (inferred
from the diff).

## Risk / impact
Anyone writing a function signature with `List<…>` in the root package (screens, ViewModels) gets a confusing
compile error. `emptyList()` / `listOf()` are unaffected.

## How to pay it down
Rename the route (e.g. `CardListRoute`) and update `Navigation.kt` and `MainActivity.kt`. Until then, use
inference or `kotlin.collections.List<…>`.
