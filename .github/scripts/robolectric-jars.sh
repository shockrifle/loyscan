#!/usr/bin/env bash
# Lists Robolectric's downloaded jars and derives the CI cache key from that list, so a run that downloads a new jar
# (a test pins a new SDK) gets a new key and saves it (#94): wiki/workflows/ci.md -> Caches
# Usage: robolectric-jars.sh <dir> <key prefix>
# Prints the jars, then writes count=<n> and key=<prefix><16 hex of the list's sha256> to $GITHUB_OUTPUT if set.
set -euo pipefail
dir=$1
prefix=$2

jars=""
if [ -d "$dir" ]; then
  jars=$(cd "$dir" && find . -name '*.jar' | LC_ALL=C sort)
fi
count=0
[ -n "$jars" ] && count=$(printf '%s\n' "$jars" | wc -l | tr -d ' ')
key="$prefix$(printf '%s\n' "$jars" | sha256sum | cut -c1-16)"

echo "$count Robolectric jars in $dir:"
[ -n "$jars" ] && printf '%s\n' "$jars"
echo "key: $key"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "count=$count" >> "$GITHUB_OUTPUT"
  echo "key=$key" >> "$GITHUB_OUTPUT"
fi
