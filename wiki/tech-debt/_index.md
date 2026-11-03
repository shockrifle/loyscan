# Tech debt

Longer-lived, accepted owed work: `<slug>.md`. Skeleton: What / Why it exists / Risk / How to pay it down — see
[[CLAUDE]]. When paid down, add `Resolved (<sha>)` to the page's status and **move** the entry below; don't
delete the page.

## Open

- [[no-room-migrations]] — DB v2, no migration path

## Resolved

- [[list-screen-bypasses-image-loader]] — list tiles load their photo and logo through the app's loader (#10)

- [[gs1-128-drawn-as-code-128]] — scans keep their FNC1 (`GS1_128`); `hu-coop` stays plain Code 128, as Coop's own
  app draws it (#33)
- [[shared-element-key-from-async-state]] — the detail key comes from the route, so the transition pairs before
  Room emits (#16)
- [[rules-cannot-require-leading-digits]] — rules can require leading digits, and confirmed EAN-13 stores do;
  Auchan's unconfirmed prefix moved to #52 (#32)
- [[no-automated-tests]] — tests in every layer, coverage gate, test-first workflow and CI (#13)
- [[empty-brand-color-crash]] — empty/malformed brand colour crashed detail (and list); now parsed with a
  fallback (#14)
