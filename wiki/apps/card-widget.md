# Home-screen widget

A Glance app-widget that shows the user's cards as tiles; tapping one opens that card's [[card-detail]]
screen, even from a dead process (#11). Added 2026-11-04 as the first slice: brand-colour tiles with the
card's list label, no photo yet. One runtime surface alongside the [[android-app]] activity.

All paths are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.

## What it shows

- `widget/glance/CardWidget.kt` — `CardWidget : GlanceAppWidget`. `provideGlance` reads the cards and the sort
  preference from the repositories through a Hilt `@EntryPoint` (`CardWidgetEntryPoint`, the same trick as
  `hiltImageLoader()`, [[architecture]] → Composition/DI), collects them in `provideContent`, and renders
  `CardWidgetContent`. This renders the **current** cards whenever Glance asks (e.g. when the widget is added). The
  `widget.glance` sub-package holds the Glance runtime glue that runs only on a real home screen; the tested content
  and logic (`widget/CardWidgetContent.kt`, `CardWidgetTiles.kt`, `CardWidgetUpdater.kt`) stay in `widget`.
- **Staying in step with the cards** is a separate job: a Glance widget renders a snapshot and does not follow a
  `Flow` once its session ends, so something must redraw it when the data changes. `widget/CardWidgetUpdater.kt`
  observes the rendered tiles (`toWidgetTiles`) and refreshes the widget whenever they change — and only then, so an
  edit a tile doesn't show (a note, a number) doesn't redraw it. The two Glance calls it needs — "is a widget placed?"
  and "redraw them all" — are behind `CardWidgetRefresher` (impl `glance.GlanceCardWidgetRefresher`: `getGlanceIds` and
  `updateAll`), so the updater's logic is unit-tested with a fake and only the Glance impl is excluded from coverage.
  `ensureObserving()` is idempotent and called both from `LoyScanApplication.onCreate` (for a widget placed before the
  app launched) and from `CardWidgetReceiver.onUpdate` (for one placed, or shown after a restart, while the app runs),
  so a widget added mid-session is followed too. It observes nothing — and touches neither the database nor the
  preferences — while no widget is placed, which is the common case and every test that boots the app. A failure to
  read the cards or redraw is logged, not thrown (an app-scope `CoroutineExceptionHandler` and per-emission
  `try`/`catch`): the widget is optional and must not crash the app. **On-device check (#11): confirm the widget
  refreshes after a card is added, edited, deleted or re-sorted, including when the widget was placed mid-session.**
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
  with the card id and its store colour as extras (`EXTRA_OPEN_CARD_ID`, `EXTRA_OPEN_CARD_COLOR`) and
  `FLAG_ACTIVITY_SINGLE_TOP`. The widget wraps it in `actionStartActivity`.
- `Intent.openCard()` reads them back, returning `null` for a non-widget intent or one reopened from recents
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, as `sharedPicture()` does). The colour may be empty; the [[card-detail]]
  route tolerates an empty or malformed one.
- `MainActivity` sends each `openCard()` into an `openCards` `Channel` — on a cold start in `onCreate`
  (`savedInstanceState == null`, the **dead-process** case the issue asks about) and on a warm tap through
  `addOnNewIntentListener`. `MainActivity` is the only activity, so `SINGLE_TOP` makes a warm tap reach the running
  instance's `onNewIntent` rather than stack a second one. `AppNavigation` collects the channel and calls
  `NavController.openCard(cardId, color)` (`Navigation.kt`), which **pushes** `Detail(cardId, color)` over whatever is
  open (`launchSingleTop`, no `popUpTo`). Opening a card from the widget counts a use at a till, as tapping it in the
  list does (#4).
- **A half-filled add/edit form survives a widget tap** (#11, the decision mirrors #83): the card is pushed on top,
  so Back returns to the form with its input intact, rather than popping back to the list. Unlike a share, the tap
  does not forward to a LoyScan of its own; it opens in the running task.

**On-device check (#11):** confirm a warm tap (and a repeat tap) opens the right card in the running task without
spawning a second LoyScan or losing a half-filled form — the exact task behaviour of a `standard` activity launched
from a widget is hard to prove on Robolectric.

## Manifest

`widget/glance/CardWidgetReceiver.kt` (`CardWidgetReceiver : GlanceAppWidgetReceiver`) is declared in
`AndroidManifest.xml` as a non-exported `<receiver>` with the `APPWIDGET_UPDATE` filter and an
`android.appwidget.provider` meta-data pointing at `res/xml/card_widget_info.xml` (resizable, home-screen only,
Glance's `@layout/glance_default_loading_layout` as the initial layout). Glance 1.2.0
(`androidx.glance:glance-appwidget`, `gradle/libs.versions.toml`).

## Tests

- `widget/CardWidgetTilesTest` — the sort order, the label fallback and the colour helpers (Robolectric, because
  the colour parse goes through `android.graphics.Color`).
- `widget/CardWidgetContentTest` — `runGlanceAppWidgetUnitTest` (Glance's JVM unit test, `glance-appwidget-testing`):
  one clickable node per card, each showing its label. A new test seam — see [[tdd]] and [[architecture]]. The Glance
  unit API matches a click by component + `ActionParameters`, not by the intent extras the tile uses, so that a tile
  opens **its own** card is an on-device check, not a unit assertion.
- `widget/CardWidgetUpdaterTest` — the live-update trigger: it fires when a card or the sort changes the tiles, not on
  a change a tile doesn't show, never when no widget is placed, starts once a widget is placed mid-session, and a
  refresh that throws is swallowed so observation continues.
- `OpenCardIntentTest` — the intent round-trips, and is `null` for a bare intent or one from recents.
- `WidgetTapOpensCardTest` — end to end: `MainActivity` launched **cold** by `openCardIntent` opens that card's
  detail, and a **warm** `onNewIntent` opens it while the app is already running. Mirrors `ShareToLoyScanTest`. The
  cold launch is the dead-process case.
- `WidgetOpenCardDirectionsTest` — `NavController.openCard` opens the card over the list, and over a `CardForm`
  **without discarding it** (`TestNavHostController`).

The `com.bdaniel.loyscan.widget.glance` package (`CardWidget`, `CardWidgetReceiver`, `GlanceCardWidgetRefresher`) is
Glance/AppWidget glue that runs only on a real home screen — the `provideGlance` wiring, the system receiver and the
`updateAll`/`getGlanceIds` calls — so the package is excluded from the coverage gate, like the theme and the icons;
the logic it drives is tested above ([[2026-10-09-coverage-gate-kover]]).

## Not done yet (this slice)

- No front photo on a tile — a tile is the brand-colour box, as the list's non-photo tile is. Photo tiles are a
  follow-up.
- No empty-state message: a widget with no cards shows nothing.
- The live-update trigger only observes while the app process is alive. Cards change only through the running app,
  so that covers every real change; a widget added while the app is dead renders current cards when it is next shown.
- The on-device checks above (live refresh, warm/repeat tap) are why #11 stays open after this slice.

See [[2026-11-04-home-screen-widget]] for the decisions.
