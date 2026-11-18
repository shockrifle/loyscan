# Launcher icon: fanned cards with a heart-shaped barcode, on plum

**Status:** Active · **Date:** 2026-11-18 · **Commits:** see #75's PR

## Context

The app shipped Android Studio's template icon (the Android robot on green). Play needs the app's own icon before the
first upload (#75). The icon has to say "loyalty cards" (not "barcode scanner"), stay apart from the icons of other
loyalty-card apps, and work as an adaptive icon in any mask shape and as a themed (monochrome) icon on Android 13+.

## Options considered

Drawn as vectors and compared rendered in a circle, a rounded square, themed, and at launcher size, in five rounds:

- **A card with a barcode and a scan line** (the first draft): read as a generic barcode scanner, not as loyalty cards.
- **A stamp card** (a row of stamps over a barcode): close to StampWallet's icon.
- **A card with a gold star badge**, and **fanned cards with a star and a barcode**: the second is close to StoreCard's
  (a white card, a barcode, a gold star, a card fanned behind) and CardStar's.
- **A wallet with coloured cards rising out of it**: the closest to Google Wallet's icon (1B+ installs) and ONEWallet's.
- **A heart made of barcode bars**, and variations (an outline with a small barcode, a barcode with a heart cut out, a
  heart half printed as a barcode): distinctive; "Heart QR" already has a QR code shaped as a heart.
- **Fanned cards whose front card's barcode is a heart** (chosen), on raspberry or plum.

The comparison set: the icons of the top Play search results for "loyalty card wallet", "loyalty cards", "store card
barcode wallet", "membership card app" (45 apps), then for "heart barcode", "heart loyalty card", "rewards card heart",
"loyalty points app", "digital loyalty card", "card wallet heart" (109 more), fetched 2026-11-18. Stocard is no longer
on Play (it was folded into Klarna).

## Decision

Two fanned landscape cards, the front one white with a barcode clipped to a heart, the back one pale lilac (`#E1BEE7`),
on plum (`#5B2A4E`); the bars are a dark plum (`#4A1942`). The themed layer is its own drawable: the back card is cut
back by a gap around the front card and the barcode is cut out of the front card, since a themed icon is tinted by
alpha alone. Layers: `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`.

## Why

- **Loyalty, not scanning.** Cards say "cards you carry"; a heart says "loyalty"; the barcode says what the app does
  with them. The first draft (one card, a scan line) read as a scanner app.
- **Apart from the category.** Card + barcode, a wallet with cards, and fanned cards are the category's clichés, and
  stars and hearts are its usual loyalty signs. No icon among the 154 compared combines fanned cards with a
  heart-shaped barcode.
- **Plum, not raspberry.** Raspberry looked brighter, but fanned cards with a red heart are what the many "Hearts"
  card-game icons look like (playing cards and a red heart, on red or green). A dark plum heart on plum reads as a
  barcode, and the landscape cards don't look like playing cards. No loyalty-card app compared uses plum; purple,
  orange, bright blue and mint are each used several times.
- **Works at every size and as a themed icon.** The heart and the card outline survive at launcher size; the themed
  layer keeps both cards and the heart apart with gaps instead of colours.

## Consequences

- `LauncherIconTest` draws the layers and checks their pixels: both icons are adaptive with their own monochrome
  layer, nothing leaves the 66dp safe zone, and the barcode is heart-shaped (wider across the lobes than near the
  point, and reaching down to it) in both the coloured and the themed layer ([[tdd]]).
- The icon is vectors only; the template's raster `mipmap-*dpi` files are gone (minSdk 29 always uses
  `mipmap-anydpi`).
- Play's 512×512 store icon is `play/store-icon.png`, rendered from these layers (the visible 72dp centre of the
  background and foreground); `StoreIconTest` fails when it drifts from them ([[google-play]] → Store listing assets).
- The app's theme still uses the template purple; the icon's plum is not (yet) a theme colour.
