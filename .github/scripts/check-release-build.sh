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

# ── Signing: bundleRelease signs with the upload key from LOYSCAN_UPLOAD_* (#74) ─────────────────────────────────────
# A throwaway key, made the way the release-signing wizard makes the real one: PKCS12, one password for the store and
# the key (PKCS12 ignores a separate key password). The passwords are markers no log may contain.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
store_file="$work/upload.jks"
if command -v cygpath >/dev/null 2>&1; then store_file=$(cygpath -m "$store_file"); fi  # Gradle on Windows needs C:/…
marker="never-log-this-$RANDOM$RANDOM"
export LOYSCAN_UPLOAD_STORE_PASSWORD="$marker"
keytool -genkeypair -keystore "$store_file" -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=LoyScan CI throwaway" -storepass:env LOYSCAN_UPLOAD_STORE_PASSWORD >/dev/null 2>&1
fingerprint() { sed -n 's/^[[:space:]]*SHA256: //p' | head -n1 | tr -d '\r'; }
expected=$(keytool -list -v -keystore "$store_file" -storepass:env LOYSCAN_UPLOAD_STORE_PASSWORD | fingerprint)
[[ -n "$expected" ]] || fail "couldn't read the throwaway key's fingerprint"

# Blank Gradle properties, so a developer's own ~/.gradle/gradle.properties upload key can't stand in for these.
blank=(-Ployscan.upload.storeFile= -Ployscan.upload.storePassword= -Ployscan.upload.keyAlias=)

# Half set up (no key alias): the build stops, names what's missing, and prints no password.
export LOYSCAN_UPLOAD_STORE_FILE="$store_file"
unset LOYSCAN_UPLOAD_KEY_ALIAS
if out=$(./gradlew -q :app:printVersion "${blank[@]}" 2>&1); then fail "a half-set-up upload key was accepted"; fi
grep -q 'LOYSCAN_UPLOAD_KEY_ALIAS' <<<"$out" || fail "the half-set-up error doesn't name LOYSCAN_UPLOAD_KEY_ALIAS: $out"
if grep -q "$marker" <<<"$out"; then fail "the half-set-up error printed the store password"; fi
if grep -q 'LOYSCAN_UPLOAD_STORE_FILE' <<<"$out"; then fail "the half-set-up error names a value that is set"; fi
pass "a half-set-up upload key stops the build, naming only what's missing"

# Fully set up: the AAB is signed with exactly this key, and the log never shows the password.
export LOYSCAN_UPLOAD_KEY_ALIAS=upload
aab=app/build/outputs/bundle/release/app-release.aab
rm -f "$aab"
./gradlew bundleRelease "${blank[@]}" >"$work/build.log" 2>&1 || { tail -n 50 "$work/build.log"; fail "bundleRelease failed"; }
[[ -f "$aab" ]] || fail "bundleRelease made no $aab"
if grep -q "$marker" "$work/build.log"; then fail "the build log contains the store password"; fi
# Not -strict: that also fails on a self-signed certificate, which every upload key is.
verify=$(jarsigner -verify "$aab" 2>&1 | tr -d '\r') || fail "jarsigner rejects $aab: $verify"
grep -q '^jar verified\.' <<<"$verify" || fail "$aab is not signed: $verify"
signer=$(keytool -printcert -jarfile "$aab" | fingerprint)
[[ "$signer" == "$expected" ]] || fail "$aab is signed by $signer, not the upload key $expected"
pass "bundleRelease signs the AAB with the upload key, and the log doesn't show its password"
