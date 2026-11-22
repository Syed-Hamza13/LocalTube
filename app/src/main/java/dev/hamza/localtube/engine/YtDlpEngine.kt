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

    /** False in `-Pslim` builds, where merging and mp3 encoding are unavailable. */
    val hasFfmpeg: Boolean = BuildConfig.WITH_FFMPEG

    @Volatile
    private var ready = false

    suspend fun ensureReady() = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        try {
            YoutubeDL.getInstance().init(appContext)
            if (hasFfmpeg) {
                runCatching { initFfmpeg(appContext) }
            }
            ready = true
        } catch (t: Throwable) {
            throw EngineException("Could not start the yt-dlp engine on this device.", t)
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

    private fun humanize(error: Throwable): String {
        val raw = (error.message ?: error.cause?.message ?: "Unknown error")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[debug]") }
            .joinToString(" ")
        val friendly = when {
            raw.contains("Sign in to confirm", true) -> "Age or bot check: this video needs sign-in, which this app does not do."
            raw.contains("Video unavailable", true) -> "Video unavailable."
            raw.contains("is not a valid URL", true) -> "That is not a valid link."
            raw.contains("getaddrinfo failed", true) || raw.contains("Network is unreachable", true) ->
                "No internet connection."
            raw.contains("Unsupported URL", true) -> "yt-dlp does not support this link yet."
            else -> raw
        }
        return friendly.take(280)
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
        val SPEED_REGEX = Regex("at\\s+([0-9.,]+\\s*[KMGT]?i?B/s)")
    }
}
