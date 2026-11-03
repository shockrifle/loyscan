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
- A picked picture is copied in on pick (`CardPhotos.import`). A copy that fails or is cancelled leaves no file.
- **It is stored re-encoded, not byte for byte** (#88, `data/photos/CardPhotoEncoding.kt`, `encodeCardPhoto`): a JPEG
  (quality 90) at most `MAX_PHOTO_SIDE` (2048 px) on its long side, never enlarged, turned the way its EXIF orientation
  says, a transparent picture on white. A fresh encode carries no metadata, so the location and the rest of the EXIF
  stay behind. The picked picture is first copied as it is to `<name>.original` next to the photo, and decoded from
  there, so a cloud picture is downloaded once rather than once per pass (bounds, orientation, pixels); that copy is
  deleted after, and one a killed process leaves is an orphan for the sweep below. A file that can't be decoded throws,
  so the form says the photo couldn't be added. (Changed 2026-11-03 for #88: this was a byte-for-byte copy.)
- **A photo taken with the camera** (added 2026-11-03, the camera slice of #10) is written by the camera app into a
  capture file in the same directory, `<uuid>.jpg` (`CardPhotos.newCapture`), handed to it as a
  `content://<applicationId>.photos/…` Uri by `androidx.core`'s `FileProvider`, whose paths (`res/xml/card_photo_paths.xml`)
  cover only `card_photos/`. The capture is then imported like a pick and deleted. In the photo directory rather than
  `cache/`: the sweep below already deletes an old file no card names, so a capture a killed process leaves goes
  with no second sweep, and it stays out of cloud backup with the photos *(the cache dir is never backed up either;
  the sweep is the reason, inferred)*.
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
- Re-encoding on import (#88): a card photo needs neither a 50 MP original nor where it was taken, and a smaller file
  weighs less on device-to-device transfer and later on the opt-in backup's quota (#24). 2048 px keeps a card's small
  print legible full screen (the issue's suggestion). Applying the orientation to the pixels means no viewer depends on
  EXIF, which the JPEG no longer has. JPEG for every picture, PNG screenshots included: one format, and a photo-sized
  PNG would be several times larger. `BitmapFactory`, not `ImageDecoder`, so the encode is tested on real pixels
  (Robolectric's native graphics decodes with `BitmapFactory` on Windows, but not with `ImageDecoder`, [[tdd]]).
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
- Until #88 copied pictures were full size and kept their metadata (EXIF location included); now they are at most
  2048 px and carry none. They stay private, and they are **excluded from cloud backup** (`data_extraction_rules.xml`, `backup_rules.xml`): an Android auto-backup would
  otherwise send pictures of the user's cards to their Google account unasked (cloud backup becomes opt-in with #24),
  and they would pass its 25 MB quota, which stops the app's backup altogether, database included. Even at about 1 MB
  each since #88, two photos a card pass it at a dozen cards. (Updated 2026-11-03 in the review of #88: this said the
  backup would send their location and that a few full-size photos would pass the quota.) Device-to-device transfer takes them on Android
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
