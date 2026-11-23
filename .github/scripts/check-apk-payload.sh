#!/usr/bin/env bash
# Fail the build when an APK no longer carries the payloads the engine unpacks on first run.
# Nothing checks these at compile time: R8 and the resource shrinker decide at packaging, and a
# missing one only shows up as a dead engine on the phone.
#
#   usage: check-apk-payload.sh <apk> [ffmpeg]
#
# The yt-dlp bundle is matched by byte size because release builds may rename res/ paths; the
# discovered path is printed so a rename is visible instead of silently tolerated.
set -uo pipefail

apk="$1"
want_ffmpeg="${2:-}"
label="$(basename "$(dirname "$apk")")/$(basename "$apk")"
listing="$(unzip -l "$apk")"
missing=0

by_size() {
    printf '%s\n' "$listing" | awk -v s="$1" '$1 + 0 == s + 0 && NF >= 4 { print $NF; exit }'
}

by_name() {
    printf '%s\n' "$listing" | awk -v n="/$1" 'index($NF, n) { print $NF; exit }'
}

report() {
    local what="$1" path="$2"
    if [[ -z "$path" ]]; then
        printf '::error::%s: %s is MISSING from the APK\n' "$label" "$what"
        missing=1
    else
        printf '::notice::%s: %s at %s\n' "$label" "$what" "$path"
    fi
}

report "yt-dlp bundle" "$(by_size 3170726)"
report "python runtime" "$(by_name libpython.zip.so)"
if [[ "$want_ffmpeg" == "ffmpeg" ]]; then
    report "ffmpeg runtime" "$(by_name libffmpeg.zip.so)"
fi

exit "$missing"
