#!/usr/bin/env bash
# Gradle's own output is only readable in a signed-in browser, but step annotations are public.
# On a failed build this copies the interesting lines out as ::error:: annotations so the failure
# can be read straight from the API. Run it as: annotate-gradle-log.sh <log-file> <label>

set -u

log="${1:-}"
label="${2:-gradle}"

if [ ! -s "$log" ]; then
  echo "::error::${label}: build failed with no output captured"
  exit 0
fi

# Order matters: percent first, otherwise the carriage-return escape introduces a stray %25.
strip() {
  sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e 's/\x1b\[[0-9;]*m//g'
}

grep -E '^(e: |w: |FAILURE: |BUILD |Execution failed|\* What went wrong|\* Try:|> |A problem occurred|Caused by:|error: |Deprecated Gradle)' "$log" |
  strip |
  head -n 55 |
  sed "s|^|::error::${label}: |"

echo "::warning::${label}: last 20 log lines"
tail -n 20 "$log" | grep -v '^[[:space:]]*$' | strip | sed "s|^|::warning::${label} tail: |"
