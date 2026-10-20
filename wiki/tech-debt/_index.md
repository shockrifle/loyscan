# Tech debt

Longer-lived, accepted owed work: `<slug>.md`. Skeleton: What / Why it exists / Risk / How to pay it down — see
[[CLAUDE]]. When paid down, add `Resolved (<sha>)` to the page's status and **move** the entry below; don't
delete the page.

## Open

- [[list-screen-bypasses-image-loader]] — list tiles use Coil's default loader (no SVG, no logging)
- [[shared-element-key-from-async-state]] — detail key is `card/` until Room emits (suspected)
- [[no-room-migrations]] — DB v2, no migration path
- [[rules-cannot-require-leading-digits]] — confirmed EAN-13 stores require their prefix; Auchan still waits
  for one (#32, #31)
- [[gs1-128-drawn-as-code-128]] — scans keep their FNC1 (`GS1_128`); `hu-coop` is still plain Code 128, unconfirmed at
  the till (#33)

## Resolved

- [[no-automated-tests]] — tests in every layer, coverage gate, test-first workflow and CI (#13)
- [[empty-brand-color-crash]] — empty/malformed brand colour crashed detail (and list); now parsed with a
  fallback (#14)
