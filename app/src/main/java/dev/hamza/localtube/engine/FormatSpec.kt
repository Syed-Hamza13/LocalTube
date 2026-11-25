package dev.hamza.localtube.engine

import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality

/** Everything yt-dlp needs for one chosen option set. */
data class FormatPlan(
    val formatSelector: String,
    val extraOptions: List<Pair<String, String?>>,
    /** True when the plan silently loses quality because FFmpeg is not bundled. */
    val degraded: Boolean,
)

/**
 * Translates the three UI modes into yt-dlp selectors.
 *
 * FFmpeg presence changes what is achievable: without it, separated video+audio streams cannot be
 * merged and audio cannot be re-encoded, so the plan falls back to progressive streams (what
 * YouTube serves at 720p and below) and to a container copy for audio.
 */
object FormatSpec {

    /**
     * One condition per bracket: yt-dlp parses `bestvideo[height<=?1080][ext=mp4]`, while
     * `bestvideo[height<=?1080,ext=mp4]` raises `SyntaxError: Invalid filter specification` and kills
     * the job before a byte is fetched. `&&` is rejected too.
     */
    private fun height(n: Int) = "height<=?$n"

    fun plan(mode: DownloadMode, quality: Quality, audioExt: AudioExt, hasFfmpeg: Boolean): FormatPlan {
        val h = quality.height
        return when (mode) {
            DownloadMode.VIDEO_AUDIO -> if (hasFfmpeg) {
                FormatPlan(
                    formatSelector = sequenceOf(
                        if (h == null) "bestvideo[ext=mp4]+bestaudio[ext=m4a]"
                        else "bestvideo[${height(h)}][ext=mp4]+bestaudio[ext=m4a]",
                        if (h == null) "bestvideo+bestaudio"
                        else "bestvideo[${height(h)}]+bestaudio",
                        if (h == null) null else "best[${height(h)}]",
                        "best",
                    ).filterNotNull().joinToString("/"),
                    extraOptions = listOf("--merge-output-format" to "mp4"),
                    degraded = false,
                )
            } else {
                FormatPlan(
                    formatSelector = sequenceOf(
                        if (h == null) "best[acodec!=none][ext=mp4]"
                        else "best[${height(h)}][acodec!=none][ext=mp4]",
                        if (h == null) "best[acodec!=none]"
                        else "best[${height(h)}][acodec!=none]",
                        "best",
                    ).joinToString("/"),
                    extraOptions = emptyList(),
                    degraded = h != null && h > 720,
                )
            }

            DownloadMode.AUDIO_ONLY -> if (hasFfmpeg) {
                FormatPlan(
                    formatSelector = "bestaudio[ext=m4a]/bestaudio/best[acodec!=none]",
                    extraOptions = buildList {
                        add("--extract-audio" to null)
                        add("--audio-format" to audioExt.key)
                        if (audioExt == AudioExt.MP3) add("--audio-quality" to "0")
                    },
                    degraded = false,
                )
            } else {
                // No re-encoding without FFmpeg: keep the source stream in its own container.
                FormatPlan(
                    formatSelector = "bestaudio[ext=m4a]/bestaudio/best[acodec!=none]",
                    extraOptions = emptyList(),
                    degraded = audioExt == AudioExt.MP3,
                )
            }

            DownloadMode.VIDEO_ONLY -> FormatPlan(
                formatSelector = sequenceOf(
                    if (h == null) "bestvideo[ext=mp4]" else "bestvideo[${height(h)}][ext=mp4]",
                    if (h == null) "bestvideo" else "bestvideo[${height(h)}]",
                    // Sites that serve no separate video stream still need to produce something.
                    if (h == null) "best[ext=mp4]" else "best[${height(h)}]",
                ).joinToString("/"),
                extraOptions = emptyList(),
                degraded = false,
            )
        }
    }

    /** Qualities offered in the sheet, filtered by what the video actually serves. */
    fun offeredQualities(infoHeights: List<Int>): List<Quality> {
        val max = infoHeights.maxOrNull() ?: 0
        return Quality.entries
            .filter { it == Quality.BEST || (it.height != null && it.height <= max) }
            .ifEmpty { listOf(Quality.BEST) }
    }
}
