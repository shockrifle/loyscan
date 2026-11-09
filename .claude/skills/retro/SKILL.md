---
name: retro
description: "Conduct a retrospective on a coding session: suggest changes to LoyScan's agent environment so future runs go better."
disable-model-invocation: true
argument-hint: "[session id or topic; default: this session]"
---

The user has asked for a **retrospective**. You are suggesting improvements to the coding agent's **environment**
to improve future runs. Target: `$ARGUMENTS`

## Steps

1. Call the Skill tool with `writing-for-agents` for the writing style guide.

2. Read the primary sources for the session. If the user doesn't name one, use the current one. Session
   transcripts are JSONL files under `~/.claude/projects/C--workspace-LoyScan/` (one per session; a session's
   subagents under `<session id>/subagents/`). Also read the commits the session made (`git log`, `git show`) and
   any issue comments it posted.

3. Look for candidates for improvement in these categories.

- **Navigation**: how easy was it for the agent to find the right files? Was the right wiki page missing, stale,
  or not linked from `wiki/index.md` or the feature page? Would a **navigation pointer** make it easier? _Use
  when_ the session took a long time to find a piece of information.
- **Automated checks**: are there automated checks that could catch errors the agent made? The repo's gate is
  `./gradlew testDebugUnitTest koverVerify` locally and the GitHub Actions workflow in `.github/workflows/`
  (`wiki/workflows/ci.md`). Read those first, so a check that exists but is unwired or
  silently broken is the finding, not a reinvention. A **mechanical** mistake (a fixed pattern, a banned API, a
  file-location rule, a missing keep rule) gets a deterministic check: a plain JUnit test that reads the source
  or config, like `RouteKeepRulesTest` and `FrozenSchemaTest` do, an Android Lint rule, or a CI step, whichever
  is cheapest. _Use when_ the agent made a mistake an automated check could have caught.
- **Review rules**: should the reviewer be given a new rule to enforce, or an existing rule removed or clarified?
  The reviewer's rules are `.claude/commands/review-changes.md` plus the wiki pages it points at. Default to a
  check (above) for mechanical violations; keep the review file for genuine **judgement calls**. _Use when_ the
  independent review (`/next-task` step 4) failed to catch a mistake, or flagged something the project had
  already decided.
- **Steering files**: should instructions move out of the root `CLAUDE.md` into a wiki page it points at, or into
  a check? The root `CLAUDE.md` is loaded every turn and should hold quick facts and pointers only. Same question
  for the user's global `~/.claude/CLAUDE.md` if the session shows it steering this repo. _Use when_ a steering
  file is large or the session ignored an instruction in it.
- **Commands and skills**: did `/next-task`, `/review-changes`, `/review-fix` or a skill in `.claude/skills/`
  lead the agent wrong, stall at a step, or ask the user something it could have looked up? _Use when_ the
  session ran one of them.
- **Tool economy**: did the agent make expensive tool calls that could be streamlined: a full Gradle build where
  one test class would do, a whole-suite run in a loop, reading huge files or transcripts whole, a CLI build that
  failed on the JVM target (`wiki/runbooks/cli-build-jvm-target-mismatch.md`)? _Use when_ the agent made an
  expensive tool call.
- **No-ops**: instructions in steering files that don't change the agent's behaviour. _Use when_ the steering
  files are large and unwieldy.
- **Information access**: could the agent have had information it lacked: device logcat, a CI run's report, an
  emulator it could drive? _Use when_ a crucial piece of information was not available to the agent.

4. Present these candidates to the user, in order of severity. For each: what happened (with a pointer into the
   transcript or a commit SHA), the proposed change, and the file it touches.

5. Change nothing until the user picks. Then make the picked changes; a change to a wiki page, command or skill
   follows the wiki checklist in `wiki/CLAUDE.md` (including the `wiki/_log.md` line), and a new check is
   written test-first like any other code. Transcripts hold personal paths and data: quote from them only what
   is public-safe (`wiki/CLAUDE.md`, "Public-safe").

## Reference

### Implementation vs review

All work goes through two stages: implementation and review (`wiki/workflows/code-review.md`). The implementing
session has the most **context pressure**: it explores, writes code and debugs. The independent reviewer has the
least: it receives the unpushed range, so it needs no exploration. So the reviewer is responsible for imposing
judgement-call standards, not the implementing session's steering files.

### Where things go

- Root `CLAUDE.md`: loaded into every session. Used sparingly, mostly for **navigation pointers** into the wiki.
- `wiki/CLAUDE.md`: the wiki's schema and update checklist. `wiki/workflows/`, `runbooks/`, `decisions/`: the
  reference that commands and skills point at. Look for an existing page before writing a new one.
- `.claude/commands/review-changes.md`: the reviewer's rules (read during review, not implementation).
- `.claude/commands/*.md`, `.claude/skills/*`: user-invoked workflows, and model-invoked skills whose
  description is always loaded (follow `writing-for-agents`).
- Tests and CI: the deterministic checks.

---

Adapted from Matt Pocock's `retro` skill (https://github.com/mattpocock/skills, MIT licence; see
`.claude/skills/LICENSE-mattpocock-skills`).
