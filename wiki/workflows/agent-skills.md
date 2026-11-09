# Agent skills

Project Claude Code skills live in `.claude/skills/<name>/SKILL.md`, next to the slash commands in
`.claude/commands/` ([[code-review]]). Five are adapted copies of Matt Pocock's MIT-licensed skills
(https://github.com/mattpocock/skills, licence in `.claude/skills/LICENSE-mattpocock-skills`); why these and why
copies: [[2026-11-09-adapted-third-party-skills]].

| Skill | Invoked by | What it is for | What was adapted |
|---|---|---|---|
| `diagnosing-bugs` | the agent, on a bug, crash, red CI or slowness; or `/diagnosing-bugs` | Hard bugs: build a fast command that goes red on the bug *before* hypothesising, then minimise, rank hypotheses, instrument, fix with a regression test, clean up | Feedback loops are LoyScan's harnesses ([[tdd]] layer table, Robolectric, `adb`, the R8 `smoke` build, Windows vs CI); Timber `[DEBUG-…]` tags; card numbers and pictures are personal data, so redacted; cleanup ends on the gate and the wiki checklist. Its HITL script template asks about a physical card, not a web page |
| `writing-for-agents` | the agent, when editing a skill, command, `CLAUDE.md` or subagent prompt | Style reference for documents agents read: context pointers, progressive disclosure, completion criteria, pruning | An "In LoyScan" section mapping its terms to our files; `wiki/CLAUDE.md` wins for wiki pages |
| `grilling` | the agent, on "grill me" or shaping a plan or issue | Round-by-round interview over a design tree, each question with a recommended answer; facts are looked up by an `Explore` subagent, decisions go to the user | Reads the issue and recorded decisions first; at the end offers to write the outcome where it belongs (issue = what, `decisions/` = why, [[glossary]] = terms) |
| `retro` | the user only: `/retro [session]` | Retrospective on a session: proposes changes to navigation, checks, review rules, steering files, commands and skills | Points at our files (transcripts under `~/.claude/projects/C--workspace-LoyScan/`, `review-changes.md` as the reviewer's rules, JUnit source-reading tests as the cheap mechanical check); changes nothing until the user picks |
| `to-tickets` | the user only: `/to-tickets [issue #n]` | Splits a large issue or plan into ordered vertical-slice issues, each with testable criteria and its blockers, after the user approves the breakdown | Slices follow the [[architecture]] layers and carry their own schema migration; tickets are GitHub issues in the existing style with a "Blocked by #N" line (which `/next-task` reads) plus GitHub's native sub-issue and blocked-by links; a field check is its own `needs-field-check` ticket |

## Changing a skill

- Edit it like any agent document (`writing-for-agents`), and update the row above in the same commit.
- The copies don't track upstream. To pull in an upstream improvement, diff the upstream file against the original
  it was adapted from (Matt Pocock's repo as of 2026-10-31), then port the change by hand.
- Each adapted file ends with a credit line naming the source and licence; keep it.
