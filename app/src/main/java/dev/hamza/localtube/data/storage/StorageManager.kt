package dev.hamza.localtube.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.hamza.localtube.data.prefs.AppPrefs
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.StorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Final location of one stored media file. */
data class StoredMedia(
    /** Absolute path (app-private) or `content://` URI string (MediaStore). */
    val path: String,
    val sizeBytes: Long,
    val ext: String,
)

/**
 * Owns every byte on disk.
 *
 * Android 15 rules followed here: yt-dlp can only write to a real filesystem path, so it always
 * writes into the app-private staging dir (no permission required, works on every API level), and
 * the finished file is either kept there or published into a public collection through MediaStore.
 * No `MANAGE_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`.
 */
class StorageManager(
    context: Context,
    private val prefs: AppPrefs,
) {
    private val appContext = context.applicationContext

    private val mediaRoot: File by lazy {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(appContext.filesDir, "movies")
        File(base, "LocalTube").apply { if (!exists()) mkdirs() }
    }

    private val stagingRoot: File by lazy { File(mediaRoot, ".staging").apply { mkdirs() } }

    private val thumbRoot: File by lazy { File(mediaRoot, ".thumbs").apply { mkdirs() } }

    /** MediaStore requires API 29+ for RELATIVE_PATH; below that we stay app-private. */
    fun publicModeAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun stagingDirFor(taskKey: String): File = File(stagingRoot, taskKey).apply { mkdirs() }

    fun thumbnailFile(url: String): File = File(thumbRoot, "${sha1(url)}.jpg")

    fun toUri(storedPath: String): Uri =
        if (storedPath.startsWith("content://")) {
            Uri.parse(storedPath)
        } else {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                File(storedPath),
            )
        }

    /**
     * Moves the file yt-dlp produced inside [stagingDir] to its permanent home and removes the
     * staging directory. App-private mode renames (instant, no extra I/O); public mode copies into
     * MediaStore and deletes the original.
     */
    suspend fun store(
        stagingDir: File,
        videoId: String,
        title: String,
        mode: DownloadMode,
    ): StoredMedia = withContext(Dispatchers.IO) {
        val produced = stagingDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && !it.name.startsWith(".") }
            ?.maxByOrNull { it.length() }
            ?: error("yt-dlp finished without producing a file")

        val settings = prefs.snapshot()
        val ext = produced.extension.ifBlank { if (mode == DownloadMode.AUDIO_ONLY) "m4a" else "mp4" }
        val baseName = "${sanitize(title)} ($videoId).$ext"
        val size = produced.length()

        val usePublic = settings.storageMode == StorageMode.PUBLIC_MOVIES && publicModeAvailable()
        val storedPath = if (usePublic) {
            publishToMediaStore(produced, baseName, mode, settings.publicFolder) ?: run {
                // MediaStore refused (rare); fall back so the download is never lost.
                keepAppPrivate(produced, baseName).absolutePath
            }
        } else {
            keepAppPrivate(produced, baseName).absolutePath
        }

        deleteRecursively(stagingDir)
        StoredMedia(path = storedPath, sizeBytes = size, ext = ext)
    }

    private fun keepAppPrivate(source: File, displayName: String): File {
        val dest = uniqueChild(mediaRoot, displayName)
        if (!source.renameTo(dest)) {
            source.copyTo(dest, overwrite = true)
            source.delete()
        }
        return dest
    }

    private fun publishToMediaStore(
        source: File,
        displayName: String,
        mode: DownloadMode,
        publicFolder: String,
    ): String? = runCatching {
        val resolver = appContext.contentResolver
        val isAudio = mode == DownloadMode.AUDIO_ONLY
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/$publicFolder"
        } else {
            "${Environment.DIRECTORY_MOVIES}/$publicFolder"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(source.extension, isAudio))
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return@runCatching null

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { input -> input.copyTo(out, COPY_BUFFER) }
        } ?: return@runCatching null

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        source.delete()
        uri.toString()
    }.getOrNull()

    /**
     * Moves an existing app-private file into the public Movies/Music collection and returns the
     * new `content://` path, or null when there is nothing to move. Used by the Library "Export"
     * action so a file can become visible to other apps without ever asking for storage rights.
     */
    suspend fun moveIntoPublic(
        storedPath: String,
        displayName: String,
        mode: DownloadMode,
        folder: String,
    ): String? = withContext(Dispatchers.IO) {
        if (!publicModeAvailable() || storedPath.startsWith("content://")) return@withContext null
        val source = File(storedPath)
        if (!source.exists()) return@withContext null
        publishToMediaStore(source, displayName, mode, folder)
    }

    suspend fun delete(storedPath: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (storedPath.startsWith("content://")) {
                appContext.contentResolver.delete(Uri.parse(storedPath), null, null)
            } else {
                File(storedPath).delete()
            }
        }
        Unit
    }

    suspend fun clearThumbnailCache() = withContext(Dispatchers.IO) {
        deleteRecursively(thumbRoot)
        thumbRoot.mkdirs()
        Unit
    }

    /** Bytes actually occupied by this app's media, including staging leftovers. */
    suspend fun appMediaBytes(): Long = withContext(Dispatchers.IO) {
        walkBytes(mediaRoot)
    }

    private fun uniqueChild(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val next = File(dir, "$stem ($index)$ext")
            if (!next.exists()) return next
            index++
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    private fun walkBytes(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::walkBytes) ?: 0L
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun mimeFor(ext: String, isAudio: Boolean): String = when (ext.lowercase()) {
        "mp4", "m4v" -> if (isAudio) "audio/mp4" else "video/mp4"
        "mkv", "webm" -> if (isAudio) "audio/webm" else "video/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> if (isAudio) "audio/*" else "video/*"
    }

    companion object {
        private const val COPY_BUFFER = 128 * 1024

        /** Filesystem-safe title, unicode preserved so Hindi/Urdu titles keep working. */
        fun sanitize(raw: String, maxLength: Int = 90): String {
            val cleaned = raw
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('.')
            val clipped = cleaned.take(maxLength).trim()
            return clipped.ifBlank { "video" }
        }
    }
}
