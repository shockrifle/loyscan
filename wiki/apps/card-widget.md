# Home-screen widget

A Glance app-widget that shows the user's cards as tiles; tapping one opens that card's [[card-detail]]
screen, even from a dead process (#11). Added 2026-11-04 as the first slice: brand-colour tiles with the
card's list label, no photo yet; since #181 (2026-11-24) a tile shows its store's logo ([[store-logo]]), and since #185
(2026-11-26) the card's front photo when the card says so ([[card-photos]]). One runtime surface alongside the
[[android-app]] activity.

All paths are under `app/src/main/java/com/bdaniel/loyscan/` unless written in full.

## What it shows

- `widget/glance/CardWidget.kt` — `CardWidget : GlanceAppWidget`. `provideGlance` reads the cards and the sort
  preference from the repositories through a Hilt `@EntryPoint` (`CardWidgetEntryPoint`, the same trick as
  `hiltImageLoader()`, [[architecture]] → Composition/DI), maps them to tiles and their pictures (`WidgetPictures.forTiles`,
  below), collects that in `provideContent`, and renders `CardWidgetContent`. The first tiles and pictures are read before
  `provideContent`, so a tile never draws its initials before its logo or photo arrives. This renders the **current** cards whenever Glance asks (e.g. when the widget is added). The
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
  storeName, label, logo, photo)`, `logo` being the store's logo file name and `photo` the card's front photo's, set only
  while the card's "Show photo on widget" is on (`LoyaltyCard.showPhotoOnWidget`, #185; separate from My cards'
  `showPhotoOnList`). The `label` is the card's own label, `null` when it has none: since #181 a tile never shows the
  store name, the same rule as My cards since #183 (until #181 an unlabelled card's widget tile showed the store name).
  `storeName` is only for TalkBack: `WidgetTile.description()` is "store, label", or the store alone, as the list's tile
  joins them, and the tile carries it as its `semantics { contentDescription }`.
  `WidgetTile.backgroundColor()` / `.textColor()` reuse [[card-detail]]'s `toBrandColorOrNull()` and
  `contrastingTextColor()`, falling back to a neutral grey for an empty or malformed brand colour.
- `widget/CardWidgetContent` — the Glance composable (since #108, [[design-system]]): a `LazyVerticalGrid` of
  tiles, one column below 240dp of widget width and two from 240dp, on `appWidgetBackground()`. A tile narrower than
  120dp is square, a wider one has the card's ratio (#216): at the card's ratio a narrow tile was too low for a logo and
  its label. A tile is
  the brand colour with the store's initials over the label, like the list's plain tile. **A tile whose store's logo
  decoded** (#181) shows the logo instead of the initials, fitted whole (`ContentScale.Fit`) inside an 8dp margin on
  every side (`WIDGET_LOGO_MARGIN`, `Spacing.s`, the margin chosen while planning #181), and the label, if any, at the
  bottom edge over the logo, in white on a scrim behind the label alone: a fade from clear at the top of the label's
  box (4dp above the text) to the My cards scrim colour (`PHOTO_LABEL_SCRIM`, black at 60%, [[card-list]]) at the
  tile's edge. Glance has no gradient brush, so the scrim is a drawable, `res/drawable/widget_label_scrim.xml`, the label
  box's background image. The issue asked for the label straight on the brand colour with no band. The owner checked
  it on an Android 15 phone and an Android 14 handheld on 2026-11-24 and changed it twice: on the brand colour the label
  was hard to read over a logo; a solid band with a fade above it, as on My cards, covered too much of the logo; a fade
  that turned dark at the label's middle was tried and dropped for the plain clear-to-dark one. A store with no logo,
  or one whose logo is missing or can't be decoded, shows the initials, with the label on the brand colour as before.
  **A tile whose card shows its front photo** (#185) and whose photo decoded shows the photo instead, filling the tile
  (`ContentScale.Crop`, no margin, `WIDGET_TILE_PHOTO_TAG`), with the label on the same scrim as a logo tile
  (`LabelOnScrim`). The issue said "the photo scrim as on My cards"; the owner chose the widget's fade over My cards'
  solid band while planning (2026-11-26), as for the logos. A photo that is missing or can't be decoded falls back to the
  logo, else the initials. From Android 12 the widget
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
  computes the columns from `LocalSize`, and 2dp of padding around the grid and around each tile
  (`WIDGET_HALF_SPACING`) makes the 4dp edge and the 4dp gap between tiles (`WIDGET_SPACING`, #182; both were 8dp).
  **A tile's height comes from its real width (#216):** each tile is `res/layout/widget_tile.xml`, a `RelativeLayout`
  embedded with Glance's `AndroidRemoteViews`. Its sizer, an `ImageView` as wide as the cell with `adjustViewBounds`,
  shows a transparent shape, square or at the card's ratio (`res/drawable/widget_tile_square.xml`, 856 × 856dp, or
  `widget_tile_card.xml`, 856 × 539.8dp; larger than any tile so it is only scaled down), so it is as tall as that
  shape at the cell's real width. The layout's width picks the shape (`widgetTileShape`). The face, which Glance
  fills (`containerViewId`), is aligned to the sizer's top and bottom, so a logo can't make the tile taller. Until #216
  the height was worked out for the layout's width (`widgetTileSize`), so a layout shown in a narrower widget gave tiles
  taller than a card, and taller than wide when much narrower ([[2026-11-25-widget-tile-height-from-its-real-width]]).
  The widget is laid out for 4 sizes (`SizeMode.Responsive(WIDGET_SIZES)`), each 40dp high, at the switch points: one
  column of square tiles from 110dp, of card tiles from 128dp, two columns of square tiles from 240dp, of card tiles from
  252dp. From Android 12 the home screen picks the widest that fits the widget's real width, and the layout's width
  chooses the columns and the shape. Until #216 there were 16 sizes, 110 to 410dp, stepped so that a height worked out
  for the layout's width stayed within 10% of the card's ratio. A tile is square below 120dp only while the launcher
  picks the layout for the widget's real width: shown a layout made for another width, a tile takes that layout's shape,
  at its own real width, so it is never taller than wide (a 126dp widget shown the 128dp layout has a low 118dp card
  tile). It was `SizeMode.Exact`, which lays out the sizes the launcher reports: on a device
  (2026-11-12, Nova launcher) it reported 131×432dp and a landscape 263dp-wide size for a widget about 131×386dp, none
  fit, Android showed the smallest, the landscape layout, and its fixed-width tile was cut to half.
  A launcher can still show a layout made for another width: the Android 12L launcher on a tablet kept the 410dp
  layout after the widget was resized to ~215dp (2026-11-12). Tiles fill their cells and keep the card's ratio, so
  nothing is cut or stretched, but such a widget can show two columns where one fits, or the other shape. A card-ratio Glance `Image` that
  fills the cell was tried for the height before #216 and failed: Glance's `Image` doesn't set `adjustViewBounds`, so it
  kept its intrinsic height. Each
  tile's `clickable` is `actionStartActivity(openCardIntent(…))`. At its narrowest (110dp) the widget can't be resized
  below one square tile's height: `minResizeHeight` is 110dp, a 102dp tile and its 4dp edges (`res/xml/card_widget_info.xml`,
  #216; 90dp for a card tile since #108, 40dp before, a thin strip). A wider one-column widget has taller tiles, so
  resized to 110dp it cuts its tile (a 127dp one has a 119dp square tile and needs 127dp); the launcher has no per-width
  minimum. A launcher makes a widget a whole number of its own cells, so `minResizeWidth` can't make it narrower than
  one cell: on the Android 14 handheld one cell is 160dp, so its tiles are never square there.
  Glance's unit test can't see a node's size or the grid's columns; `widget/CardWidgetRenderTest` measures them on the
  real RemoteViews (#216, Android 12+ only), and a device check on 2026-11-12 saw one column at ~131dp and two at
  ~278dp, tiles uncut. **Checked on devices on 2026-11-25 (#216)**, tiles measured from `uiautomator dump`: Nova on an
  Android 15 phone at 82dp wide (Nova allows less than `minResizeWidth`; Android showed the 110dp layout) drew square
  74.9dp tiles, at 131 and 180dp one column of card tiles, at 278dp two columns of card tiles; the Android 14 handheld's
  launcher at its narrowest, 160dp, card tiles; an Android 11 emulator (Launcher3, the pre-Android-12 grid through a
  `RemoteViewsService`) at its narrowest, 130dp, a card tile. No tile was taller than wide, and on the Nova phone a tapped tile still opened its card (the click now sits in
  the tile layout's face). Not reached on a device: two
  columns of square tiles (240–252dp) and square tiles before Android 12; the render tests cover the first. Nor is the widget checked at a large font
  scale: a small tile's two lines can be cut there.

- `widget/WidgetPictures.kt` — the logos (#181) and the shown front photos (#185) as bitmaps, by file name (named
  `WidgetLogos` until #185). `WidgetPictures.forTiles(tiles)` takes the one picture each tile draws: its photo, else
  its store's logo. The logo of a tile showing its photo is not read, so it takes no budget (review of PR #241: it
  first took budget from the photos without being drawn); a photo whose header reads but whose pixels don't is found
  when decoded, remembered as unreadable, and its tile's logo is read instead. It reads each picture's size from its header. A **photo** is
  cut around its middle to the **photo box**'s shape and scaled down, never up, to it: the whole 216dp card tile below,
  216 × 136.2dp (`widgetPhotoBox`, `widgetPhotoBoxPx`, 594 × 374px at 2.75), so no photo bitmap is larger than its tile
  (the binder limit below). A photo kept whole ("use whole photo", [[card-photos]]) can have any shape, so fitting it
  whole could have made a tall photo twice the tile's pixels; the tile crops it anyway, and a square tile crops the
  cut's sides. A **logo** is fitted, scaled down and never up, to the **logo box**: a 216dp card tile, the widest of the layouts
  before #216 (216 × 136.2dp, at 224dp wide), less the margin, 200 × 120.2dp (`widgetLogoBox`), in pixels at the screen's density
  (`widgetLogoBoxPx`). When the pictures shown together, logos and photos, would take more than
  `WIDGET_LOGO_BUDGET_BYTES` (4 MB, ARGB), all are scaled down alike to fit it (`widgetLogoScale`). A full photo is
  about 0.89 MB at 2.75 density and a full logo up to about 0.73 MB, so from about five photo tiles (fewer alongside
  logo tiles) the pictures come out softer; the owner kept the
  shared 4 MB for safety over sharpness while planning #185 (2026-11-26). Each picture is then decoded straight to that
  size (`inSampleSize`, then cut and scaled), from a stream it closes (#88). One bitmap serves every layout size. It keeps only the bitmaps it last
  handed out, so it never holds more than the budget, and decodes a logo again only when its size changes (another
  logo came or went past the budget); a logo's name is never reused, so a kept bitmap is never stale. A missing file,
  a file that isn't a picture, or a name that isn't a photo's is left out, so its tiles show the initials; a logo no
  longer shown is forgotten. It is an app `@Singleton` (`widget/glance/WidgetPicturesModule.kt`), so a redraw doesn't
  decode again (review of PR #205: it first kept every full-size decode for the process's life and rescaled on each
  draw). Why a budget: Android refuses a widget update whose bitmaps exceed 1.5 × the screen's pixels × 4 bytes
  (about 15 MB on a 1080 × 2400 phone), and the platform's RemoteViews keeps one copy per `Bitmap` object for all the
  responsive sizes and collection items, which is why one object per logo is reused (read in the platform and
  glance-appwidget 1.2.0 sources, 2026-11-24; not measured). **Checked on devices on 2026-11-24:** the owner
  looked at logo tiles on an Android 15 phone and an Android 14 handheld (the label's scrim above came from that).
  **Not checked on a device yet:** photo tiles (#185): how a photo looks cropped on a square tile, and how soft many
  photos look under the shared budget; the tile height with a logo measured against one without (a `Fit` image kept its
  intrinsic height in an earlier attempt, below), TalkBack's reading, and a widget with many stores' logos.
  **A known limit:** a photo and a logo are decoded for a 216dp tile, but a tile fills its cell since #216, so in a
  two-column widget wider than about 445dp (a tablet) a tile is wider and its picture is drawn larger than decoded,
  softer (review of PR #241).

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
  `addOnNewIntentListener`. `MainActivity` is the only activity in its task (shares run in `ShareActivity` tasks of
  their own, which the tap never reaches, #189), so `SINGLE_TOP` makes a warm tap reach the running
  instance's `onNewIntent` rather than stack a second one. `AppNavigation` collects the channel and calls
  `NavController.openCard(cardId, color)` (`Navigation.kt`), which opens `Detail(cardId, color)` **over the list, in
  place of whatever was open above it** (`popUpTo<CardList>()`), so Back returns to the list (#213). A tap for the card
  already on top does nothing: no second entry, no reload, and no use counted; any other tap counts a use at a till, as
  tapping the card in the list does (#4). The tap opens in the running task; unlike a share it doesn't forward to a
  LoyScan of its own.
- **Until #213** the card was pushed over whatever was open (`launchSingleTop`), so a half-filled form survived a tap
  (#11, #83), but another card's screen kept showing that card ([[2026-11-24-widget-tap-reused-the-open-card]]).

**On-device check (#102), before #213 (ASUS AI2302, Android 15, 2026-11-05).** With a half-filled add form open, a warm
tap opened that tile's card in the same task (`dumpsys`: one task, no second LoyScan) and Back returned to the form; a
repeat tap on another tile opened that card in the same task. Each tile opened its own card, confirming the
intent-extras match the Glance unit test can't assert. Since #213 Back from it goes to the list.

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
  the app's resources and checks `minResizeHeight` fits one tile at `minResizeWidth` (#108; 110dp for a square tile
  since #216). `WidgetLayoutTest` also checks the shape (square below 120dp of tile width, in one and two columns) and
  that, for every width from 110 to 480dp, the layout a launcher picks for it has that width's columns and shape (#216).
  `widget/glance/CardWidgetTest` pins `sizeMode` to `SizeMode.Responsive(WIDGET_SIZES)`, which the render tests assume. Since #181 also the logo
  box, 200 × 120.21dp, and its pixels at a density (550 × 330 at 2.75).
- `widget/WidgetPicturesTest` — real PNGs in a `TemporaryFolder`, `@GraphicsMode(NATIVE)` (#181). Photos (#185): a
  large card-shaped one scaled to the photo box; a tall and a wide one cut around their middle (a banded PNG whose
  corners come back in the middle band's colour) and scaled to the box; a small one cut but not scaled up; a portrait
  photo at 2.75 density no larger than its 594 × 374px tile; a missing file, a non-picture, a PNG cut after its header
  and an invalid name left out while the tile's logo still comes; a photo and a logo over the budget scaled alike to
  141 × 70 (√½ of 200 × 100, worked out by hand); the logo of a tile showing its photo not decoded and taking no budget,
  and read when the tile's photo is cut after its header (review of PR #241); a photo no longer shown forgotten. Logos: a wide and a square
  logo scaled down to fit the box, a small one not scaled up, a missing file, a non-picture and an invalid name left
  out without throwing, one bitmap for all of a store's cards and for later calls (the file deleted between them, so the
  stream was closed), a logo no longer shown forgotten, and three logos over the budget scaled to 163 × 163 (√(2/3) of
  200, worked out by hand), the same bitmaps on the next call, while two within it keep their size; by default two
  1024 × 1024 logos (8 MiB) come back at 724 × 724, within 4 MiB.
- `widget/CardWidgetTilesTest` — the sort order, the label (none without one, never the store name), the store name and
  logo, the front photo only while the card shows it on the widget (not with My cards' switch alone, not a back photo, #185), the initials and the colour helpers (Robolectric, because
  the colour parse goes through `android.graphics.Color`).
- `widget/CardWidgetContentTest` — `runGlanceAppWidgetUnitTest` (Glance's JVM unit test, `glance-appwidget-testing`):
  one clickable node per card, each showing its initials and label (an unlabelled one only its initials, never the store
  name), described for TalkBack as "store, label" (`hasContentDescriptionEqualTo`, #181), at a set widget size
  (`setAppWidgetSize`). Since
  #182 it also reads a node's Glance modifiers (`MappedNode.emittable.modifier`): the grid and each tile's cell have 2dp
  of padding; at API 32 the background has the 12dp corner radius and each tile 8dp, and at API 29 neither is rounded. Glance's `CornerRadiusModifier` is internal to Kotlin,
  so the test reads its radius by reflection. That the corners look concentric on a home screen is checked by hand. A new test seam — see [[tdd]] and [[architecture]]. The Glance
  unit API matches a click by component + `ActionParameters`, not by the intent extras the tile uses, so that a tile
  opens **its own** card is an on-device check, not a unit assertion. Since #181: a tile with a logo shows that bitmap
  (`EmittableImage`, `BitmapImageProvider`), fitted, with 8dp of padding, on its brand colour (`BackgroundModifier.Color`,
  read through `ColorProvider.getColor`); no initials and no store name; its label white on a dark and a light
  brand colour alike, in a box whose only background is the scrim drawable, aligned to the bottom; the drawable runs
  top to bottom from clear to `PHOTO_LABEL_SCRIM`; a labelled tile without a logo has no scrim; a tile
  whose logo didn't decode shows the initials. Since #185: a tile with a photo shows that bitmap cropped
  (`ContentScale.Crop`), filling the tile with no padding, and no logo, initials or store name; its label white on the
  scrim on a dark and a light brand colour; a tile without a photo, or whose photo didn't decode, shows its logo, else its
  initials.
- `widget/CardWidgetRenderTest` — the tiles as a home screen lays them out (#216): the real RemoteViews for a layout
  width, inflated at a real width that may differ from it (`widget/WidgetRender.kt`, [[tdd]]), each tile's size read from
  its view. A layout shown at its own width has card tiles filling their cells, in one and two columns; one shown in a
  narrower widget fills the real width and keeps the card's ratio there; every layout at every width from 110 to 480dp
  (every 5dp) draws no tile taller than wide. Since the square tiles: the layout picked for each width from 110 to 480dp
  draws square tiles below 120dp and card tiles from 120dp, at 1x and again at a phone's 2.75 density (a tile's padding
  is whole pixels there, so sizes are checked within a few pixels); the 110dp widget at its 110dp minimum height shows a
  whole square tile, 4dp from the bottom. A layout shown at another width keeps its shape at the real width: the 128dp
  layout in a 115dp widget has card tiles, the 110dp layout in a 200dp widget square ones. Since #185 one of the four
  tiles is a photo tile with a tall 100 × 400px bitmap, and every check above holds for it too: a photo doesn't make
  its tile taller. Each test draws what it measured into `app/build/outputs/widget-renders/`.
- `widget/CardWidgetUpdaterTest` — the live-update trigger: it fires when a card or the sort changes the tiles, not on
  a change a tile doesn't show, never when no widget is placed, starts once a widget is placed mid-session, and a
  refresh that throws is swallowed so observation continues. Since #181 it fires when a store's logo is set, changed or
  removed; `RoomLoyaltyCardRepositoryTest.getAllWithStore_emitsAgain_whenItsStoresLogoIsSetOrRemoved` shows the real
  card flow emits then (the `@Transaction` query observes the stores table). Since #185 it fires when "Show photo on
  widget" is turned on or off on a card with a front photo (`refresh_runsWhenACardsShowPhotoOnWidgetIsToggled`), and
  not when it changes on a card without one or only My cards' switch changes.
- `OpenCardIntentTest` — the intent round-trips, and is `null` for a bare intent or one from recents.
- `WidgetTapOpensCardTest` — end to end: `MainActivity` launched **cold** by `openCardIntent` opens that card's
  detail (the dead-process case), a **warm** `onNewIntent` opens it while the app runs, and one for card B over card
  A's screen shows B (#213). Mirrors `ShareToLoyScanTest`.
- `WidgetOpenCardDirectionsTest` (`TestNavHostController`) — `openCard` opens the card over the list from the list, a
  `CardForm` or another card's screen, in a new entry, and Back lands on the list; for the card on top it keeps the
  same entry and back stack (#213).

The `com.bdaniel.loyscan.widget.glance` package (`CardWidget`, `CardWidgetReceiver`, `GlanceCardWidgetRefresher`,
`WidgetPicturesModule`) is
Glance/AppWidget glue that runs only on a real home screen — the `provideGlance` wiring, the system receiver and the
`updateAll`/`getGlanceIds` calls — so the package is excluded from the coverage gate, like the theme and the icons;
the logic it drives is tested above ([[2026-10-09-coverage-gate-kover]]).

## Not done yet (this slice)

- No empty-state message: a widget with no cards shows nothing.
- The live-update trigger only observes while the app process is alive. Cards change only through the running app,
  so that covers every real change; a widget added while the app is dead renders current cards when it is next shown.
- The on-device checks above (live refresh, warm/repeat tap, a tile opening its own card) were the open part of
  #11 after this slice; they passed on a device on 2026-11-05 (#102), and edit and re-sort on 2026-11-12.

See [[2026-11-04-home-screen-widget]] for the decisions.
