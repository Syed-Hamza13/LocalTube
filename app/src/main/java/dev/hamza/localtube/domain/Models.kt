package dev.hamza.localtube.domain

/** What the user asked yt-dlp to produce. */
enum class DownloadMode(val key: String, val label: String, val short: String) {
    VIDEO_AUDIO("video_audio", "Video + audio", "MP4"),
    AUDIO_ONLY("audio_only", "Audio only", "Audio"),
    VIDEO_ONLY("video_only", "Video only (muted)", "Video");

    companion object {
        fun fromKey(value: String?): DownloadMode =
            entries.firstOrNull { it.key == value } ?: VIDEO_AUDIO
    }
}

/** Cap applied to the video height (`[height<=?N]`). */
enum class Quality(val key: String, val label: String, val height: Int?) {
    BEST("best", "Best", null),
    H2160("2160", "4K", 2160),
    H1440("1440", "1440p", 1440),
    H1080("1080", "1080p", 1080),
    H720("720", "720p", 720),
    H480("480", "480p", 480),
    H360("360", "360p", 360);

    companion object {
        fun fromKey(value: String?): Quality = entries.firstOrNull { it.key == value } ?: BEST
    }
}

enum class AudioExt(val key: String, val label: String) {
    M4A("m4a", "m4a"),
    MP3("mp3", "mp3");

    companion object {
        fun fromKey(value: String?): AudioExt = entries.firstOrNull { it.key == value } ?: M4A
    }
}

enum class StorageMode {
    /** /Android/data/<pkg>/files/Movies/LocalTube — no permission, no copy, fastest. */
    APP_PRIVATE,

    /** Movie/LocalTube through MediaStore — visible in Files & gallery, costs one copy. */
    PUBLIC_MOVIES;

    companion object {
        fun fromKey(value: String?): StorageMode =
            entries.firstOrNull { it.name == value } ?: APP_PRIVATE
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Immutable description of one requested download; passed to WorkManager as inputData. */
data class DownloadArgs(
    val url: String,
    val videoId: String,
    val title: String,
    val channel: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val mode: DownloadMode,
    val quality: Quality,
    val audioExt: AudioExt,
    val playlistIds: List<Long>,
) {
    val taskKey: String get() = "${videoId}-${mode.key}-${quality.key}-${audioExt.key}"
}
