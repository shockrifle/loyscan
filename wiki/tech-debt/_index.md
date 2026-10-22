# Tech debt

Longer-lived, accepted owed work: `<slug>.md`. Skeleton: What / Why it exists / Risk / How to pay it down — see
[[CLAUDE]]. When paid down, add `Resolved (<sha>)` to the page's status and **move** the entry below; don't
delete the page.

## Open

- [[list-screen-bypasses-image-loader]] — list tiles use Coil's default loader (no SVG, no logging); no tile loads
  an image today, so it waits for tile photos (#7)
- [[shared-element-key-from-async-state]] — detail key is `card/` until Room emits (suspected)
- [[no-room-migrations]] — DB v2, no migration path
- [[gs1-128-drawn-as-code-128]] — scans keep their FNC1 (`GS1_128`); `hu-coop` is still plain Code 128, unconfirmed at
  the till (#33)

## Resolved

- [[rules-cannot-require-leading-digits]] — rules can require leading digits, and confirmed EAN-13 stores do;
  Auchan's unconfirmed prefix moved to #52 (#32)
- [[no-automated-tests]] — tests in every layer, coverage gate, test-first workflow and CI (#13)
- [[empty-brand-color-crash]] — empty/malformed brand colour crashed detail (and list); now parsed with a
  fallback (#14)
