# Tech debt

Longer-lived, accepted owed work: `<slug>.md`. Skeleton: What / Why it exists / Risk / How to pay it down — see
[[CLAUDE]]. When paid down, add `Resolved (<sha>)` to the page's status and **move** the entry below; don't
delete the page.

## Open

- [[list-screen-bypasses-image-loader]] — list tiles use Coil's default loader (no SVG, no logging)
- [[empty-brand-color-crash]] — `toColorInt()` on an unguarded route color (latent)
- [[shared-element-key-from-async-state]] — detail key is `card/0` until Room emits (suspected)
- [[no-room-migrations]] — DB v1, no migration path
- [[no-automated-tests]] — safety net and gate in place; test-first workflow and CI still missing

## Resolved

_(none yet)_
