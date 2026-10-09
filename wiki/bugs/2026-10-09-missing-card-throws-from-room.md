# A missing card crashed the detail screen instead of showing nothing

**Fixed in:** #30 (found while backfilling tests) · **Date:** 2026-10-09 · **Area:** [[card-detail]]
(`LoyaltyCardDao.getByIdWithBrand`, `LoyaltyCardRepository.getByIdWithBrand`)

## Symptom
Never seen on a device; found by a test. `getByIdWithBrand(id)` for an id with no row threw
`IllegalStateException: The query result was empty, but expected a single row to return a NON-NULL object …`
instead of emitting. Inside `DetailViewModel`'s `stateIn(viewModelScope, …)` that is an uncaught exception, so
the app would crash. That happens when a card is deleted while its detail screen is open, or for a stale route
(process death after a delete). Neither is reachable today: there is no delete UI yet.

## Root cause
The DAO declared `Flow<LoyaltyCardWithBrand>` (non-null). For a single-row query with a **non-null** Kotlin
return type, Room's generated code throws when no row matches; it only emits `null` when the type is nullable.
`DetailViewModel` already had `.filterNotNull()`, so the author expected `null`, but nothing ever produced it.
The test fake hid the problem: it used `mapNotNull`, so it skipped instead of throwing.

## Fix
`getByIdWithBrand` returns `Flow<…?>` in the DAO, the repository interface and the fake. The repository maps
with `it?.toDomainModel()`. `DetailViewModel.filterNotNull()` now does its job: it shows `LoyaltyCard.EMPTY`
until the card exists, and keeps the last card if it is deleted. Tests: `LoyaltyCardDaoTest`,
`RoomLoyaltyCardRepositoryTest` and `DetailViewModelTest` (`…_unknownId_emitsNull`, `card_staysEmpty_…`,
`card_keepsTheLastCard_…`).

## Lessons
- **A Room query that returns one row from a `WHERE` must have a nullable return type** (`Flow<T?>`, `T?`), unless
  a missing row really is a programming error. This applies to `BrandDao.getById` too, which is unused today.
- Fakes must copy the real thing's edge cases. A fake that's kinder than the real implementation hides the bug.
