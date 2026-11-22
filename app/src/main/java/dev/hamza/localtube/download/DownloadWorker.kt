package dev.hamza.localtube.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.hamza.localtube.LocalTubeApp
import dev.hamza.localtube.domain.DownloadArgs
import dev.hamza.localtube.engine.EngineException
import dev.hamza.localtube.engine.LiveProgress
import dev.hamza.localtube.util.Notifier
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Runs one yt-dlp download inside a dataSync foreground service provided by WorkManager.
 *
 * Cancellation is deliberately two-step: WorkManager cancels the coroutine, and only killing the
 * Python process through the engine actually stops the transfer.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val services get() = LocalTubeApp.instance.services
    private val workId: String get() = id.toString()

    /** Stable, per-job notification ids that never collide with each other. */
    private val slot: Int get() = workId.hashCode() and 0x3FFF
    private val progressNotificationId: Int get() = PROGRESS_ID_BASE + slot
    private val resultNotificationId: Int get() = RESULT_ID_BASE + slot

    override suspend fun doWork(): Result {
        val args = JobData.fromInput(inputData) ?: return Result.failure()
        ActiveDownloads.running(workId, args)
        postForeground(args, 0f, null, -1L)

        try {
            var lastNotificationAt = 0L
            val stored = services.engine.download(args, workId) { live: LiveProgress ->
                ActiveDownloads.progress(workId, live)
                val now = System.currentTimeMillis()
                if (now - lastNotificationAt >= NOTIF_INTERVAL_MS) {
                    lastNotificationAt = now
                    Notifier.push(
                        applicationContext,
                        progressNotificationId,
                        buildProgress(args, live.percent, live.speed, live.etaSeconds),
                    )
                }
            }

            val thumbnailPath = services.engine.fetchThumbnail(args.thumbnailUrl, null)
            val downloadId = services.library.record(
                videoId = args.videoId,
                url = args.url,
                title = args.title,
                channel = args.channel,
                durationSec = args.durationSec,
                stored = stored,
                thumbnailPath = thumbnailPath,
                mode = args.mode,
                quality = args.quality,
            )
            if (args.playlistIds.isNotEmpty()) {
                services.library.addToPlaylists(args.playlistIds, downloadId)
            }

            ActiveDownloads.done(workId, downloadId)
            Notifier.push(
                applicationContext,
                resultNotificationId,
                Notifier.finished(applicationContext, args.title, success = true, detail = ""),
            )
            return Result.success(
                Data.Builder().putLong(JobData.OUT_DOWNLOAD_ID, downloadId).build()
            )
        } catch (cancelled: CancellationException) {
            ActiveDownloads.cancelled(workId)
            throw cancelled
        } catch (error: Throwable) {
            return handleFailure(args, error)
        } finally {
            services.engine.cancel(workId)
            Notifier.cancel(applicationContext, progressNotificationId)
        }
    }

    override suspend fun onStopped() {
        super.onStopped()
        services.engine.cancel(workId)
        if (isCancelled) ActiveDownloads.cancelled(workId)
        Notifier.cancel(applicationContext, progressNotificationId)
    }

    private fun handleFailure(args: DownloadArgs, error: Throwable): Result {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Download failed"
        if (shouldRetry(error) && runAttemptCount < MAX_ATTEMPTS) {
            ActiveDownloads.failed(workId, "Retrying: $message")
            return Result.retry()
        }
        ActiveDownloads.failed(workId, message)
        Notifier.push(
            applicationContext,
            resultNotificationId,
            Notifier.finished(applicationContext, args.title, success = false, detail = message),
        )
        return Result.failure(Data.Builder().putString(KEY_ERROR, message).build())
    }

    private fun shouldRetry(error: Throwable): Boolean =
        error is IOException ||
            error is SocketTimeoutException ||
            (error is EngineException && RETRYABLE.any { needle ->
                error.message?.contains(needle, ignoreCase = true) == true
            })

    private suspend fun postForeground(args: DownloadArgs, percent: Float, speed: String?, etaSeconds: Long) {
        val notification = buildProgress(args, percent, speed, etaSeconds)
        runCatching { setForeground(ForegroundInfo(progressNotificationId, notification)) }
            .onFailure { Notifier.push(applicationContext, progressNotificationId, notification) }
    }

    private fun buildProgress(args: DownloadArgs, percent: Float, speed: String?, etaSeconds: Long) =
        Notifier.running(
            context = applicationContext,
            workId = workId,
            title = args.title,
            mode = args.mode,
            percent = percent,
            speed = speed,
            etaSeconds = etaSeconds,
        )

    private companion object {
        const val PROGRESS_ID_BASE = 20_000
        const val RESULT_ID_BASE = 60_000
        const val NOTIF_INTERVAL_MS = 1_000L
        const val MAX_ATTEMPTS = 2
        const val KEY_ERROR = "error"

        val RETRYABLE = listOf(
            "getaddrinfo",
            "timed out",
            "connection reset",
            "temporary in",
            "http error 5",
            "unable to download",
            "network is unreachable",
        )
    }
}
