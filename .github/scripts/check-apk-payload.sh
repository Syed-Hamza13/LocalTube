#!/usr/bin/env bash
# Fail the build when an APK no longer carries the payloads the engine unpacks on first run.
# Nothing checks these at compile time: R8 and the resource shrinker decide at packaging, and a
# missing one only shows up as a dead engine on the phone.
#
#   usage: check-apk-payload.sh <apk>...   (WANT_FFMPEG=1 makes the ffmpeg bundle mandatory)
#
# Everything leaves as ONE workflow command on purpose: GitHub keeps a single annotation per file
# line, so a loop that prints a row per finding silently loses all but one of them.
set -uo pipefail

segments=""
missing=0

for apk in "$@"; do
    label="$(basename "$(dirname "$apk")")"
    listing="$(unzip -l "$apk")"

    # The yt-dlp bundle is matched by uncompressed length: release builds may rename res/ paths,
    # the bytes may not change. Its real path is printed so a rename stays visible.
    ytdlp="$(printf '%s\n' "$listing" | awk '$1 + 0 == 3170726 && NF >= 4 { print $NF; exit }')"
    python="$(printf '%s\n' "$listing" | awk 'index($NF, "/libpython.zip.so") { print $NF; exit }')"
    ffmpeg="$(printf '%s\n' "$listing" | awk 'index($NF, "/libffmpeg.zip.so") { print $NF; exit }')"

    seg="$label"
    if [[ -n "$ytdlp" ]]; then
        seg="$seg ytdlp=$ytdlp"
    else
        seg="$seg ytdlp=MISSING"
        missing=1
    fi
    if [[ -n "$python" ]]; then
        seg="$seg python=${python##*/}"
    else
        seg="$seg python=MISSING"
        missing=1
    fi
    if [[ -n "$ffmpeg" ]]; then
        seg="$seg ffmpeg=${ffmpeg##*/}"
    elif [[ "${WANT_FFMPEG:-0}" == "1" ]]; then
        seg="$seg ffmpeg=MISSING"
        missing=1
    fi

    segments="${segments:+$segments | }$seg"
done

if (( missing )); then
    printf '::error::engine payloads: %s\n' "$segments"
else
    printf '::notice::engine payloads: %s\n' "$segments"
fi

exit "$missing"
