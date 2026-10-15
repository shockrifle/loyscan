# Tech debt

Longer-lived, accepted owed work: `<slug>.md`. Skeleton: What / Why it exists / Risk / How to pay it down — see
[[CLAUDE]]. When paid down, add `Resolved (<sha>)` to the page's status and **move** the entry below; don't
delete the page.

## Open

- [[list-screen-bypasses-image-loader]] — list tiles use Coil's default loader (no SVG, no logging)
- [[shared-element-key-from-async-state]] — detail key is `card/` until Room emits (suspected)
- [[no-room-migrations]] — DB v2, no migration path
- [[rules-cannot-require-leading-digits]] — EAN-13 catalogue stores can't be told apart on a scan; the rule field
  exists, the entries wait for confirmed prefixes (#32, #31)
- [[gs1-128-drawn-as-code-128]] — no FNC1; Coop's `90…` barcode is unconfirmed at the till (#33)

## Resolved

- [[no-automated-tests]] — tests in every layer, coverage gate, test-first workflow and CI (#13)
- [[empty-brand-color-crash]] — empty/malformed brand colour crashed detail (and list); now parsed with a
  fallback (#14)
