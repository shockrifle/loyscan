# Home-screen widget

A Glance app-widget that shows the user's cards as tiles; tapping one opens that card's [[card-detail]]
screen, even from a dead process (#11). Added 2026-11-04 as the first slice: brand-colour tiles with the
card's list label, no photo yet. One runtime surface alongside the [[android-app]] activity.

All paths are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.

## What it shows

- `widget/CardWidget.kt` — `CardWidget : GlanceAppWidget`. `provideGlance` reads the cards and the sort
  preference from the repositories through a Hilt `@EntryPoint` (`CardWidgetEntryPoint`, the same trick as
  `hiltImageLoader()`, [[architecture]] → Composition/DI), collects them in `provideContent`, and renders
  `CardWidgetContent`. Collecting the `Flow`s inside the Glance composition is what makes the widget
  **follow the cards and the sort as they change** — no manual `updateAll`.
- `widget/CardWidgetTiles.kt` — the pure logic, unit-tested: `List<LoyaltyCard>.toWidgetTiles(sort)` sorts with
  the list's `sortedBy(sort)` ([[card-list]], #7) and maps each card to a `WidgetTile(cardId, color, label)`,
  the `label` being the card's label or, when it has none, the store name — the same text the list tile shows.
  `WidgetTile.backgroundColor()` / `.textColor()` reuse [[card-detail]]'s `toBrandColorOrNull()` and
  `contrastingTextColor()`, falling back to a neutral grey for an empty or malformed brand colour.
- `widget/CardWidgetContent` — the Glance composable: a `LazyColumn` of one clickable `Box` per tile, the box in
  the brand colour, a `Text` of the label. Each box's `clickable` is `actionStartActivity(openCardIntent(…))`.

## Opening a card — an intent extra, not a URI deep link

LoyScan has no URI deep links; external launches go through an intent that `MainActivity` reads and turns into
navigation (the share flow, [[add-card]], #77). The widget follows that pattern rather than adding
`navDeepLink`:

- `openCardIntent(context, cardId, color)` (`MainActivity.kt`) builds an explicit `Intent` to `MainActivity`
  with the card id and its store colour as extras (`EXTRA_OPEN_CARD_ID`, `EXTRA_OPEN_CARD_COLOR`). The widget
  wraps it in `actionStartActivity`.
- `Intent.openCard()` reads them back, returning `null` for a non-widget intent or one reopened from recents
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, as `sharedPicture()` does). The colour may be empty; the [[card-detail]]
  route tolerates an empty or malformed one.
- `MainActivity` sends each `openCard()` into an `openCards` `Channel` — on a cold start in `onCreate`
  (`savedInstanceState == null`, the **dead-process** case the issue asks about) and on a warm tap through
  `addOnNewIntentListener`. `AppNavigation` collects the channel and calls `NavController.openCard(cardId, color)`
  (`Navigation.kt`), which navigates to `Detail(cardId, color)` with `popUpTo<CardList>()`, so Back returns to
  the list. Opening a card from the widget counts a use at a till, as tapping it in the list does (#4).

A widget tap is a deliberate request for one card, so it opens over whatever is on screen — unlike a share,
which forwards to a LoyScan of its own to protect a half-filled form (#83).

## Manifest

`widget/CardWidgetReceiver.kt` (`CardWidgetReceiver : GlanceAppWidgetReceiver`) is declared in
`AndroidManifest.xml` as a non-exported `<receiver>` with the `APPWIDGET_UPDATE` filter and an
`android.appwidget.provider` meta-data pointing at `res/xml/card_widget_info.xml` (resizable, home-screen only,
Glance's `@layout/glance_default_loading_layout` as the initial layout). Glance 1.2.0
(`androidx.glance:glance-appwidget`, `gradle/libs.versions.toml`).

## Tests

- `widget/CardWidgetTilesTest` — the sort order, the label fallback and the colour helpers (Robolectric, because
  the colour parse goes through `android.graphics.Color`).
- `widget/CardWidgetContentTest` — `runGlanceAppWidgetUnitTest` (Glance's JVM unit test, `glance-appwidget-testing`):
  one clickable node per card, each showing its label. A new test seam — see [[tdd]] and [[architecture]].
- `OpenCardIntentTest` — the intent round-trips, and is `null` for a bare intent or one from recents.
- `WidgetTapOpensCardTest` — end to end: `MainActivity` launched **cold** by `openCardIntent` opens that card's
  detail, mirroring `ShareToLoyScanTest`. The cold launch is the dead-process case.

`CardWidget` and `CardWidgetReceiver` are Glance/AppWidget glue that runs only on a real home screen (the
`provideGlance` wiring and the system receiver), so they are excluded from the coverage gate, like the theme and
the icons; the logic they show is tested above ([[2026-10-09-coverage-gate-kover]]).

## Not done yet (this slice)

- No front photo on a tile — a tile is the brand-colour box, as the list's non-photo tile is. Photo tiles are a
  follow-up.
- No empty-state message: a widget with no cards shows nothing.

See [[2026-11-04-home-screen-widget]] for the decisions.
