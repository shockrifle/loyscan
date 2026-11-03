"""Which model and effort a Claude Code subagent really ran on, from its transcript.

Usage: python -I .claude/scripts/reviewer_model.py <agent id or transcript path> [--expect MODEL] [--effort EFFORT]

Reads ~/.claude/projects/*/*/subagents/agent-<id>.jsonl. Trusts only the API requests (params.model,
params.output_config.effort) and the responses (message.model, one per message id). The .meta.json "model" is the alias
the agent was spawned with, and Claude Code may have fallen back from it before any request was sent. Exits 1 when a
response's model differs from its request's, when an advisor call answered, or when --expect / --effort don't match.
"""
import argparse
import collections
import json
import pathlib
import sys


def transcript(arg):
    path = pathlib.Path(arg)
    if path.is_file():
        return path
    matches = sorted(pathlib.Path.home().glob(f".claude/projects/*/*/subagents/agent-{arg}.jsonl"))
    if len(matches) != 1:
        sys.exit(f"Expected one transcript for agent {arg}, found {len(matches)}")
    return matches[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("agent")
    parser.add_argument("--expect", help="the model every request and response must be, e.g. claude-sonnet-5-5")
    parser.add_argument("--effort", help="the effort every request must ask for, e.g. high")
    args = parser.parse_args()
    path = transcript(args.agent)

    requested = collections.Counter()
    responses = {}
    advisor_calls = 0
    with path.open(encoding="utf-8") as lines:
        for line in lines:
            entry = json.loads(line)
            if entry.get("type") == "api-request":
                params = entry.get("params", {})
                requested[(params.get("model"), (params.get("output_config") or {}).get("effort"))] += 1
            elif entry.get("type") == "assistant":
                message = entry["message"]
                responses[message.get("id")] = message.get("model")
                # The advisor (named in every entry's advisorModel and in the tool list) only counts if it answered.
                iterations = (message.get("usage") or {}).get("iterations") or []
                advisor_calls += sum(1 for it in iterations if it.get("type") != "message")
                advisor_calls += sum(
                    1 for block in message.get("content", [])
                    if isinstance(block, dict) and block.get("type") == "server_tool_use" and block.get("name") == "advisor"
                )

    meta = path.with_suffix(".meta.json")
    alias = json.loads(meta.read_text(encoding="utf-8")).get("model") if meta.exists() else None
    answered = collections.Counter(responses.values())
    print(f"spawned as (meta, not proof): {alias}")
    print(f"requested (model, effort):    {dict(requested)}")
    print(f"answered by:                  {dict(answered)} ({len(responses)} responses)")
    print(f"advisor calls:                {advisor_calls}")

    problems = []
    requested_models = {model for model, _ in requested}
    if set(answered) - requested_models:
        problems.append(f"answered by {set(answered) - requested_models}, which was never requested")
    if advisor_calls:
        problems.append(f"{advisor_calls} advisor call(s) answered too")
    if args.expect and (requested_models | set(answered)) != {args.expect}:
        problems.append(f"expected only {args.expect}")
    if args.effort and {effort for _, effort in requested} != {args.effort}:
        problems.append(f"expected effort {args.effort} on every request")
    if not responses:
        problems.append("no responses in the transcript")
    for problem in problems:
        print(f"MISMATCH: {problem}")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
