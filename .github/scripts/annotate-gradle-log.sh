#!/usr/bin/env bash
# Gradle's own output is only readable in a signed-in browser, but check-run annotations are public.
# On a failed build this copies the interesting lines out as ::error:: annotations so the failure can
# be read straight from the API. Run it as: annotate-gradle-log.sh <log-file> <label>
#
# GitHub keeps at most 50 annotations per check run, so buckets are ordered highest-signal first,
# compiler diagnostics are collapsed to one annotation per file, and the totals line goes out first
# so a truncated list is always recognisable as truncated.

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

# 1. Compiler diagnostics: one line per source file, each carrying every message it produced.
grep -E '^e: ' "$log" | strip |
  awk '
    {
      line = $0
      sub(/^e: /, "", line)
      i = index(line, "dev/hamza/localtube/")
      if (i > 0) {
        rest = substr(line, i + 20)
        c = index(rest, ":")
        file = substr(rest, 1, c - 1)
        msg = substr(rest, c + 1)
        sub(/^[0-9]+:[0-9]+ ?/, "", msg)
      } else {
        file = "other"
        msg = line
      }
      if (!((file SUBSEP msg) in seen)) {
        seen[file, msg] = 1
        bundle[file] = (file in bundle) ? bundle[file] " ;; " msg : msg
        if (!(file in listed)) { listed[file] = 1; order[++n] = file }
      }
    }
    END { for (k = 1; k <= n; k++) printf "%s -> %s\n", order[k], bundle[order[k]] }
  ' |
  cut -c1-900 >"$tmp/an-errors.txt"

# 2. Gradle's explanation block(s) - the real reason, which sits between "What went wrong" and "Try".
awk '/^\* What went wrong:/{flag=1; next} /^\* Try:/{flag=0} flag' "$log" |
  grep -v '^[[:space:]]*$' | strip | awk '!seen[$0]++' >"$tmp/an-why.txt"

# 3. AAR metadata complaints, which wrap over three lines each: artifact + demanded version.
grep -A2 -E "requires (libraries and applications|this project to use)|Dependency '[^']+' requires" "$log" |
  grep -v '^--$' | strip | awk '!seen[$0]++' >"$tmp/an-aar.txt"

# 4. Reason lines that Gradle/AGP/R8 print indented or mid-log.
grep -Ei 'requires Android (Gradle plugin|compileSdk|build tools)|Manifest merger failed|ERROR:|is not a valid (value|name)|^Missing class|^Warning:|R8: |was already retained|Invocation target' "$log" |
  strip |
  awk '!seen[$0]++' >"$tmp/an-detail.txt"

# 5. Which tasks broke.
grep -E '^(> Task .* FAILED|FAILURE: )' "$log" |
  strip |
  awk '!seen[$0]++' >"$tmp/an-tasks.txt"

echo "::error::${label}: counts files=$(wc -l <"$tmp/an-errors.txt") raw_errors=$(grep -cE '^e: ' "$log") aar=$(wc -l <"$tmp/an-aar.txt") why=$(wc -l <"$tmp/an-why.txt") detail=$(wc -l <"$tmp/an-detail.txt") tasks=$(wc -l <"$tmp/an-tasks.txt")"

annotate 30 "$tmp/an-errors.txt"
annotate 8 "$tmp/an-aar.txt"
annotate 4 "$tmp/an-why.txt"
annotate 4 "$tmp/an-detail.txt"
annotate 2 "$tmp/an-tasks.txt"
