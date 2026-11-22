package dev.hamza.localtube.download

import dev.hamza.localtube.domain.DownloadArgs
import dev.hamza.localtube.engine.LiveProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class JobStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

data class ActiveDownload(
    val workId: String,
    val args: DownloadArgs,
    val status: JobStatus = JobStatus.QUEUED,
    val percent: Float = 0f,
    val etaSeconds: Long = -1L,
    val speed: String? = null,
    val message: String? = null,
    /** Row inserted in Room once the file is on disk. */
    val downloadId: Long? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val title: String get() = args.title
    val isLive: Boolean get() = status == JobStatus.QUEUED || status == JobStatus.RUNNING
}

/**
 * Live job board shared by the worker and the UI.
 *
 * Both run in the same process, so a singleton is enough — writing every yt-dlp progress callback
 * into Room would burn flash and battery for data nobody keeps. Anything that must survive a
 * reinstall of the process is re-adopted by [DownloadScheduler.restore].
 */
object ActiveDownloads {

    private val _jobs = MutableStateFlow<List<ActiveDownload>>(emptyList())
    val jobs: StateFlow<List<ActiveDownload>> = _jobs.asStateFlow()

    fun put(workId: String, args: DownloadArgs, status: JobStatus = JobStatus.QUEUED) {
        _jobs.update { list ->
            val existing = list.firstOrNull { it.workId == workId }
            if (existing == null) {
                list + ActiveDownload(workId = workId, args = args, status = status)
            } else {
                list.map { if (it.workId == workId) it.copy(args = args, status = existing.status) else it }
            }
        }
    }

    fun running(workId: String, args: DownloadArgs) {
        _jobs.update { list ->
            val existing = list.firstOrNull { it.workId == workId }
            if (existing == null) {
                list + ActiveDownload(workId = workId, args = args, status = JobStatus.RUNNING)
            } else {
                list.map {
                    if (it.workId == workId) it.copy(args = args, status = JobStatus.RUNNING) else it
                }
            }
        }
    }

    fun progress(workId: String, live: LiveProgress) = touch(workId) {
        it.copy(
            status = if (it.status == JobStatus.QUEUED) JobStatus.RUNNING else it.status,
            percent = live.percent.coerceIn(0f, 100f),
            etaSeconds = live.etaSeconds,
            speed = live.speed ?: it.speed,
        )
    }

    fun done(workId: String, downloadId: Long) = touch(workId) {
        it.copy(status = JobStatus.DONE, percent = 100f, downloadId = downloadId, message = null)
    }

    fun failed(workId: String, message: String) = touch(workId) {
        it.copy(status = JobStatus.FAILED, message = message)
    }

    fun cancelled(workId: String) = touch(workId) {
        it.copy(status = JobStatus.CANCELLED, message = null)
    }

    fun dismiss(workId: String) {
        _jobs.update { list -> list.filterNot { it.workId == workId } }
    }

    fun clearFinished() {
        _jobs.update { list -> list.filter { it.isLive } }
    }

    fun of(workId: String): ActiveDownload? = _jobs.value.firstOrNull { it.workId == workId }

    private fun touch(workId: String, transform: (ActiveDownload) -> ActiveDownload) {
        _jobs.update { list -> list.map { if (it.workId == workId) transform(it) else it } }
    }
}
