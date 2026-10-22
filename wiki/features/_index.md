# Features

One page per non-trivial user-visible feature or flow. Lead with what the user experiences, then entry points
(`path:line`), behavior, what's not implemented, and known issues (links to tech-debt/hacks/bugs).

- [[card-list]] — "My Cards" grid, the start destination
- [[card-detail]] — single card view
- [[card-transition]] — list ↔ detail shared-element animation
- [[add-card]] — adding a card: store picker, scanner, form with live check, save (#6, in progress)
- [[edit-card]] — editing a card's label and number in the same form (#8, in progress)
- [[barcode-scanner]] — camera scanner (CameraX + zxing-cpp), permission flow, torch, hint
- [[store-catalogue]] — the shipped store list, synced into the database on every open
- [[seed-data]] — demo cards, debug builds only
