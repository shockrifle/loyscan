#!/usr/bin/env bash
# Human-in-the-loop reproduction loop.
# Copy this file to the scratchpad (not the repo), edit the steps below, and run it.
# The agent runs the script; the user follows prompts in their terminal.
#
# Usage:
#   bash hitl-loop.sh
#
# Two helpers:
#   step "<instruction>"          → show instruction, wait for Enter
#   capture VAR "<question>"      → show question, read response into VAR
#
# At the end, captured values are printed as KEY=VALUE for the agent to parse.
#
# `capture` prints its value back to the terminal, where the agent reads it, and
# the repo is treated as public: ask for observations ("did the digits match?",
# "which barcode format did it show?"), never for a real card number.
#
# Adapted from Matt Pocock's skills (https://github.com/mattpocock/skills, MIT licence;
# see .claude/skills/LICENSE-mattpocock-skills).

set -euo pipefail

step() {
  printf '\n>>> %s\n' "$1"
  read -r -p "    [Enter when done] " _
}

capture() {
  local var="$1" question="$2" answer
  printf '\n>>> %s\n' "$question"
  read -r -p "    > " answer
  printf -v "$var" '%s' "$answer"
}

# --- edit below ---------------------------------------------------------

step "Install the debug build (./gradlew installDebug) and open LoyScan on the phone."

step "Tap + and choose to scan a card. Hold the physical card's barcode in front of the camera."

capture SCANNED "Did the scanner accept the barcode? (y/n)"

capture DIGITS_MATCH "Do the digits on screen match the ones printed on the card? (y/n/partly)"

capture NOTES "Anything else you saw (error text, wrong store, a freeze)? Do not paste card numbers:"

# --- edit above ---------------------------------------------------------

printf '\n--- Captured ---\n'
printf 'SCANNED=%s\n' "$SCANNED"
printf 'DIGITS_MATCH=%s\n' "$DIGITS_MATCH"
printf 'NOTES=%s\n' "$NOTES"
