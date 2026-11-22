package dev.hamza.localtube.download

import androidx.work.Data
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadArgs
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality

/** WorkManager [Data] <-> [DownloadArgs] mapping. Primitives only, no serialisation library. */
object JobData {

    private const val URL = "url"
    private const val VIDEO_ID = "video_id"
    private const val TITLE = "title"
    private const val CHANNEL = "channel"
    private const val DURATION = "duration"
    private const val THUMB = "thumb_url"
    private const val MODE = "mode"
    private const val QUALITY = "quality"
    private const val AUDIO_EXT = "audio_ext"
    private const val PLAYLISTS = "playlists"

    const val OUT_DOWNLOAD_ID = "download_id"

    fun toInput(args: DownloadArgs): Data = Data.Builder()
        .putString(URL, args.url)
        .putString(VIDEO_ID, args.videoId)
        .putString(TITLE, args.title)
        .putString(CHANNEL, args.channel)
        .putLong(DURATION, args.durationSec)
        .putString(THUMB, args.thumbnailUrl)
        .putString(MODE, args.mode.key)
        .putString(QUALITY, args.quality.key)
        .putString(AUDIO_EXT, args.audioExt.key)
        .putString(PLAYLISTS, args.playlistIds.joinToString(","))
        .build()

    fun fromInput(data: Data): DownloadArgs? {
        val url = data.getString(URL) ?: return null
        return DownloadArgs(
            url = url,
            videoId = data.getString(VIDEO_ID) ?: url,
            title = data.getString(TITLE) ?: "Untitled",
            channel = data.getString(CHANNEL),
            durationSec = data.getLong(DURATION, 0L),
            thumbnailUrl = data.getString(THUMB),
            mode = DownloadMode.fromKey(data.getString(MODE)),
            quality = Quality.fromKey(data.getString(QUALITY)),
            audioExt = AudioExt.fromKey(data.getString(AUDIO_EXT)),
            playlistIds = data.getString(PLAYLISTS)
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                .orEmpty(),
        )
    }

    /**
     * Text form for the persisted queue. [androidx.work.WorkInfo] deliberately hides inputData, so a
     * job can only be re-adopted after process death from something the app stores itself.
     */
    fun encode(args: DownloadArgs): String = with(args) {
        listOf(
            url,
            videoId,
            title,
            channel.orEmpty(),
            durationSec.toString(),
            thumbnailUrl.orEmpty(),
            mode.key,
            quality.key,
            audioExt.key,
            playlistIds.joinToString("+"),
        ).joinToString(FIELD)
    }

    fun decode(line: String): DownloadArgs? {
        val f = line.split(FIELD)
        if (f.size < 10 || f[0].isBlank()) return null
        return DownloadArgs(
            url = f[0],
            videoId = f[1].ifEmpty { f[0] },
            title = f[2].ifEmpty { "Untitled" },
            channel = f[3].takeIf { it.isNotBlank() },
            durationSec = f[4].toLongOrNull() ?: 0L,
            thumbnailUrl = f[5].takeIf { it.isNotBlank() },
            mode = DownloadMode.fromKey(f[6]),
            quality = Quality.fromKey(f[7]),
            audioExt = AudioExt.fromKey(f[8]),
            playlistIds = f[9].split('+').mapNotNull { it.toLongOrNull() },
        )
    }

    /** Whole queue as one string, so DataStore keeps a single key for it. */
    fun encodeQueue(entries: List<Pair<String, DownloadArgs>>): String =
        entries.joinToString(RECORD) { (workId, args) -> workId + FIELD + encode(args) }

    fun decodeQueue(raw: String): List<Pair<String, DownloadArgs>> =
        raw.split(RECORD)
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val sep = entry.indexOf(FIELD)
                if (sep <= 0) return@mapNotNull null
                val args = decode(entry.substring(sep + 1)) ?: return@mapNotNull null
                entry.substring(0, sep) to args
            }

    /** Control characters: titles and URLs contain every printable one. */
    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"
}
