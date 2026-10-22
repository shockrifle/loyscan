# Add card

The list's "Add card" button starts adding a card: pick the store, then scan its barcode. Issue
[#6](https://github.com/shockrifle/loyscan/issues/6); this page describes the slice that exists. Everything runs offline
on the local database.

**Entry points** (under `app/src/main/java/com/bdaniel/loyscan/`):
- `StorePickerScreen.kt`, `StorePickerViewModel.kt`; search in `domain/StoreSearch.kt`.
- Routes `StorePicker` and `Scanner(storeId)`, with `StorePickerDirections` / `storePickerDirections()`, in
  `Navigation.kt`; `ListDirections.toAddCard` opens the picker.

## Behaviour

- **Store picker.** Every store in the database (catalogue, custom and, in debug builds, demo stores), sorted by
  name. The search field narrows it to stores whose name or an alias contains the text, ignoring case and accents
  (`StoreSearch.search`: NFD, combining marks removed), so "mobelix" finds "Möbelix". Sorting uses the same key. With
  no match the screen says "No store matches “…”". Tapping a store opens the [[barcode-scanner]] for it; back returns
  to the list.
- **After the scanner**, until the form exists, a scan is only shown: [[scanner-result-dialog-until-add-card-flow]].

## Tests

- `domain/StoreSearchTest`: matching (name, alias, case, accents) and order.
- `StorePickerViewModelTest`, `StorePickerScreenTest`.
- `AddCardDirectionsTest`: where each step's directions lead.
- `AppNavigationTest.addCard_opensTheStorePicker_pickingAStoreOpensTheScanner_andBackReturnsToThePicker`.

## Not implemented

From #6: "frequently added" stores for the user's country, custom stores, suggesting another store when a scan matches
it better, and "add another".
