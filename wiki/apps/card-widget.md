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
  so a widget added mid-session is followed too. It observes nothing — and reads nothing from the database or the
  preferences — while no widget is placed, which is the common case and every test that boots the app. A failure to
  read the cards or redraw is logged, not thrown (an app-scope `CoroutineExceptionHandler` and per-emission
  `try`/`catch`): the widget is optional and must not crash the app. **On-device check (#102): verified on a device
  (ASUS AI2302, Android 15, 2026-11-05).** A widget placed mid-session (app already running) drew the current cards at
  once; adding a card made its tile appear and deleting one made its tile disappear, without reopening the app. **Edit and
  re-sort verified on the same device on 2026-11-12:** giving a card a label redrew its tile with the label, switching the
  list's sort from Name to Recently used reordered the tiles, and clearing the label put the store name back, each
  without reopening the app.
- `widget/CardWidgetTiles.kt` — the pure logic, unit-tested: `List<LoyaltyCard>.toWidgetTiles(sort)` sorts with
  the list's `sortedBy(sort)` ([[card-list]], #7) and maps each card to a `WidgetTile(cardId, color, initials,
  storeName, label)`. The `label` is the card's own label, `null` when it has none: since #181 a tile never shows the
  store name, the same rule as My cards since #183 (until #181 an unlabelled card's widget tile showed the store name).
  `storeName` is only for TalkBack: `WidgetTile.description()` is "store, label", or the store alone, as the list's tile
  joins them, and the tile carries it as its `semantics { contentDescription }`.
  `WidgetTile.backgroundColor()` / `.textColor()` reuse [[card-detail]]'s `toBrandColorOrNull()` and
  `contrastingTextColor()`, falling back to a neutral grey for an empty or malformed brand colour.
- `widget/CardWidgetContent` — the Glance composable (since #108, [[design-system]]): a `LazyVerticalGrid` of
  card-ratio tiles, one column below 240dp of widget width and two from 240dp, on `appWidgetBackground()`. A tile is
  the brand colour with the store's initials over the label, like the list's plain tile. From Android 12 the widget
  rounds its own background with `WIDGET_CORNER_RADIUS` (12dp, `ui/theme/Shapes.kt`), and a tile's corners are that
  less the 4dp between the widget's edge and the tile, 8dp, so they nest concentrically (#182, `widgetBackgroundRadius`,
  `widgetTileCornerRadius`). The widget rounds its background itself because the launcher otherwise rounds it with a
  radius the app can't read. Launcher3 skips its own rounding when the background view already clips to its outline,
  and Nova did the same on the device below; a launcher that forces its own radius would still show unnested corners. On an Android 15 phone with Nova launcher (2026-11-24) the platform's
  `system_app_widget_background_radius` was 28dp, while Nova drew the background at about 22dp, so tiles derived from
  the platform value didn't match it; rounding the background with that 28dp did match, but made the small tiles look
  like pills, hence the fixed 12dp. Before Android 12 the widget's background is square, and so are the tiles
  (Glance's `cornerRadius` does nothing on 10–11). **Checked on devices on 2026-11-24:** the 12dp background, 8dp
  tiles and 4dp spacing looked right on an Android 15 phone with Nova and an Android 14 handheld. Until #182 the corners were 6% of the tile's height, like the app's
  `CardShape`, unrelated to the background's. Glance has no `aspectRatio` or grid spacing, so `widget/WidgetLayout.kt`
  computes the columns and the tile height from `LocalSize`, and 2dp of padding around the grid and around each tile
  (`WIDGET_HALF_SPACING`) makes the 4dp edge and the 4dp gap between tiles (`WIDGET_SPACING`, #182; both were 8dp).
  A tile fills its cell's real width. The widget is laid out for 16 sizes (`SizeMode.Responsive(WIDGET_SIZES)`), widths from 110 to 410dp, each
  40dp high, so from Android 12 the home screen picks the widest that fits the widget's real width; the steps keep a
  tile within 10% of the card's ratio up to 440dp (`WidgetLayoutTest.theLayoutChosenForAnyPhoneWidth_…`), and a wider
  widget's tiles are flatter. It was `SizeMode.Exact`, which lays out the sizes the launcher reports: on a device
  (2026-11-12, Nova launcher) it reported 131×432dp and a landscape 263dp-wide size for a widget about 131×386dp, none
  fit, Android showed the smallest, the landscape layout, and its fixed-width tile was cut to half.
  A launcher can still show a layout made for another width: the Android 12L launcher on a tablet kept the 410dp
  layout after the widget was resized to ~215dp (2026-11-12). Tiles fill their cells, so nothing is cut, but they are
  taller than a card until the next update. Giving tiles a native ratio instead (a card-ratio image that fills the cell
  with `ContentScale.Fit`, which turns on `adjustViewBounds`) was tried and failed: in the grid the image kept its
  intrinsic height. A widget can't measure its real size, so this is as far as the layout goes. Each
  tile's `clickable` is `actionStartActivity(openCardIntent(…))`. At its narrowest (110dp) the widget can't be resized
  below one tile's height: `minResizeHeight` is 90dp (`res/xml/card_widget_info.xml`; it was 40dp, a thin strip). A wider
  one-column widget has taller tiles, so resized to 90dp it cuts its tile; the launcher has no per-width minimum.
  Glance's unit test can't see a node's size or the grid's columns, so the composable's use of `widgetColumns` and
  `widgetTileSize` is checked on a device (2026-11-12: one column at ~131dp, two at ~278dp, tiles uncut), and only the
  functions are tested. Nor is the widget checked at a large font
  scale: a small tile's two lines can be cut there.

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

**On-device check (#102): verified on a device (ASUS AI2302, Android 15, 2026-11-05).** With a half-filled add form
open and the app running, a warm tap on a tile opened that tile's card in the same task (`dumpsys` showed the one task
`#40488`, no second LoyScan), and Back returned to the form with its text intact; a repeat tap on another tile opened
that card in the same task. Each tile opened its own card (Auchan tile → Auchan, Tesco tile → Tesco), confirming the
intent-extras match the Glance unit test can't assert. The exact task behaviour of a `standard` activity launched from
a widget is hard to prove on Robolectric.

