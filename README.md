# LocalTube

Offline-first `yt-dlp` downloader + player for Android. Personal use, no account, no ads, no
analytics, no tracker — and no storage permission.

Built for **Android 15 (API 35) on arm64-v8a** (developed against a Realme 11x 5G), Kotlin +
Jetpack Compose (Material 3), MVVM, Room, WorkManager and
[`youtubedl-android`](https://github.com/junkfood/youtubedl-android) (the maintained
`io.github.junkfood02.youtubedl-android` fork of yausername's library).

**Status:** CI builds every push, both variants, debug and release, all green — release APKs measure
**54.05 MB** (full) and **20.04 MB** (slim), and each build now asserts that the yt-dlp bundle, the
Python runtime and (for `full`) FFmpeg are still inside the APK after R8. First real install on the
target phone gets through the whole UI; `YoutubeDL.init()` fails there and the reason is now on
screen instead of in logcat, so engine startup is the open item.

---

## What it does

| Screen | What you get |
| --- | --- |
| **Download** | URL field with **Paste**, `Fetch options`, and Share-sheet import (`YouTube → Share → LocalTube`). A bottom sheet shows *Video + audio* (quality picker from what the video actually offers), *Audio only* (m4a / mp3) and *Video only (muted)*, plus playlist chips. Live percentage · speed · ETA in the app **and** in the notification shade. |
| **Library** | Everything on disk, grouped **Today / Yesterday / This week / This month / dated**, instant local search by title, list **or** grid. Tap → plays inside the app. Overflow menu: play, add to playlist, share, copy into Movies/Music, open with another player, delete (confirm dialog removes file **and** Room row). Long-press → playlist chips. |
| **Playlists** | Create/rename-free simple playlists, each shown with item count + total size + cover. A video can live in several playlists. Deleting a playlist never deletes a file. |
| **Player** | Fullscreen `Media3/ExoPlayer` with hand-drawn controls: back, **stop**, previous, **-10 s**, **play/pause**, **+10 s**, next, speed (0.75×–2×), **device volume + mute**, lock, double-tap left/right to skip, immersive system bars, keep-screen-on, queue-aware next/previous (whole library or one playlist). |
| **Settings** | Storage location + folder name, theme, wallpaper colours, default format/quality/audio, disk used by the app, clear preview cache, wake-and-check yt-dlp version. |

First launch asks **where videos should live** — private app folder (default, zero permissions) or
public `Movies/<folder>` + `Music/<folder>` — and you can change it later in Settings.

## Size — the honest numbers

The engine is not the app. Measured from the published AARs, `arm64-v8a` slice only:

| Payload | Size |
| --- | --- |
| `ytdlp:library` — `libpython.zip.so` 13.64 MB + `libqjs.so` 0.87 MB | **14.52 MB** |
| `ytdlp:library` — `res/raw` (Python + yt-dlp bundle) | 3.02 MB |
| `ytdlp:ffmpeg` — `libffmpeg.so` | **34.50 MB** |
| Compose + Room + WorkManager + Media3 + Coil + this app, R8-shrunk, single ABI | **2.50 MB** |

That last line is arithmetic from the builds below: the slim release APK (20.04 MB) minus the 17.54 MB
the yt-dlp library itself ships. Everything the app adds is 2.5 MB.

| Build | Release APK | Debug APK | Needs FFmpeg for |
| --- | --- | --- | --- |
| **full** (`gradle assembleRelease`) | **54.05 MB** | 68.93 MB | merging 1080p/4K video+audio into one mp4, and `-x` to mp3 |
| **slim** (`gradle assembleRelease -Pslim=true`) | **20.04 MB** | 34.90 MB | — (progressive streams ≤720p, m4a extraction only) |

Measured in CI on commit `47ae3d7`; the debug numbers are larger because they are not shrunk, and the
54.05 − 20.04 = 34 MB gap is exactly the FFmpeg binary.

Every run prints the exact numbers twice: in the **job summary** and as annotations on the
"Report APK sizes" step (annotations are readable without signing in). `useLegacyPackaging` is
on because the Python/FFmpeg `.so` files must be extracted at install, so the *installed* size is a
little larger than the APK.

## Build it — GitHub Actions (no Android SDK needed on your PC)

1. Push this folder to a GitHub repository (private is fine).
2. **Actions → “Build APK” → Run workflow** (or just push to `main`). It runs a 2-job matrix:
   `full` and `slim`, each `assembleDebug` + `assembleRelease`.
3. Open the run → **Artifacts** → download `LocalTube-full-apk` (or `-slim-apk`). The zip contains
   `app-release.apk` and `app-debug.apk`; install the **release** one on the phone (*Allow from
   unknown sources* for your browser/File Manager).

Toolchain pinned in the workflow: JDK 17 (temurin), Gradle **8.14.8** via `gradle/actions/setup-gradle@v4`.
The repository deliberately contains **no** binary `gradle-wrapper.jar`; CI calls `gradle` directly.

### Optional: sign releases with your own key

Without secrets CI signs the release build with the debug key (fine for personal installs, useless
for updates later). To use your own key, create it locally
(`keytool -genkeypair -keystore release.keystore -alias localtube -keyalg RSA -keysize 2048 -validity 10000`)
then add repo secrets — CI never sees the file, only the base64 blob:

| Secret | Value |
| --- | --- |
| `LOCALTUBE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `LOCALTUBE_KEY_ALIAS` | `localtube` |
| `LOCALTUBE_KEY_PASSWORD` | keystore password |

`*.keystore`, `*.jks` and `keystore.properties` are git-ignored.

### Build locally instead

You need JDK 17 and an Android SDK (`compileSdk 36`). Then:

```bash
gradle wrapper --gradle-version 8.14.8   # once, generates the missing wrapper jar
./gradlew assembleRelease                # full
./gradlew assembleRelease -Pslim=true    # slim
```

> **What is verified and what is not:** CI compiles and packages both variants (`full`, `slim`) in
> debug *and* release, so the code, the pinned dependency set and the R8 rules are proven, and the
> payload check reads the finished APK: `libpython.zip.so` ships **`Defl`**, which is what makes the
> installer extract it into `nativeLibraryDir` — the one place the engine is willing to read *and*
> `exec()` from — and the yt-dlp bundle survives shrinking (in release under its shortened path
> `res/Nc`, not the `res/raw/ytdlp` name). Packaging cannot prove what a given phone ends up with,
> so the engine banner prints the wrapped cause chain plus the paths and free space `init()` needs.
> Downloads, the MediaStore publish and the share sheet still get their real test on first use.

## Permissions (all of them)

```
INTERNET                     – fetch the video
ACCESS_NETWORK_STATE         – don't start a download with the radio off
WAKE_LOCK                    – keep going with the screen off
FOREGROUND_SERVICE           – required by the dataSync service
FOREGROUND_SERVICE_DATA_SYNC – Android 14+ typed FGS permission (install-time, normal)
POST_NOTIFICATIONS           – the only runtime prompt (download progress)
```

No `READ/WRITE_EXTERNAL_STORAGE`, no `MANAGE_EXTERNAL_STORAGE`, no location, no advertising id.

### How files are stored on Android 15

`yt-dlp` needs a real filesystem path, which scoped storage will not give you for public folders. So:

1. yt-dlp writes into `Android/media`-adjacent app-private staging:
   `<external-files>/Movies/LocalTube/.staging/<taskKey>/`
2. the biggest produced file is then either **renamed in place** (private mode — no extra I/O at all)
   or **published through MediaStore** with `IS_PENDING` into `Movies/<folder>` (video) /
   `Music/<folder>` (audio), which also needs no permission on API 29+
3. the Room row keeps the final path (absolute path or `content://` URI), size, ext, thumbnail path,
   title, channel, duration and timestamp; thumbnails live in `.thumbs/` keyed by sha1 of the remote
   URL and are reference-counted before deletion

Playback always uses the stored path/URI through `FileProvider`, so other players can open it too.

## Layout

```
app/src/main/java/dev/hamza/localtube/
├── LocalTubeApp.kt            no eager engine start; re-adopts running jobs after process death
├── MainActivity.kt            single activity, handles share/VIEW/cancel-notification intents
├── domain/Models.kt           DownloadMode / Quality / AudioExt / StorageMode / ThemeMode / DownloadArgs
├── di/ServiceLocator.kt       hand-written graph (no Hilt/Koin → ~2 MB and a faster cold start)
├── data/
│   ├── local/                 Room entities, DAOs (Flow queries, FK CASCADE), database
│   ├── prefs/AppPrefs.kt      DataStore: storage choice, theme, defaults, onboarded flag, job queue
│   ├── storage/StorageManager staging → rename or MediaStore publish, delete, thumbnails, sizes
│   └── repository/LibraryRepository  the only door to Room + disk
├── engine/
│   ├── YtDlpEngine.kt         init, --dump-json meta, download with progress, cancel, thumbnail
│   └── FormatSpec.kt          format strings per mode, with slim-build fallbacks
├── download/
│   ├── DownloadScheduler.kt   WorkManager enqueue/cancel/retry/restore
│   ├── DownloadWorker.kt      CoroutineWorker + setForeground(ForegroundInfo) notification
│   ├── ActiveDownloads.kt     in-memory live job board (progress is never written to Room)
│   └── JobData.kt             DownloadArgs ↔ WorkManager Data ↔ persisted queue snapshot
├── util/                      Notifier, Fmt (bytes/duration/ETA/date buckets), MediaActions, extras
└── ui/
    ├── theme/                 M3 colour + type, dynamic colour on API 31+, no XML component lib
    ├── icons/PlayerIcons.kt   the ~15 glyphs material-icons-core does not ship, drawn inline
    ├── components/            thumbnails, badges, rows/tiles, playlist chips, dialogs
    ├── home/                  url box + format bottom sheet + live job cards
    ├── library/               search, date-grouped list/grid, delete confirm, export
    ├── playlists/             playlist list + detail
    ├── player/                ExoPlayer + SurfaceView + custom controls
    ├── settings/              storage, appearance, defaults, engine + first-run dialog
    └── navigation/            routes, bottom tabs, fullscreen player route
```

`app/src/full/java/` and `app/src/slim/java/` each hold one file, `engine/FfmpegSupport.kt`: the full
build initialises the bundled FFmpeg, the slim build has no FFmpeg artifact on its classpath at all,
so it compiles the no-op twin. `android.sourceSets` picks the directory from `-Pslim`, which keeps one
source tree instead of two product flavours.

## Deliberate size choices

* single ABI `arm64-v8a`, `isMinifyEnabled = true`, `isShrinkResources = true`, R8 full mode
* `material-icons-core` only (extended icons ≈ 1.5 MB for a few glyphs); player icons are inline vectors
* no Hilt/Koin (`ServiceLocator`), no Retrofit/OkHttp/Glide (Coil3 without the network engine — the
  app only ever loads local thumbnails), no material-components XML theme (`Theme.DeviceDefault`)
* `dependenciesInfo { includeInApk = false }`, no exported Room schemas, `META-INF` + kotlin metadata
  excluded from packaging
* progress is kept in memory, not in Room — hundreds of yt-dlp callbacks per file would otherwise
  burn flash and battery for data nobody keeps

## Things to know

* **yt-dlp breaks when YouTube changes.** The engine is pinned inside the AAR; when a site change
  breaks downloads, update `ytdlp = "…"` in `gradle/libs.versions.toml` and rebuild.
* **No playlist *URL* import** — playlists here are local collections you build yourself; a shared
  YouTube playlist link downloads the single video you pasted (`--no-playlist`).
* Audio-only files are not "background audio" — play them in the player or export them to Music.
* Subtitles, chapters, SponsorBlock and portrait-video cropping are **not** implemented; each one
  costs size or complexity this project tries to avoid.
* ⚠️ **License:** `LICENSE` is currently MIT, but `youtubedl-android` is **GPL-3.0**, and shipping an
  APK linked against it makes yours a distribution of a GPL-3 work. Before you publish it anywhere,
  switch `LICENSE` to GPL-3.0 (or drop the dependency) — for purely personal installs it does not matter.
