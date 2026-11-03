# Card photos

A card can carry photos of the physical card, front and back (#10). They are copied into app-private storage and
deleted with the card. Storage and deletion: [[2026-11-03-card-photos-as-private-files]].

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `data/photos/CardPhotos.kt`: `CardPhotos` (`import(uri)`, `delete(names)`), implemented by `FileCardPhotos` over
  `files/card_photos/` (`di/CardPhotosModule.kt`).
- `data/repository/RoomLoyaltyCardRepository.kt` (`update`, `delete`, `deleteAll`) and
  `data/repository/RoomStoreRepository.kt` (`delete`): delete the files whose names the row write dropped.

## Behaviour

- **Files are deleted on every path:**
  - replacing or removing a photo in an edit ([[edit-card]]);
  - deleting a card from its screen ([[card-detail]]);
  - deleting cards by multi-select ([[card-list]]);
  - deleting a custom store with its cards ([[edit-store]]).

  A catalogue store can't be deleted, so its cards' photos stay.

## Tests

- `data/photos/FileCardPhotosTest`: a picked picture is copied under a new name, an unreadable one leaves no file,
  delete skips missing names, a name can't leave the directory.
- `RoomLoyaltyCardRepositoryTest`: `update_deletesAReplacedOrRemovedPhoto_andKeepsTheOthers`,
  `update_keepingThePhotos_deletesNone`, `delete_deletesTheCardsPhotos`, `deleteAll_deletesThoseCardsPhotos_only`.
  `RoomStoreRepositoryTest.delete_deletesThePhotosOfTheStoresCards`, `delete_ofACatalogueStore_keepsItsCardsPhotos`.
  `LoyaltyCardDaoTest.updateReturningDroppedPhotos_…`, `deleteReturningPhotos_…`.

## Not implemented

Taking a photo with the camera, cropping to the card's ratio, the front photo as the list tile, and backup (#71) are
later parts of #10.