## Manifest

`widget/glance/CardWidgetReceiver.kt` (`CardWidgetReceiver : GlanceAppWidgetReceiver`) is declared in
`AndroidManifest.xml` as a non-exported `<receiver>` with the `APPWIDGET_UPDATE` filter and an
`android.appwidget.provider` meta-data pointing at `res/xml/card_widget_info.xml` (resizable, home-screen only,
Glance's `@layout/glance_default_loading_layout` as the initial layout). Glance 1.2.0
(`androidx.glance:glance-appwidget`, `gradle/libs.versions.toml`).

## Tests

- `widget/WidgetLayoutTest` — the column count by width, the tile size and ratio, the 4dp spacing, the tile's corner
  radius from the background's, and the height of one row, against values worked out by hand (plain JUnit).
  It also checks the background radius is 12dp from Android 12 and none before (`widgetBackgroundRadius(sdkInt)`);
  `ui/theme/ThemeTest` pins `WIDGET_CORNER_RADIUS`. `widget/CardWidgetInfoTest` reads the provider XML through
  the app's resources and checks `minResizeHeight` fits one tile at `minResizeWidth` (#108).
- `widget/CardWidgetTilesTest` — the sort order, the label fallback, the initials and the colour helpers (Robolectric, because
  the colour parse goes through `android.graphics.Color`).
- `widget/CardWidgetContentTest` — `runGlanceAppWidgetUnitTest` (Glance's JVM unit test, `glance-appwidget-testing`):
  one clickable node per card, each showing its initials and label (an unlabelled one only its initials, never the store
  name), described for TalkBack as "store, label" (`hasContentDescriptionEqualTo`, #181), at a set widget size
  (`setAppWidgetSize`). Since
  #182 it also reads a node's Glance modifiers (`MappedNode.emittable.modifier`): the grid and each tile's cell have 2dp
  of padding; at API 32 the background has the 12dp corner radius and each tile 8dp, and at API 29 neither is rounded. Glance's `CornerRadiusModifier` is internal to Kotlin,
  so the test reads its radius by reflection. That the corners look concentric on a home screen is checked by hand. A new test seam — see [[tdd]] and [[architecture]]. The Glance
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

- No front photo on a tile — a tile is the brand-colour tile with the initials, as the list's non-photo tile is. Photo tiles are a
  follow-up.
- No empty-state message: a widget with no cards shows nothing.
- The live-update trigger only observes while the app process is alive. Cards change only through the running app,
  so that covers every real change; a widget added while the app is dead renders current cards when it is next shown.
- The on-device checks above (live refresh, warm/repeat tap, a tile opening its own card) were the open part of
  #11 after this slice; they passed on a device on 2026-11-05 (#102), and edit and re-sort on 2026-11-12.

See [[2026-11-04-home-screen-widget]] for the decisions.
