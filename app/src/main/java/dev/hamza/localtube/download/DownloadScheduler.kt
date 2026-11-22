package dev.hamza.localtube.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.hamza.localtube.data.prefs.AppPrefs
import dev.hamza.localtube.domain.DownloadArgs
import dev.hamza.localtube.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val prefs: AppPrefs,
) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        val workId = request.id.toString()
        ActiveDownloads.put(workId, args)
        updateQueue { it + (workId to args) }
        return workId
    }

    fun cancel(workId: String) {
        // Kill the Python process first: cancelling the worker alone would let the transfer run on.
        engine.cancel(workId)
        runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        ActiveDownloads.cancelled(workId)
        updateQueue { entries -> entries.filterNot { it.first == workId } }
    }

    fun retry(workId: String): String? {
        val existing = ActiveDownloads.of(workId) ?: return null
        ActiveDownloads.dismiss(workId)
        updateQueue { entries -> entries.filterNot { it.first == workId } }
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
        updateQueue { emptyList() }
    }

    /**
     * Re-adopts in-flight work after the process was killed, so the UI is never blind.
     *
     * [WorkInfo] reports state but not input data, so the arguments come from the queue snapshot
     * written at enqueue time; anything WorkManager no longer considers live is pruned from it.
     */
    suspend fun restore() {
        val infos = runCatching {
            workManager.getWorkInfosByTagFlow(TAG).first()
        }.getOrDefault(emptyList())

        val live = infos
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            .associate { it.id.toString() to it.state }

        val queue = JobData.decodeQueue(prefs.queue())
        queue.forEach { (workId, args) ->
            val state = live[workId] ?: return@forEach
            ActiveDownloads.put(
                workId = workId,
                args = args,
                status = if (state == WorkInfo.State.RUNNING) JobStatus.RUNNING else JobStatus.QUEUED,
            )
        }

        val stale = queue.map { it.first }.toSet() - live.keys
        if (stale.isNotEmpty()) updateQueue { entries -> entries.filterNot { it.first in stale } }
    }

    private fun updateQueue(
        transform: (List<Pair<String, DownloadArgs>>) -> List<Pair<String, DownloadArgs>>,
    ) {
        scope.launch {
            val current = JobData.decodeQueue(prefs.queue())
            prefs.setQueue(JobData.encodeQueue(transform(current)))
        }
    }

    companion object {
        const val TAG = "localtube_download"
        private const val BACKOFF_SECONDS = 20L
    }
}
