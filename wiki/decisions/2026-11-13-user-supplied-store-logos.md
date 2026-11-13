# User-supplied store logos, kept with the card photos

**Status:** Active · amends [[2026-11-12-no-bundled-store-logos]] · **Date:** 2026-11-13 ·
**Issue:** [#151](https://github.com/shockrifle/loyscan/issues/151)

The app still ships no logos, but the user can give any store, catalogue or custom, a logo of their own. It is a
file in the card photos' private directory, named by the store's existing `logo` column, and every card of the store
shows it. The catalogue sync never touches that column.

## Context
- [[2026-11-12-no-bundled-store-logos]] ruled out bundled or fetched logos and named a logo the user makes from
  their own pictures as the way to have logos at all (#151).
- `stores.logo` (`StoreEntity`, `Store`) already existed as a URL column for the removed demo stores, and was always
  `null` (`CatalogueMapper` writes `null`; custom stores are created without one). The tile and the detail header
  already drew it when it was set ([[coil-image-loading]]).
- `CatalogueDatabaseCallback` rewrote every column of a catalogue row that differed from the shipped entry, `logo`
  included, so a user's logo on a catalogue store would have been wiped on the next open.

## Options considered
- **A new table or column for the user's logo:** a Room schema change and a migration, though the schemas are frozen
  from v2 ([[2026-11-05-room-schema-frozen-from-v2]]), for a value the existing, always-empty column can hold.
- **A separate `files/store_logos/` directory:** a second file store, image-loader mapper and orphan sweep, and
  its own backup reasoning.
- **The existing `logo` column, holding a file name in `files/card_photos/` (chosen).**

## Decision
- `Store.logo` is the name of the user's logo file in the card photos' directory, never a URL; `null` is the name on
  the brand colour. No schema change.
- The catalogue sync leaves `logo` alone: it is not compared and not written on an update; a new catalogue row is
  inserted with `null` (`CatalogueDatabaseCallback`, `USER_COLUMN`).
- A logo is stored through `CardPhotos`: staged like a photo, then `cropLogo` encodes it (`encodeStoreLogo`): a PNG
  that keeps transparency, at most `MAX_LOGO_SIDE` (512 px) on its long side, upright, without metadata. Its crop may
  reach past the picture, which is left transparent there; a card photo's crop may not.
- `StoreRepository.setLogo(storeId, logo)` sets or removes it on any store and deletes the file it replaces; a store
  that is gone gets nothing, and the new file is deleted. Deleting a custom store deletes its logo.
- The orphan sweep keeps a file a store names as its logo ([[card-photos]] → Orphans).
- **The logo screen** ([[store-logo]]): a square crop frame that can zoom out until the picture fits it whole, so a
  wide wordmark is kept (the user's choice on 2026-11-13, over a crop that only zooms in, where "Use whole image" would
  have been the only way). "Find a logo online" opens a Google Images search for "<name> logo" in the browser; the app
  stores no URL. The user chose Google as the default on 2026-11-13; a setting to pick the engine is not implemented.
- #151 is done in slices: the custom store form's slot, a crop of a card's front photo and an image shared to the app
  are not implemented yet.

## Why
- Reusing the photos' directory gives a logo the same private storage, backup handling ([[2026-11-05-no-cloud-backup-in-v1]]:
  no cloud backup, device-to-device transfer carries it), image-loader mapping (`CardPhoto`) and orphan sweep as a
  card photo, with no new code for any of them. *(Inference from the code; the issue asked that backup handle a logo
  "as it handles card photos".)*
- PNG, not JPEG: a logo is drawn on the brand colour, and a JPEG would put a transparent logo on a white box. 512 px
  is more than a tile or the detail header draws it at.
- The user chose PNG with transparency on 2026-11-13.
- Square, because a logo is mostly a mark or a badge, drawn fitted in a card-shaped tile; zooming out covers the wide
  ones. A browser search keeps the app out of fetching or linking third-party marks
  ([[2026-11-12-no-bundled-store-logos]]).

## Consequences
- The name in `logo` is a file name like a photo's, so `CardPhotos.exists` and the loader's `CardPhotoMapper` work for
  it unchanged. The tile and the detail header show it as a `CardPhoto`; one that can't be loaded or whose file is
  missing falls back to the initials or the name ([[card-list]], [[card-detail]]).
- The Coil SVG decoder and the `INTERNET` permission no longer serve any store field ([[coil-image-loading]]).
