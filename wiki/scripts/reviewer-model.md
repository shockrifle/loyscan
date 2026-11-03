# reviewer-model

`.claude/scripts/reviewer_model.py` tells which model and effort a Claude Code subagent really ran on, read from its
transcript. It is used to check the `/next-task` reviewer ([[code-review]]). It is plain Python 3 with no
dependencies, outside the Gradle build.

## Usage

```
python -I .claude/scripts/reviewer_model.py <agent id or transcript path> [--expect MODEL] [--effort EFFORT]
python -I .claude/scripts/reviewer_model.py a1b2c3 --expect claude-sonnet-5-5 --effort high
```

The agent id is the one the Agent tool returned. The script finds `~/.claude/projects/*/*/subagents/agent-<id>.jsonl`.

## What it reads

- **Requested:** the `api-request` entries, `params.model` and `params.output_config.effort`.
- **Answered:** the `assistant` entries, `message.model`, counted once per `message.id`. One response is split over
  several entries, one per content block.
- **Advisor calls:** a `usage.iterations` entry whose `type` isn't `message`, or a `server_tool_use` block named
  `advisor`. The `advisorModel` field and the tool list name the advisor's model in every transcript, whether it ran
  or not.
- **The `.meta.json` alias** is printed but not trusted: it is the alias the agent was spawned with, before any
  fallback.

The script exits 1 in these cases:
- a response came from a model that no request asked for;
- an advisor call answered;
- `--expect` or `--effort` doesn't match every request and response;
- the transcript holds no responses.

## Gotchas

- The transcript format is Claude Code's own, not a stable API. Checked on version 2.1.307 (2026-11-03). If a field
  moves, the script reports "no responses" rather than a false pass.
