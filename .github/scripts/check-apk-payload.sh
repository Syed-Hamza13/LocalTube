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

# The bundle the app ships, read instead of hardcoded so a bump cannot leave the APK check asserting
# an old size. res/raw/ytdlp in the app module overrides the one inside the AAR.
BUNDLE=app/src/main/res/raw/ytdlp
if [[ ! -f "$BUNDLE" ]]; then
    printf '::error::engine payloads: %s is missing from the repo\n' "$BUNDLE"
    exit 1
fi
want_bytes="$(wc -c <"$BUNDLE" | tr -d ' ')"
want_version="$(grep -o 'SHIPPED_YTDLP_VERSION = "[^"]*"' app/src/main/java/dev/hamza/localtube/engine/YtDlpEngine.kt |
    sed 's/.*"\(.*\)"/\1/')"
# unzip warns about the shebang ahead of the archive; the entry itself reads fine.
bundle_version="$(unzip -p "$BUNDLE" yt_dlp/version.py 2>/dev/null |
    sed -n "s/^__version__ = '\\(.*\\)'$/\\1/p" | head -1)"

# The extracted copy is only replaced when SHIPPED_YTDLP_VERSION changes, so a bundle swapped without
# bumping it would leave every installed phone on the old extractor forever.
if [[ -z "$want_version" || "$bundle_version" != "$want_version" ]]; then
    printf '::error::engine payloads: bundle is yt-dlp %s but YtDlpEngine expects %s — update SHIPPED_YTDLP_VERSION and SHIPPED_YTDLP_BYTES (%s)\n' \
        "${bundle_version:-unknown}" "${want_version:-unset}" "$want_bytes"
    exit 1
fi

for apk in "$@"; do
    label="$(basename "$(dirname "$apk")")"
    # unzip -v columns: length method compressed-size ratio date time crc name. The method is the
    # only externally visible proof of useLegacyPackaging: the engine execs and unzips files out of
    # nativeLibraryDir, which only exists on disk when the installer extracted them.
    listing="$(unzip -v "$apk")"

    # The yt-dlp bundle is matched by uncompressed length: release builds may rename res/ paths,
    # the bytes may not change. Its real path is printed so a rename stays visible.
    ytdlp="$(printf '%s\n' "$listing" | awk -v n="$want_bytes" '$1 + 0 == n && NF >= 8 { print $NF " " $2; exit }')"
    python="$(printf '%s\n' "$listing" | awk 'index($NF, "/libpython.zip.so") && NF >= 8 { print $NF " " $2; exit }')"
    ffmpeg="$(printf '%s\n' "$listing" | awk 'index($NF, "/libffmpeg.zip.so") && NF >= 8 { print $NF " " $2; exit }')"

    seg="$label"
    if [[ -n "$ytdlp" ]]; then
        seg="$seg ytdlp=$ytdlp"
    else
        seg="$seg ytdlp=MISSING(want $want_bytes)"
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
    printf '::error::engine payloads (bundle %s, %s B): %s\n' "$bundle_version" "$want_bytes" "$segments"
else
    printf '::notice::engine payloads (bundle %s, %s B): %s\n' "$bundle_version" "$want_bytes" "$segments"
fi

exit "$missing"
