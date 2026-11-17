#!/usr/bin/env bash
# Builds the signed release AAB for version X.Y.Z (#74), the way a release is built: by the release workflow on a tag,
# and by check-release-build.sh on every CI run. The upload key comes from LOYSCAN_UPLOAD_* (or Gradle properties).
# Fails if Gradle resolves another version than the tag's, or if the AAB isn't signed.
# Usage: release-bundle.sh X.Y.Z [extra Gradle arguments]. It never prints a value: no tracing, no --info.
# wiki/workflows/release.md
set -euo pipefail

fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }

expected="${1:?usage: release-bundle.sh X.Y.Z [Gradle arguments]}"
shift

# The version Gradle resolves, not gradle.properties' text: a -P or ~/.gradle/gradle.properties value overrides that.
resolved=$(./gradlew -q :app:printVersion "$@" | tr -d '\r')
name="${resolved%% *}"
[[ "$name" == "$expected" ]] || fail "the build's version is $name, not $expected: set loyscan.versionName in gradle.properties"

aab=app/build/outputs/bundle/release/app-release.aab
rm -f "$aab"
./gradlew bundleRelease "$@"
[[ -f "$aab" ]] || fail "bundleRelease made no $aab"
# Not -strict: that also fails on a self-signed certificate, which every upload key is.
verify=$(jarsigner -verify "$aab" 2>&1 | tr -d '\r') || fail "jarsigner rejects $aab"
grep -q '^jar verified\.' <<<"$verify" || fail "$aab is not signed: is the upload key set up?"
printf 'Signed %s (version %s)\n' "$aab" "$resolved"
