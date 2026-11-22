package dev.hamza.localtube.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.hamza.localtube.engine.YtDlpEngine
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owns the WorkManager side of downloading.
 *
 * Downloads are capped at one concurrent worker by [androidx.work.Configuration] defaults plus the
 * serial queue, which is what we want on a phone: two parallel yt-dlp processes pull battery and
 * bandwidth without finishing any file sooner.
 */
class DownloadScheduler(
    context: Context,
    private val engine: YtDlpEngine,
) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    fun enqueue(args: DownloadArgs): String {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(JobData.toInput(args))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueue(request)
        ActiveDownloads.put(request.id.toString(), args)
        return request.id.toString()
    }

    fun cancel(workId: String) {
        // Kill the Python process first: cancelling the worker alone would let the transfer run on.
        engine.cancel(workId)
        runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        ActiveDownloads.cancelled(workId)
    }

    fun retry(workId: String): String? {
        val existing = ActiveDownloads.of(workId) ?: return null
        ActiveDownloads.dismiss(workId)
        return enqueue(existing.args)
    }

    fun cancelAll() {
        ActiveDownloads.jobs.value
            .filter { it.isLive }
            .forEach { job ->
                engine.cancel(job.workId)
                ActiveDownloads.cancelled(job.workId)
            }
        runCatching { workManager.cancelAllWorkByTag(TAG) }
    }

    /** Re-adopts in-flight work after the process was killed, so the UI is never blind. */
    suspend fun restore() {
        val states = listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
        val infos = runCatching {
            workManager.getWorkInfosByStatusFlow(states).first()
        }.getOrDefault(emptyList())

        infos.filter { it.tags.contains(TAG) }.forEach { info ->
            val args = JobData.fromInput(info.inputData) ?: return@forEach
            val status = when (info.state) {
                WorkInfo.State.RUNNING -> JobStatus.RUNNING
                else -> JobStatus.QUEUED
            }
            ActiveDownloads.put(info.id.toString(), args, status)
        }
    }

    companion object {
        const val TAG = "localtube_download"
        private const val BACKOFF_SECONDS = 20L
    }
}
