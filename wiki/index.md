# LoyScan wiki

LoyScan is a single-module Android app (Kotlin, Jetpack Compose, Hilt, Room) for keeping loyalty cards.
Today it shows a grid of cards from a local Room database (demo cards in debug builds; a curated store catalogue), and a detail screen
reached through a shared-element transition. This wiki is what the code doesn't tell you: why things are
the way they are, what's fragile, and how to work on it.

**Maintainers (human or LLM): read [[CLAUDE]] first** — it is the schema and the update checklist.

## Start here

- [[architecture]] — layers, DI, data flow, and a worked end-to-end example (the template for new features)
- [[glossary]] — vocabulary used in code and in this wiki
- [[faq]] — questions worth remembering
- [[android-app]] — the one runtime surface: entry points, manifest, build config

## Features

- [[card-list]] — "My Cards" grid (start destination)
- [[card-detail]] — one card: store header, label, display number
- [[card-transition]] — shared-element animation between list and detail
- [[add-card]] — adding a card: pick the store, scan or type the number, save
- [[barcode-scanner]] — reads a card's barcode with the camera (offline, no Play Services)
- [[store-catalogue]] — curated store list, synced into the database on every open
- [[seed-data]] — demo cards, debug builds only

## How-to

- Workflows: [[tdd]] (start here before writing code), [[build-and-run]], [[ci]], [[database-schema-changes]], [[add-a-catalogue-store]], [[code-review]] (incl. `/next-task`)
- Runbooks: [[cli-build-jvm-target-mismatch]], [[reset-local-database]]
- Integrations: [[coil-image-loading]], [[zxing-core]], [[zxing-cpp]]
- Scripts: none yet — see [[scripts/_index|scripts]]

## Knowledge registers

| Folder | What's in it |
|---|---|
| [[decisions/_index\|decisions]] | Why the codebase looks the way it does (15 pages) |
| [[bugs/_index\|bugs]] | Postmortems (3 pages) |
| [[tech-debt/_index\|tech-debt]] | Owed work, open vs resolved (7 pages) |
| [[hacks/_index\|hacks]] | Short-lived workarounds (3 pages) |
| [[workflows/_index\|workflows]] · [[runbooks/_index\|runbooks]] · [[integrations/_index\|integrations]] · [[features/_index\|features]] · [[apps/_index\|apps]] | Folder indexes |

Raw human-dropped material goes in `wiki/assets/` (read-only source; see [[assets/_index|assets]]).

Changelog of the wiki itself: [[_log]].
