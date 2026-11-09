---
name: diagnosing-bugs
description: Diagnosis loop for hard bugs and performance regressions in LoyScan. Use when the user says "diagnose"/"debug this", or reports something broken, crashing, failing (including a red CI run) or slow.
---

# Diagnosing Bugs

A discipline for hard bugs. Skip phases only when explicitly justified. An easy bug, one whose cause the report
already shows, needs only the bug-fix loop in `wiki/workflows/tdd.md`.

Before exploring, read the wiki pages for the area (from `wiki/index.md`), `wiki/glossary.md`, the postmortems in
`wiki/bugs/_index.md` (the class of bug may have bitten before), and the Open items in `wiki/hacks/_index.md` and
`wiki/tech-debt/_index.md`.

## Redact

This skill has you show commands, outputs and captured artifacts. The repo is treated as public (`wiki/CLAUDE.md`,
"Public-safe"), and LoyScan's data is personal: **a real card number, barcode picture, card photo or store the
user shopped at is personal data.** Write `<REDACTED>` in its place in everything you show, commit or file. A
fixture built from a real card uses invented digits of the same shape (same length, same check digit rule,
same barcode format), never the real ones. Quote only the logcat lines that carry the signal.

If the redacted output is not enough to diagnose the bug, say so and ask the user.

## Phase 1: Build a feedback loop

**This is the skill.** Everything else is mechanical. If you have a **tight** pass/fail signal for the bug (one
that goes **red** on _this_ bug), you will find the cause; bisection, hypothesis-testing and instrumentation all
just consume it. If you don't have one, no amount of staring at code will save you.

Spend disproportionate effort here. **Be aggressive. Be creative. Refuse to give up.**

### Ways to construct one, in roughly this order

Set `JAVA_HOME` first (root `CLAUDE.md`).

1. **Failing test at the layer's seam.** `wiki/workflows/tdd.md` → "Which test for which layer" names the harness
   for every layer (plain JUnit, Robolectric, Room in memory, Compose rule, `AppNavigationTest`). Run one class:
   `./gradlew testDebugUnitTest --tests '*DetailViewModelTest'`.
2. **Replay a captured input through a test.** The thing that broke is usually data: a scanned value, a shared
   picture, a catalogue entry, a database file from an older version. Turn it into a redacted fixture (a drawn
   barcode picture as in `FileCardPhotosTest`, an exported schema as in `DatabaseMigrationTest`) and drive the
   code path with it.
3. **Property / fuzz loop in plain JUnit.** For "sometimes wrong output" (card-number rules, barcode encoding,
   layout maths): run thousands of generated inputs and look for the failure mode.
