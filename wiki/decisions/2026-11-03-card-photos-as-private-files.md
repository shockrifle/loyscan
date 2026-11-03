# Card photos are private files named in the card's row, deleted by the repositories

**Status:** Active · **Date:** 2026-11-03 · **Issue:** [#10](https://github.com/shockrifle/loyscan/issues/10)

## Context
#10 lets a card carry a photo of the physical card, front and back. The `loyalty_cards` row has had
`frontPhotoPath` / `backPhotoPath` since schema v2 ([[2026-10-12-data-model-v2]]), unused until now. The issue requires
the photos to stay out of shared folders, and leave no files behind when a photo is replaced or a card is deleted.
A card can be deleted three ways: from its screen ([[card-detail]]), by the list's multi-select ([[card-list]]), and
by deleting its custom store, where `ON DELETE CASCADE` deletes the rows in SQLite and no Kotlin code sees them ([[edit-store]]).

## Options considered
- **Where:** app-private `files/` (no permission, no other app can read it, carried by device-to-device transfer) or
  shared storage / MediaStore (visible in the gallery, needs permissions, outlives an uninstall). Private.
- **What the row holds:** an absolute path, or a name relative to the photo directory. An absolute path breaks when the
  data directory differs, e.g. a restore into a work profile (`/data/user/10/…`).
- **Who deletes the files:** each ViewModel after its delete, a sweep of unreferenced files, or the repositories.
  ViewModels would miss the cascade, which only SQL sees. A sweep alone needs to know which files belong to forms still
  open, possibly in another task since a share opens its own ([[2026-11-03-share-in-standard-launch-mode]]).
- **A form's copies when its process is killed** (swiped from Recents, a low-memory kill never returned to): no
  `onCleared` runs. Added in review, 2026-11-03. Options: stage picks in a separate directory and move them on
  save (suggested in review), or sweep files no card names once they are old enough that no open form still holds them.
- **When a picked picture is copied:** on pick, or on save. The photo picker's read grant lasts only while the app's
  process lives, so a save after process death couldn't read a picture that was only remembered by its Uri.

## Decision
- Photos are files in `files/card_photos/` (`CardPhotosModule.PHOTO_DIRECTORY`). The row stores the file's **name** (a
  UUID). `FileCardPhotos.file(name)` refuses anything that isn't a bare file name.
- A picked picture is copied in on pick (`CardPhotos.import`), byte for byte. A copy that fails or is cancelled
  leaves no file.
- **The repositories delete the files**, after the row is written:
  `LoyaltyCardDao.deleteReturningPhotos(ids)` / `updateReturningDroppedPhotos(edit)` and
  `StoreDao.deleteCustomReturningCardPhotos(id)` read the photo names and write the row in one `@Transaction`, and
  `RoomLoyaltyCardRepository` / `RoomStoreRepository` then delete the files those calls return. `delete(id)` is
  `deleteAll(setOf(id))`, so a card has one delete path.
- **A sweep catches what the rest misses:** `CardPhotoSweepCallback` runs on every database open and deletes photo
  files no card names that were last written over a day ago. A save drops a picked name whose file is gone
  (`CardPhotos.exists`), so a form restored after more than a day doesn't name a swept file.
- An edit (`LoyaltyCardEdit`) now writes the photo columns. Before #10 it left them alone ([[edit-card]]).

## Why
- One choke point covers all three delete paths, including the cascade, and a test per path sits at the repository
  (`RoomLoyaltyCardRepositoryTest.delete_…`, `deleteAll_…`, `RoomStoreRepositoryTest.delete_…`).
- The sweep rather than staging: the age alone tells an abandoned copy from one an open form holds, so a pick needs no
  second location or a move on save that could fail halfway (row written, file not moved). The day is a guess at how
  long a form waits in Recents; a form older than that loses its unsaved photos. On that side the save keeps the card's own photo, so the
  edit doesn't delete it (`anEditWhosePickedPhotoIsGone_keepsTheCardsOwnPhoto`, found in the second review).
- Files go after the row: if the write fails, the card still has its photos. If a file delete fails, the file is left
  behind (logged), which is better than a card naming a missing file.

## Consequences
- An edit saved from a form that loaded the card before another form changed its photos writes the stale names
  back. Two forms editing one card at once would need two tasks. Not handled.
- Copied pictures are full size and keep their metadata (EXIF location included). They stay private, and they
  are **excluded from cloud backup** (`data_extraction_rules.xml`, `backup_rules.xml`): an Android auto-backup would
  otherwise send them, location and all, to the user's Google account unasked, and a few photos would pass its 25 MB
  quota, which stops the app's backup altogether, database included. Device-to-device transfer takes them on Android
  12+. On 10–11 the bare exclude leaves them behind for now: `requireFlags="deviceToDeviceTransfer"` includes would
  carry them, and they are #71's, with the rest of the rules (corrected in the second review: this said the platform
  forces it). (Corrected 2026-11-03 in review: this said only that
  they count toward device-to-device transfer, while cloud backup was still on with the template rules.)
- A photo picked in a form that is then closed is the form's to delete ([[card-photos]]); the sweep catches the
  rest on the next start.
- Cloud backup still holds `loyscan.db` (#71), but no longer the photos. So a cloud restore brings back rows that name
  photo files it didn't restore. Since #87 such a photo is shown as none, on the card screen and in the form, and a
  saved edit clears its name ([[card-photos]]). (Updated 2026-11-03: this said nothing clears the name and the card
  shows an empty photo.)
