package dev.hamza.localtube.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.mapper.VideoInfo
import dev.hamza.localtube.BuildConfig
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.data.storage.StoredMedia
import dev.hamza.localtube.domain.DownloadArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class VideoMeta(
    val id: String,
    val title: String,
    val channel: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val pageUrl: String?,
    /** Video heights the extractor actually offers, ascending. */
    val heights: List<Int>,
    val approxBytes: Long,
)

data class LiveProgress(
    val percent: Float,
    val etaSeconds: Long,
    val speed: String?,
)

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin, testable wrapper over youtubedl-android.
 *
 * Everything here is blocking by contract (the library spawns the bundled Python process), so each
 * entry point hops to [Dispatchers.IO] itself and callers never have to remember.
 */
class YtDlpEngine(
    context: Context,
    private val storage: StorageManager,
) {
    private val appContext = context.applicationContext

    /**
     * False in `-Pslim` builds, and also flipped to false in a full build whose FFmpeg payload
     * refuses to unpack: [FormatSpec] can plan around a missing merge step, but only if it is told
     * the truth instead of being handed a build flag.
     */
    @Volatile
    var hasFfmpeg: Boolean = BuildConfig.WITH_FFMPEG
        private set

    /** Why FFmpeg was dropped at runtime, for Settings. Null when working or never expected. */
    @Volatile
    var ffmpegNote: String? = null
        private set

    @Volatile
    private var ready = false

    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        try {
            refreshBundleIfStale()
            YoutubeDL.getInstance().init(appContext)
            if (hasFfmpeg) initFfmpegQuietly()
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            throw EngineException(
                "Could not start the yt-dlp engine: ${causeChain(t)} | ${diskState()}",
                t,
            )
        }
    }

    /**
     * `YoutubeDL.init()` copies `R.raw.ytdlp` only when the extracted file is missing, so an app
     * installed months ago keeps its first bundle forever. Deleting it on a version bump makes the
     * next `init()` unpack the one shipped in this APK — with YouTube, a stale extractor is a dead
     * download rather than a cosmetic gap. If the delete fails the preference stays old, so the next
     * launch retries.
     */
    private fun refreshBundleIfStale() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_BUNDLE_VERSION, null) == SHIPPED_YTDLP_VERSION) return
        val bundleDir = File(
            File(appContext.noBackupFilesDir, YoutubeDL.baseName),
            YoutubeDL.ytdlpDirName,
        )
        bundleDir.deleteRecursively()
        if (!File(bundleDir, YoutubeDL.ytdlpBin).exists()) {
            prefs.edit().putString(KEY_BUNDLE_VERSION, SHIPPED_YTDLP_VERSION).apply()
        }
    }

    private fun initFfmpegQuietly() {
        try {
            initFfmpeg(appContext)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            hasFfmpeg = false
            ffmpegNote = "FFmpeg failed to start, so downloads skip merging and re-encoding: ${causeChain(t)}"
        }
    }

    fun engineVersion(): String? = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()

    /** `yt-dlp -J` equivalent. Also collapses a playlist URL down to its first entry. */
    suspend fun fetchMeta(url: String): VideoMeta = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--playlist-items", "1")
        val info = try {
            YoutubeDL.getInstance().getInfo(request)
        } catch (e: YoutubeDLException) {
            throw EngineException(humanize(e), e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw EngineException(humanize(e), e)
        }
        info.toMeta(url)
    }

    /**
     * Runs one download into a private staging directory and hands the finished file to
     * [StorageManager], which decides between a rename (app-private) or a MediaStore publish.
     *
     * @param processId unique per running job; used by [cancel] to kill the Python process.
     */
    suspend fun download(
        args: DownloadArgs,
        processId: String,
        onProgress: (LiveProgress) -> Unit,
    ): StoredMedia = withContext(Dispatchers.IO) {
        ensureReady()
        val staging = storage.stagingDirFor(processId)
        val plan = FormatSpec.plan(args.mode, args.quality, args.audioExt, hasFfmpeg)

        val request = YoutubeDLRequest(args.url)
            .addOption("--no-playlist")
            .addOption("-f", plan.formatSelector)
            .addOption("-o", File(staging, "%(id)s.%(ext)s").absolutePath)

        plan.extraOptions.forEach { (option, value) ->
            if (value == null) request.addOption(option) else request.addOption(option, value)
        }
        request
            .addOption("--newline")
            .addOption("--no-mtime")
            .addOption("--retries", "3")
            .addOption("--socket-timeout", "20")

        try {
            YoutubeDL.getInstance().execute(request, processId) { percent, etaSeconds, line ->
                onProgress(LiveProgress(percent, etaSeconds, parseSpeed(line)))
            }
        } catch (e: YoutubeDL.CanceledException) {
            staging.deleteRecursively()
            throw CancellationException("cancelled")
        } catch (e: InterruptedException) {
            staging.deleteRecursively()
            throw CancellationException("cancelled")
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw EngineException(humanize(e), e)
        }

        storage.store(staging, args.videoId, args.title, args.mode)
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Downloads the thumbnail into the cache so the library works fully offline. */
    suspend fun fetchThumbnail(url: String?, headers: Map<String, String>?): String? =
        withContext(Dispatchers.IO) {
            if (url.isNullOrBlank()) return@withContext null
            val dest = storage.thumbnailFile(url)
            if (dest.exists() && dest.length() > 0) return@withContext dest.absolutePath
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                if (connection.responseCode !in 200..299) return@withContext null
                dest.parentFile?.mkdirs()
                val written = dest.outputStream().use { out ->
                    connection.inputStream.use { input -> input.copyUpTo(out, MAX_THUMBNAIL_BYTES) }
                }
                if (written <= 0) {
                    dest.delete()
                    null
                } else {
                    dest.absolutePath
                }
            } catch (t: Throwable) {
                dest.delete()
                null
            } finally {
                connection?.disconnect()
            }
        }

    private fun VideoInfo.toMeta(fallbackUrl: String) = VideoMeta(
        id = id ?: fallbackUrl.hashCode().toString(),
        title = (title ?: fulltitle ?: "Untitled").trim(),
        channel = uploader?.trim(),
        durationSec = duration.toLong(),
        thumbnailUrl = thumbnail,
        pageUrl = webpageUrl ?: fallbackUrl,
        heights = formats
            ?.asSequence()
            ?.filter { it.height > 0 && it.vcodec?.let { c -> c != "none" } == true }
            ?.map { it.height }
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty(),
        approxBytes = firstPositive(
            fileSize,
            fileSizeApproximate,
            requestedFormats?.sumOf { positive(it.fileSize) + positive(it.fileSizeApproximate) } ?: 0L,
        ),
    )

    private fun firstPositive(vararg values: Long): Long = values.firstOrNull { it > 0 } ?: 0L

    private fun positive(value: Long): Long = if (value > 0) value else 0L

    private fun parseSpeed(line: String?): String? =
        line?.let { SPEED_REGEX.find(it)?.groupValues?.get(1)?.trim() }

    /**
     * A failed yt-dlp run prints the version-nag WARNING before the ERROR that actually ended the
     * job, and the whole output arrives as one message. Joining and truncating it showed only the
     * nag on the phone, so read the last ERROR line and fall back to the last content line.
     */
    private fun humanize(error: Throwable): String {
        val lines = (error.message ?: error.cause?.message ?: "Unknown error")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[debug]") }
            .toList()
        val raw = lines.lastOrNull { it.startsWith("ERROR:") }
            ?: lines.lastOrNull { !it.startsWith("WARNING:") && !it.startsWith("[info]") }
            ?: "Unknown error"
        val friendly = when {
            raw.contains("Sign in to confirm", true) -> "Age or bot check: this video needs sign-in, which this app does not do."
            raw.contains("Video unavailable", true) -> "Video unavailable."
            raw.contains("is not a valid URL", true) -> "That is not a valid link."
            raw.contains("getaddrinfo failed", true) || raw.contains("Network is unreachable", true) ->
                "No internet connection."
            raw.contains("Unsupported URL", true) -> "yt-dlp does not support this link yet."
            raw.contains("Requested format is not available", true) ->
                "That format is not offered for this video. Try another quality."
            raw.contains("HTTP Error 403", true) ->
                "YouTube refused the file link (403). Retry, or pick a lower quality."
            raw.contains("No space left", true) || raw.contains("not enough space", true) ->
                "Storage is full."
            else -> raw
        }
        return friendly.take(280)
    }

    /**
     * `YoutubeDL.init()` buries the real IO/resource error one or two causes deep under a plain
     * "failed to initialize", and on a phone the banner is the only channel that error has.
     */
    private fun causeChain(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && parts.size < MAX_CAUSE_DEPTH) {
            val text = current.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            parts += current.javaClass.simpleName + (if (text == null) "" else ": $text")
            val next = current.cause
            current = if (next === current) null else next
        }
        return parts.joinToString(" <- ").take(260)
    }

    /**
     * The files `init()` reads and the space it unpacks into. A startup failure on a phone has no
     * other observable surface, and `Defl`-vs-`Stored` in the APK only says what the installer
     * agreed to extract, not what actually landed.
     */
    private fun diskState(): String {
        val libDir = File(appContext.applicationInfo.nativeLibraryDir ?: "")
        val bundle = File(
            File(File(appContext.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName),
            YoutubeDL.ytdlpBin,
        )
        val freeMb = appContext.noBackupFilesDir?.usableSpace ?: -1L
        return "libs=${libDir.absolutePath} python=${sizeOf(File(libDir, "libpython.zip.so"))}" +
            " stub=${sizeOf(File(libDir, "libpython.so"))}" +
            " bundle=${if (bundle.isFile) bundle.length() else "absent"}/want=$SHIPPED_YTDLP_BYTES" +
            " free=${if (freeMb < 0) "?" else "${freeMb / BYTES_PER_MB}MB"}"
    }

    /** Sub-MB payloads (the loader stub is a few KB) must not render as `0MB`, which reads as absent. */
    private fun sizeOf(file: File): String {
        if (!file.isFile) return "absent"
        val bytes = file.length()
        return if (bytes < BYTES_PER_MB) "${bytes / BYTES_PER_KB}KB" else "${bytes / BYTES_PER_MB}MB"
    }

    /** [java.io.InputStream.copyTo] has no ceiling; a rogue response must not fill the cache. */
    private fun InputStream.copyUpTo(out: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (total < maxBytes) {
            val chunk = minOf(buffer.size.toLong(), maxBytes - total).toInt()
            val read = read(buffer, 0, chunk)
            if (read == -1) break
            out.write(buffer, 0, read)
            total += read
        }
        return total
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val MAX_THUMBNAIL_BYTES = 3L * 1024 * 1024
        const val MAX_CAUSE_DEPTH = 4
        const val BYTES_PER_MB = 1024L * 1024L
        const val BYTES_PER_KB = 1024L

        /**
         * The extractor bundle shipped in `app/src/main/res/raw/ytdlp`, which runs on the CPython
         * 3.12 the AAR provides. [SHIPPED_YTDLP_BYTES] is that file's exact length, so a mismatched
         * pair surfaces in the startup banner as `bundle=<n>/want=<m>` instead of silently.
         * CI fails if the version here and inside the bundle ever drift.
         */
        const val SHIPPED_YTDLP_VERSION = "2026.11.23"
        const val SHIPPED_YTDLP_BYTES = 3_073_120L
        const val PREFS = "engine"
        const val KEY_BUNDLE_VERSION = "ytdlp_bundle_version"
        val SPEED_REGEX = Regex("at\\s+([0-9.,]+\\s*[KMGT]?i?B/s)")
    }
}
