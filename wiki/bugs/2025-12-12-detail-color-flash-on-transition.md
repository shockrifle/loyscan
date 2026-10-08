# Detail screen colorless during the shared-element transition

**Fixed in:** `45a33e1` · **Date:** 2025-12-12 · **Area:** [[card-transition]], [[card-detail]]

Reconstructed from the diff of `45a33e1` ("transition fixes"); the commit message has no detail.

## Symptom
Going from the list to a card's detail, the destination's top bar and header started **transparent** and only
took on the brand color once data arrived, so the morph from a colored tile wasn't seamless. The shared element
was also the detail screen's text `Card`, not the colored area, so the colored tile morphed into the white card
body.

## Root cause
`DetailScreen` computed `color` from `viewModel.card` — a `StateFlow` with `initialValue = LoyaltyCard.EMPTY`
fed by an async Room query (`DetailViewModel.kt:24-31`). On the first frames `card.brand.color == ""`, which the
old code mapped to `Color.Transparent`. Shared-element transitions render the destination immediately, so the
async gap is visible.

## Fix
- The brand color travels in the route: `Detail(cardId, color)` → [[2025-12-12-brand-color-in-route]].
- The shared element moved: the list tile's **colored box** ↔ the detail **`Scaffold`**; key `item/{id}` →
  `card/{id}`.
- The detail header shows the brand name (contrast-colored) when there's no logo, matching the list tile.

## Lessons
- **Async ViewModel state + transitions = first-frame glitches.** Whatever a transition's first frame needs must
  be synchronous (route args, or shared state already in memory).
- The same class of problem probably still affects the shared-element key —
  [[shared-element-key-from-async-state]].
