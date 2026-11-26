# LoyScan wiki

LoyScan is a single-module Android app (Kotlin, Jetpack Compose, Hilt, Room) for keeping loyalty cards.
Today it shows a grid of cards from a local Room database (added by the user; a curated store catalogue), and a detail screen
reached through a shared-element transition. This wiki is what the code doesn't tell you: why things are
the way they are, what's fragile, and how to work on it.

**Maintainers (human or LLM): read [[CLAUDE]] first** — it is the schema and the update checklist.

## Start here

- [[architecture]] — layers, DI, data flow, and a worked end-to-end example (the template for new features)
- [[design-system]] — the spacing scale, shapes and card shape every screen and the widget use (#108)
- [[glossary]] — vocabulary used in code and in this wiki
- [[faq]] — questions worth remembering
- [[android-app]] — the main runtime surface: entry points, manifest, build config
- [[card-widget]] — the home-screen Glance widget: cards as tiles, a tap opens the card (#11)

## Features

- [[card-list]] — "My Cards" grid (start destination)
- [[card-detail]] — one card: store band, label, display number
- [[card-transition]] — shared-element animation between list and detail
- [[add-card]] — adding a card: pick the store, scan or type the number, save
- [[edit-card]] — editing a card's label, number and store; deleting is on [[card-detail]]
- [[card-photos]] — photos of the physical card, in private storage, deleted with the card
- [[edit-store]] — editing or deleting a custom store from the store picker
- [[store-logo]] — the user's own logo for a store, on every card of it (#151, in slices)
- [[about]] — the About screen from the list: name, version, and the non-affiliation note (#150)
- [[barcode-scanner]] — reads a card's barcode with the camera (offline, no Play Services)
- [[store-catalogue]] — curated store list, synced into the database on every open
- [[seed-data]] — the former debug demo cards (removed, #55)

## How-to

- Workflows: [[tdd]] (start here before writing code), [[build-and-run]], [[ci]], [[database-schema-changes]], [[add-a-catalogue-store]], [[add-a-string]], [[test-backup-on-a-device]], [[code-review]] (PRs, incl. `/next-task`), [[agent-skills]], [[release]], [[public-safe-sweep]]
- Runbooks: [[cli-build-jvm-target-mismatch]], [[gradlew-hangs-in-git-bash]], [[reset-local-database]], [[leaked-signing-secret]]
- Integrations: [[coil-image-loading]], [[zxing-core]], [[zxing-cpp]], [[androidsvg]], [[google-play]]
- Scripts: [[reviewer-model]] (which model a reviewer subagent really ran on) — see [[scripts/_index|scripts]]

## Knowledge registers

| Folder | What's in it |
|---|---|
| [[decisions/_index\|decisions]] | Why the codebase looks the way it does (42 pages) |
| [[bugs/_index\|bugs]] | Postmortems (7 pages) |
| [[tech-debt/_index\|tech-debt]] | Owed work, open vs resolved (7 pages) |
| [[hacks/_index\|hacks]] | Short-lived workarounds (3 pages) |
| [[workflows/_index\|workflows]] · [[runbooks/_index\|runbooks]] · [[integrations/_index\|integrations]] · [[features/_index\|features]] · [[apps/_index\|apps]] | Folder indexes |

Raw human-dropped material goes in `wiki/assets/` (read-only source; see [[assets/_index|assets]]).

Changelog of the wiki itself: [[_log]].
