# The `List` route shadows `kotlin.collections.List`

**Status:** Active · **Since:** 2025-12-09 (`b91d813`) · **Where:**
`app/src/main/java/com/bdaniel/loyscan/Navigation.kt:7-8`

## What
The start-destination route is `@Serializable object List` in package `com.bdaniel.loyscan`. Inside that package,
an unqualified `List<…>` resolves to the route object, not the collection type, and fails to compile.

## Why it exists
`b91d813` introduced the route and, in the same commit, removed the explicit type
`StateFlow<List<LoyaltyCard>>` from `ListViewModel.cards`, relying on inference instead
(`app/src/main/java/com/bdaniel/loyscan/ListViewModel.kt:16`). Dropping the annotation is the workaround (inferred
from the diff).

## Risk / impact
Anyone writing a function signature with `List<…>` in the root package (screens, ViewModels) gets a confusing
compile error. `emptyList()` / `listOf()` are unaffected.

## How to pay it down
Rename the route (e.g. `CardListRoute`) and update `Navigation.kt` and `MainActivity.kt`. Until then, use
inference or `kotlin.collections.List<…>`. Also in [[faq]].
