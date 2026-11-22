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
}
