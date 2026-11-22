package dev.hamza.localtube.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg

/**
 * Unzips and registers the bundled FFmpeg binaries.
 *
 * [init] throws on devices where the native library cannot be loaded; callers decide whether that is
 * fatal. Lives in `src/full` because the slim build does not ship the FFmpeg artifact at all.
 */
internal fun initFfmpeg(context: Context) {
    FFmpeg.getInstance().init(context)
}
