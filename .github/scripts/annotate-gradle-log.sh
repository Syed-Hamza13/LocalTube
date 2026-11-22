#!/usr/bin/env bash
# Gradle's own output is only readable in a signed-in browser, but check-run annotations are public.
# On a failed build this copies the interesting lines out as ::error:: annotations so the failure can
# be read straight from the API. Run it as: annotate-gradle-log.sh <log-file> <label>
#
# GitHub keeps at most 50 annotations per check run, so the lines are emitted highest-signal first and
# the totals below stay under that budget.

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

annotate() {
  local limit="$1"
  shift
  local file="$1"
  shift
  if [ -s "$file" ]; then
    head -n "$limit" "$file" | sed "s|^|::error::${label}: |"
  fi
}

# kotlinc/javac diagnostics: "e: file:///.../Foo.kt: (12,3): Unresolved reference".
grep -E '^e: ' "$log" | strip | sort -u >"$RUNNER_TEMP/an-errors.txt"
# Gradle's own explanation block, which sits at the very end of the log, after the task lines.
grep -E '^(FAILURE: |Execution failed|A problem occurred|\* What went wrong|\* Try:|\* Get more help|Caused by:|BUILD |Deprecated Gradle|> Task .* FAILED)' "$log" |
  strip |
  awk '!seen[$0]++' >"$RUNNER_TEMP/an-summary.txt"
# Anything else Gradle printed under the failing task ("Required by:", AGP version demands, ...).
grep -E '^(w: |error: |warning: |> )' "$log" | grep -v '^> Task ' | strip | awk '!seen[$0]++' >"$RUNNER_TEMP/an-other.txt"

annotate 30 "$RUNNER_TEMP/an-errors.txt"
annotate 10 "$RUNNER_TEMP/an-summary.txt"
annotate 5 "$RUNNER_TEMP/an-other.txt"

echo "::error::${label}: counts: kotlin/javac=$(wc -l <"$RUNNER_TEMP/an-errors.txt") summary=$(wc -l <"$RUNNER_TEMP/an-summary.txt") other=$(wc -l <"$RUNNER_TEMP/an-other.txt")"
