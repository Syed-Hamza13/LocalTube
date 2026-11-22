package dev.hamza.localtube.engine

import android.content.Context

/**
 * Slim counterpart of `src/full`: no FFmpeg is bundled, so there is nothing to initialise.
 *
 * Never reached at runtime ([YtDlpEngine.hasFfmpeg] is false in this build); it exists so one source
 * tree compiles against both dependency sets.
 */
internal fun initFfmpeg(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