4. **Differential loop.** Same input, two configurations, diff the result:
   - debug vs the R8-minified `smoke` build (a bug that only exists minified is usually a missing keep rule;
     `RouteKeepRulesTest` and `wiki/decisions/2026-11-05-r8-smoke-test-on-a-device.md`);
   - two Android versions: `@Config(sdk = [29])` on a Robolectric test;
   - Windows vs CI (Linux). Some Robolectric paths behave differently on Windows (`FileProvider` roots,
     `ImageDecoder`, open files that can't be deleted; `wiki/workflows/tdd.md` lists the known ones). A test that
     passes locally and fails on CI may be this.
5. **Bisection harness.** If it worked at a known commit, `git bisect run` with the single test class as the
   check.
6. **On the device.** `./gradlew installDebug`, then drive it with `adb shell am start -n
   com.bdaniel.loyscan/.MainActivity` (or the intent that triggered the bug), read
   `adb logcat --pid=$(adb shell pidof com.bdaniel.loyscan)`, look at the screen with `adb shell uiautomator dump`
   / `adb exec-out screencap -p`. For the minified app: `./gradlew connectedSmokeAndroidTest`.
7. **HITL script.** Last resort, when a human must act: scan a physical card, show a barcode to a till, rotate the
   phone, use the home-screen widget. Copy [scripts/hitl-loop.template.sh](scripts/hitl-loop.template.sh) to the
   scratchpad, edit its steps, and run it, so the loop is still structured. Captured output feeds back to you.

Build the right feedback loop, and the bug is 90% fixed.

### Tighten the loop

Treat the loop as a product. Once you have _a_ loop, **tighten** it:

- Faster? Run one test class, not the suite; one Robolectric test, not the app flow.
- Sharper? Assert on the specific symptom (the wrong digits, the missing node, the exception type), not "didn't
  crash".
- More deterministic? Fake the clock and the dispatcher (`MainDispatcherRule`), wait with `waitUntil` and
  `WAIT_TIMEOUT_MS` instead of sleeping, idle the main looper when Coil is involved (`wiki/workflows/tdd.md`).

A 30-second flaky loop is barely better than no loop; a 2-second deterministic one is tight, a debugging
superpower.

### Non-deterministic bugs

The goal is not a clean repro but a **higher reproduction rate**. Repeat the test 100× (a JUnit loop or a
parameterised test), add load, narrow timing windows with gates on the fakes (`loadGate`, `addGate`, …). A
50%-flake bug is debuggable; 1% is not, so keep raising the rate until it's debuggable.

### When you genuinely cannot build a loop

Stop and say so explicitly. List what you tried. Ask the user for: (a) the device, card or till that reproduces
it, (b) a redacted captured artifact (logcat dump, screen recording with timestamps, the failing CI run's
report), or (c) permission to add temporary instrumentation to a build they run. Do **not** proceed to
hypothesise without a loop.

### Completion criterion: a tight loop that goes red

Phase 1 is done when the loop is **tight** and **red**-capable: you can name **one command** (a test invocation,
an `adb` sequence, a script path) that you have **already run at least once** (show the invocation and its
redacted output), and that is:

- [ ] **Red-capable**: it drives the actual bug code path and asserts the **user's exact symptom**, so it can go
  red on this bug and green once fixed. Not "runs without erroring"; it must be able to _catch this specific
  bug_.
- [ ] **Deterministic**: same verdict every run (flaky bugs: a pinned, high reproduction rate, per above).
- [ ] **Fast**: seconds, not minutes.
- [ ] **Agent-runnable**: you can run it unattended; a human in the loop only via the HITL script.

If you catch yourself reading code to build a theory before this command exists, **stop: jumping straight to a
hypothesis is the exact failure this skill prevents.** No red-capable command, no Phase 2.

## Phase 2: Reproduce + minimise

Run the loop. Watch it go red as the bug appears.

Confirm:

- [ ] The loop produces the failure mode the **user** described, not a different failure that happens to be
  nearby. Wrong bug = wrong fix.
- [ ] The failure is reproducible across multiple runs (or, for non-deterministic bugs, at a high enough rate to
  debug against).
- [ ] You have captured the exact symptom (exception and stack, wrong output, timing) so later phases can verify
  the fix addresses it.

### Minimise

Once it's red, shrink the repro to the **smallest scenario that still goes red**. Cut inputs, screens, fixture
fields and steps **one at a time**, re-running the loop after each cut, and keep only what's load-bearing.

Why bother: a minimal repro shrinks the hypothesis space in Phase 3 and becomes the clean regression test in
Phase 5.

Done when **every remaining element is load-bearing**: removing any one of them makes the loop go green.

## Phase 3: Hypothesise

Generate **3–5 ranked hypotheses** before testing any of them. Single-hypothesis generation anchors on the first
plausible idea.

Each hypothesis must be **falsifiable**: state the prediction it makes.

> Format: "If <X> is the cause, then <changing Y> will make the bug disappear / <changing Z> will make it worse."

If you cannot state the prediction, the hypothesis is a vibe: discard or sharpen it.

**Show the ranked list to the user before testing.** They often have knowledge that re-ranks instantly (which
phone, which card, what changed last). Don't block on it; proceed with your ranking if the user is away.

## Phase 4: Instrument

Each probe must map to a specific prediction from Phase 3. **Change one variable at a time.**

Tool preference:

1. **A test assertion or a debugger breakpoint** (Android Studio, driven by the user) at the boundary that
   separates the hypotheses. One breakpoint beats ten logs.
2. **Targeted logs** with Timber at those boundaries (debug builds plant a `DebugTree`).
3. Never "log everything and grep".

**Tag every debug log** with a unique prefix: `Timber.d("[DEBUG-a4f2] …")`. Cleanup becomes one grep, and logcat
filters on it. Untagged logs survive; tagged logs die.

**Perf branch.** For performance regressions, logs are usually wrong. Establish a baseline measurement first (a
timing loop in a test, `adb shell dumpsys gfxinfo com.bdaniel.loyscan`, the Android Studio profiler driven by the
user), then bisect. Measure first, fix second.

## Phase 5: Fix + regression test

Write the regression test **before the fix**, but only if there is a **correct seam** for it: one where the test
exercises the **real bug pattern** as it occurs at the call site. If the only seam is too shallow (a ViewModel
test when the bug needs navigation and process death; a plain JUnit test when the bug lives in Room's SQL), a
regression test there gives false confidence.

**If no correct seam exists, that itself is the finding.** Note it and tell the user; it may be a tech-debt item.

If a correct seam exists, follow the bug-fix loop in `wiki/workflows/tdd.md`:

1. Turn the minimised repro into a failing test at that seam, named after the behaviour, with a `// #N` comment.
2. Watch it fail for the right reason. Keep the failure message for the commit. If you forced the red by mutating
   code or a fixture, `git diff` to prove the mutation landed before you trust it.
3. Apply the fix.
4. Watch it pass.
5. Re-run the Phase 1 loop against the original (un-minimised) scenario.

## Phase 6: Cleanup

Required before declaring done:

- [ ] Original repro no longer reproduces (re-run the Phase 1 loop)
- [ ] Regression test passes (or the absence of a seam is reported)
- [ ] All `[DEBUG-` instrumentation removed (`git grep '\[DEBUG-'` is empty)
- [ ] Throwaway harnesses and HITL scripts deleted from the repo (they live in the scratchpad)
- [ ] `./gradlew testDebugUnitTest koverVerify` passes
- [ ] The hypothesis that turned out correct is stated in the commit message, with `Fixes #N` / `Refs #N`
- [ ] The wiki checklist in `wiki/CLAUDE.md`: a surprising root cause or a class of bug likely to recur gets a
  `wiki/bugs/YYYY-MM-DD-<slug>.md` postmortem; a recurring failure gets a runbook

---

Adapted from Matt Pocock's `diagnosing-bugs` skill (https://github.com/mattpocock/skills, MIT licence; see
`.claude/skills/LICENSE-mattpocock-skills`).
