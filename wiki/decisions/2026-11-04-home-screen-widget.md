# Home-screen widget: Glance, an intent extra, and a first slice

**Status:** Active · tile shape and layout amended by [[2026-11-12-design-tokens-and-card-shape]] (#108) · **Date:** 2026-11-04 · **Issue:** [#11](https://github.com/shockrifle/loyscan/issues/11) (open: slice) · **Commits:** 61e9d13, ccbeb60, 3eead59

The home-screen widget ([[card-widget]]), and the three choices that shaped its first slice.

## Context

#11 asks for a widget showing the cards as tiles, following the list's sort, that opens a card's detail on a
tap — "even when the app process was dead". LoyScan already launches into targets from outside through an
intent `MainActivity` reads, not through URI deep links (the share flow, [[add-card]], #77).

## Decisions

1. **Glance, not RemoteViews by hand.** `androidx.glance:glance-appwidget` 1.2.0. Glance is the current
   AndroidX way to build widgets with a Compose-like API, and it has a JVM unit-test library
   (`glance-appwidget-testing`, `runGlanceAppWidgetUnitTest`) that fits the project's test-first gate — the
   content composable is asserted on the JVM without inflating RemoteViews.
2. **Open a card via an intent extra, not a `navDeepLink`.** `openCardIntent` carries the card id + colour as
   extras; `MainActivity.openCard()` reads them into an `openCards` `Channel` that `AppNavigation` collects, the
   same shape as the shared-picture channel (#77). A cold intent in `onCreate` is the dead-process case, so it
   needed no extra machinery. Adding URI deep links would have been a second, parallel way into the app.
3. **Brand-colour tiles, no photo (this slice).** A tile is the card's colour box with its list label, mirroring
   the list's non-photo tile. Glance renders RemoteViews, so a photo would mean loading a `Bitmap` into the
   remote view — more work and risk. Deferred to a follow-up.
   *Amended 2026-11-12 (#108):* the full-width label strips became card-ratio tiles with the store's initials over the
   label, in one or two columns by width ([[2026-11-12-design-tokens-and-card-shape]]). Still no photo.

## Why

- **Intent extra over deep link:** one launch mechanism, already understood and tested (`ShareToLoyScanTest`),
  reused. The widget's "process was dead" requirement is just a cold `Intent`, which the channel handles.
- **Glance:** without its unit-test library the widget content would be untestable on the JVM and the 97 %
  coverage gate ([[2026-10-09-coverage-gate-kover]]) would have blocked the feature or forced a large exclusion.

## Consequences

- The irreducible Glance/AppWidget glue lives in the `com.bdaniel.loyscan.widget.glance` package (`CardWidget`'s
  `provideGlance` wiring, `CardWidgetReceiver`, `GlanceCardWidgetRefresher`), runs only on a real home screen, and is
  excluded from coverage by package, like the theme and icons. All logic (`toWidgetTiles`, the colour helpers, the
  live-update trigger, the intent, the navigation) stays in `com.bdaniel.loyscan.widget` and is tested.
- **Staying in step with the cards** needs an explicit trigger, because Glance renders a snapshot and stops
  following a `Flow` once its session ends. `CardWidgetUpdater.ensureObserving()` observes the tiles and calls
  `updateAll` on a change; it is (idempotently) started both from the Application and from the receiver's `onUpdate`,
  so a widget placed mid-session is followed, and it observes nothing — reading nothing from the database or the
  preferences — while no widget is placed. A failure never propagates: the widget is optional and must not crash the app.
- **A warm tap opens the card without discarding a half-filled form** (the #83 concern, decided for the widget):
  `openCard` pushes the card over whatever is open instead of popping to the list, and `SINGLE_TOP` lands the tap in
  the running task. The exact task behaviour of a `standard` activity launched from a widget can't be proven on
  Robolectric, so it is an on-device check and **#11 stays open** (`Refs`, not `Fixes`) until confirmed.
- `glance-testing`'s unit API matches a tile's click by component, not by intent extras, so the "right card is
  opened" guarantee rests on `CardWidgetTilesTest` (cardId↔tile) plus the intent/navigation tests, not on the
  content test — another on-device check.
- **Follow-ups (not built):** front-photo tiles, and an empty-state message when there are no cards.
