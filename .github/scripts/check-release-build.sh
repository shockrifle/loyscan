#!/usr/bin/env bash
# Checks the release build's version scheme and signing (#74) through what Gradle resolves and what it builds, as a
# release would. Run from the repo root: on CI (tests.yml, "Release build"), and locally in Git Bash.
# wiki/workflows/release.md
set -euo pipefail

fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { printf 'ok: %s\n' "$1"; }

# The "<versionName> <versionCode>" Gradle resolves, with any extra Gradle arguments.
resolved_version() { ./gradlew -q :app:printVersion "$@" | tr -d '\r'; }

expect_version() {
  local name="$1" code="$2" got
  got=$(resolved_version -Ployscan.versionName="$name") || fail "versionName $name was rejected"
  [[ "$got" == "$name $code" ]] || fail "versionName $name: expected \"$name $code\", got \"$got\""
  pass "versionName $name -> versionCode $code"
}

expect_rejected() {
  local name="$1" out
  if out=$(./gradlew -q :app:printVersion -Ployscan.versionName="$name" 2>&1); then
    fail "versionName \"$name\" was accepted"
  fi
  grep -q 'loyscan.versionName must be' <<<"$out" || fail "versionName \"$name\" failed for another reason: $out"
  pass "versionName \"$name\" is rejected"
}

# ── Version scheme: X.Y.Z, versionCode = X*10000 + Y*100 + Z ────────────────────────────────────────────────────────
declared=$(sed -n 's/^loyscan\.versionName=//p' gradle.properties | tr -d '\r')
[[ "$(resolved_version)" == "$declared "* ]] || fail "the build doesn't use gradle.properties' loyscan.versionName ($declared)"
pass "the build uses gradle.properties' versionName $declared"
expect_version 1.0.0 10000
expect_version 1.2.3 10203
expect_version 0.9.1 901
expect_version 12.34.56 123456
expect_rejected 1.0
expect_rejected v1.0.0
expect_rejected 1.100.0
expect_rejected 1.0.100
expect_rejected 01.0.0
