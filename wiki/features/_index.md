# Features

One page per non-trivial user-visible feature or flow. Lead with what the user experiences, then entry points
(path and symbol), behavior, what's not implemented, and known issues (links to tech-debt/hacks/bugs).

- [[card-list]] — "My Cards" grid, the start destination
- [[card-detail]] — single card view
- [[card-transition]] — list ↔ detail shared-element animation
- [[add-card]] — adding a card: store picker, scanner, form with live check, save (#6)
- [[edit-card]] — editing a card's label, number and store in the same form (#8)
- [[card-photos]] — photos of the physical card, front and back, kept in private storage (#10)
- [[edit-store]] — editing or deleting a custom store from the picker (#53)
- [[store-logo]] — the user's own logo for a store, shown on its cards; a catalogue store's logo screen from the picker (#151)
- [[about]] — the About screen: name, version, and the note that LoyScan isn't affiliated with the retailers (#150)
- [[barcode-scanner]] — camera scanner (CameraX + zxing-cpp), permission flow, torch, hint
- [[store-catalogue]] — the shipped store list, synced into the database on every open
- [[seed-data]] — the former debug demo cards (removed, #55)
