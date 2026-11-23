#!/usr/bin/env bash
# Decides whether this Tests run runs the gate (#199): wiki/workflows/ci.md -> What runs. The run's first step after
# checkout; every later step runs only on run=true. Checked by check-ci-gate.sh.
# Env: EVENT (github.event_name), REPO (owner/name), GITHUB_OUTPUT, and GH_TOKEN for the API.
# Writes to $GITHUB_OUTPUT:
#   run=true|false   whether the gate runs
#   marker=true|false  pull_request only: whether a green run may record its tree as tested (see the workflow)
#   tree=<sha>       HEAD's tree, which names that record
# Anything unexpected (no base commit, an API error) gives run=true: a full run costs minutes, a wrong skip a check.
set -euo pipefail

# Wiki pages a test reads (StoreListingTest, PrivacyPolicyTest): a change to one runs the gate like code does.
tested_wiki='^wiki/integrations/google-play\.md$'
# What the cache keys in tests.yml hash or are made by: setup-gradle's Gradle files, the wrapper, the version catalog,
# and the workflow and scripts that form the keys. A PR changing one leaves master's entries stale unless master runs.
cache_inputs='(^|/)[^/]*\.gradle[^/]*$|(^|/)gradle\.properties$|^gradle/|^buildSrc/|(^|/)versions\.properties$|^\.github/workflows/tests\.yml$|^\.github/scripts/(ci-gate|robolectric-jars)\.sh$'

run=true
marker=false
tree=$(git rev-parse 'HEAD^{tree}')

# pull_request: the checkout is GitHub's merge commit; its first parent is the base, so HEAD^1..HEAD is the PR's change.
decide_pull_request() {
  local list files=() f wiki_only=true touches_cache=false
  list=$(mktemp)
  # --no-renames: a file moved into wiki/ must count by its old path too.
  if ! git diff --no-renames --name-only -z 'HEAD^1' HEAD >"$list" 2>/dev/null; then
    echo "Can't diff against the base commit (HEAD^1): running the gate."
    rm -f "$list"
    return
  fi
  mapfile -d '' -t files <"$list"
  rm -f "$list"
  if [ "${#files[@]}" -eq 0 ]; then
    echo "The PR changes no files: running the gate."
    return
  fi
  for f in "${files[@]}"; do
    if [[ $f != wiki/* || $f =~ $tested_wiki ]]; then wiki_only=false; fi
    if [[ $f =~ $cache_inputs ]]; then touches_cache=true; fi
  done
  if [ "$wiki_only" = true ]; then
    echo "The PR changes only wiki pages no test reads: skipping the gate."
    run=false
  elif [ "$touches_cache" = true ]; then
    echo "The PR changes what a cache key is made of: running the gate; master runs it again to save fresh caches."
  else
    echo "Running the gate; a green run records its tree, so master can skip it after the merge."
    marker=true
  fi
}

case "${EVENT:-}" in
  pull_request) decide_pull_request ;;
  *) echo "A ${EVENT:-<unknown>} run: running the gate." ;;
esac

printf 'run=%s\nmarker=%s\ntree=%s\n' "$run" "$marker" "$tree"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  printf 'run=%s\nmarker=%s\ntree=%s\n' "$run" "$marker" "$tree" >>"$GITHUB_OUTPUT"
fi
