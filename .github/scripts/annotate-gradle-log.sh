#!/usr/bin/env bash
# Gradle's own output is only readable in a signed-in browser, but check-run annotations are public.
# On a failed build this copies the interesting lines out as ::error:: annotations so the failure can
# be read straight from the API. Run it as: annotate-gradle-log.sh <log-file> <label>
#
# GitHub keeps at most 50 annotations per check run, so the buckets below are ordered highest-signal
# first and their limits stay under that budget.

set -u

log="${1:-}"
label="${2:-gradle}"
tmp="${RUNNER_TEMP:-/tmp}"

if [ ! -s "$log" ]; then
  echo "::error::${label}: build failed with no output captured"
  exit 0
fi

# Order matters: percent first, otherwise the carriage-return escape introduces a stray %25.
strip() {
  sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e 's/\x1b\[[0-9;]*m//g' -e 's/^[[:space:]]*//'
}

annotate() {
  local limit="$1" file="$2"
  if [ -s "$file" ]; then
    head -n "$limit" "$file" | sed "s|^|::error::${label}: |"
  fi
}

# 1. Compiler diagnostics: "e: file:///.../Foo.kt: (12,3): Unresolved reference".
grep -E '^e: ' "$log" | strip | awk '!seen[$0]++' >"$tmp/an-errors.txt"

# 2. Gradle's explanation block(s) - the real reason, which sits between "What went wrong" and "Try".
awk '/^\* What went wrong:/{flag=1; next} /^\* Try:/{flag=0} flag' "$log" |
  grep -v '^[[:space:]]*$' | strip | awk '!seen[$0]++' >"$tmp/an-why.txt"

# 3. Reason lines that Gradle/AGP print indented or mid-log.
grep -Ei 'requires Android (Gradle plugin|compileSdk|build tools)|AAR metadata|Manifest merger failed|ERROR:|is not a valid (value|name)|Deprecation' "$log" |
  strip |
  awk '!seen[$0]++' >"$tmp/an-detail.txt"

# 4. Which tasks broke, plus the "Required by:" chains that explain why a version got pulled in.
grep -E '^(> Task .* FAILED|FAILURE: |[A-Za-z0-9_.:-]+ (needed|required) by )' "$log" |
  strip |
  awk '!seen[$0]++' >"$tmp/an-tasks.txt"

annotate 22 "$tmp/an-errors.txt"
annotate 12 "$tmp/an-why.txt"
annotate 9 "$tmp/an-detail.txt"
annotate 4 "$tmp/an-tasks.txt"

echo "::error::${label}: counts errors=$(wc -l <"$tmp/an-errors.txt") why=$(wc -l <"$tmp/an-why.txt") detail=$(wc -l <"$tmp/an-detail.txt") tasks=$(wc -l <"$tmp/an-tasks.txt")"
