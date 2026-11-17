#!/usr/bin/env bash
# Runs the release-signing wizard (scripts/release-signing-setup.sh, #74) with its answers piped in, in a sandbox HOME,
# and checks what it leaves behind: the keystore, the Gradle properties, and a terminal that never shows the password.
# No browser and no gh on its PATH, and it answers "no" to setting secrets. Run from the repo root: on CI ("Release
# wizard checks") and in Git Bash. wiki/workflows/release.md
set -euo pipefail

fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }
pass() { printf 'ok: %s\n' "$1"; }

sandbox=$(mktemp -d)
trap 'rm -rf "$sandbox"' EXIT
mkdir -p "$sandbox/home"
props="$sandbox/home/.gradle/gradle.properties"
tools="$(dirname "$(command -v keytool)"):/usr/bin:/bin"
[[ -d /mingw64/bin ]] && tools="$tools:/mingw64/bin"

# run_wizard ANSWER...: one answer per prompt, in order; the terminal output goes to $sandbox/out.
run_wizard() {
  printf '%s\n' "$@" | HOME="$sandbox/home" PATH="$tools" scripts/release-signing-setup.sh >"$sandbox/out" 2>&1 ||
    { tail -n 20 "$sandbox/out"; fail "the wizard exited with an error"; }
}

opens() {  # opens KEYSTORE PASSWORD: the password opens the upload key
  LOYSCAN_CHECK_PASSWORD="$2" keytool -list -keystore "$1" -alias upload -storepass:env LOYSCAN_CHECK_PASSWORD >/dev/null 2>&1
}

path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

# ── A first run: makes the key and writes the three properties ──────────────────────────────────────────────────────
first="$sandbox/keys/first.jks"
# banner, path, pause, password, certificate name, create, pause, backed up, write, pause, secrets, pause, export, done
run_wizard "" "$first" "" "first-pass-1" "" y "" y y "" n "" n ""
opens "$first" first-pass-1 || fail "the first run's key doesn't open with its password"
grep -qx "loyscan.upload.storeFile=$(path "$first")" "$props" || fail "storeFile isn't the keystore's Java path"
grep -qx 'loyscan.upload.storePassword=first-pass-1' "$props" || fail "storePassword wasn't written"
grep -qx 'loyscan.upload.keyAlias=upload' "$props" || fail "keyAlias wasn't written"
if grep -q 'first-pass-1' "$sandbox/out"; then fail "the wizard printed the password"; fi
pass "a first run makes the key and writes the properties, without showing the password"

# ── A path inside the repo is refused ───────────────────────────────────────────────────────────────────────────────
inside="$PWD/app/wizard-check.jks"
run_wizard "" "$inside" "$sandbox/keys/second.jks" "" "second-pass-3" "" y "" y n "" n "" n ""
[[ ! -e "$inside" ]] || { rm -f "$inside"; fail "the wizard made a keystore inside the repo"; }
grep -q 'inside the repo' "$sandbox/out" || fail "the wizard didn't say why it refused the repo path"
pass "a keystore path inside the repo is refused"

# ── Rotating: a new keystore path never reuses the saved password (wiki/runbooks/leaked-signing-secret.md) ──────────
rotated="$sandbox/keys/rotated.jks"
# Enter at the password prompt must not keep the old one: the wizard asks again, and gets a new one.
run_wizard "" "$rotated" "" "" "rotated-pass-2" "" y "" y y "" n "" n ""
opens "$rotated" rotated-pass-2 || fail "the rotated key doesn't open with the new password"
if opens "$rotated" first-pass-1; then fail "the rotated key reuses the leaked password"; fi
grep -qx 'loyscan.upload.storePassword=rotated-pass-2' "$props" || fail "the new password wasn't written"
pass "a new keystore path asks for a new password instead of keeping the saved one"
