package dev.hamza.localtube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hamza.localtube.data.prefs.AppSettings
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.download.ActiveDownloads
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadArgs
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import dev.hamza.localtube.engine.FormatSpec
import dev.hamza.localtube.engine.VideoMeta
import dev.hamza.localtube.engine.YtDlpEngine
import dev.hamza.localtube.util.services
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val url: String = "",
    val fetching: Boolean = false,
    val meta: VideoMeta? = null,
    val thumbnailPath: String? = null,
    val error: String? = null,
    val alreadyInLibrary: Boolean = false,
    val sheetOpen: Boolean = false,
    val queuedTitle: String? = null,
)

class HomeViewModel(private val services: ServiceLocator) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val jobs = ActiveDownloads.jobs

    val playlists = services.library.observePlayablePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = services.prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val engine: YtDlpEngine get() = services.engine

    /** Surfaced to the UI so the slim build can tell the user why 1080p is missing. */
    val hasFfmpeg: Boolean get() = services.engine.hasFfmpeg

    /** Qualities worth showing for the fetched video, `Best` first. */
    val offeredQualities: List<Quality>
        get() = FormatSpec.offeredQualities(_state.value.meta?.heights.orEmpty())

    fun onUrlChange(value: String) {
        _state.value = _state.value.copy(url = value, error = null, queuedTitle = null)
    }

    /** Called by the Paste button and by the share intent. */
    fun submitUrl(value: String) {
        val cleaned = value.trim().trim('"', '\'')
        _state.value = _state.value.copy(url = cleaned)
        fetch()
    }

    fun fetch() {
        val raw = _state.value.url.trim()
        if (raw.isEmpty()) {
            _state.value = _state.value.copy(error = "Paste a link first.")
            return
        }
        val candidate = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else if (raw.matches(BARE_ID)) {
            "https://youtu.be/$raw"
        } else {
            _state.value = _state.value.copy(error = "That does not look like a link.", meta = null)
            return
        }

        _state.value = _state.value.copy(
            fetching = true,
            error = null,
            meta = null,
            thumbnailPath = null,
            queuedTitle = null,
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = services.library.alreadyDownloaded(candidate)
                    val meta = services.engine.fetchMeta(candidate)
                    // Cached here so the sheet previews offline and the worker reuses the same file.
                    Triple(saved, meta, services.engine.fetchThumbnail(meta.thumbnailUrl, null))
                }
            }.onSuccess { (saved, meta, thumbnail) ->
                _state.value = _state.value.copy(
                    fetching = false,
                    meta = meta,
                    thumbnailPath = thumbnail,
                    alreadyInLibrary = saved,
                    sheetOpen = true,
                    url = candidate,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    fetching = false,
                    error = error.message ?: "Could not read that link.",
                )
            }
        }
    }

    fun openSheet() {
        if (_state.value.meta != null) {
            _state.value = _state.value.copy(sheetOpen = true)
        } else {
            fetch()
        }
    }

    fun closeSheet() {
        _state.value = _state.value.copy(sheetOpen = false)
    }

    fun queueDownload(
        mode: DownloadMode,
        quality: Quality,
        audioExt: AudioExt,
        playlistIds: List<Long>,
    ) {
        val meta = _state.value.meta ?: return
        val args = DownloadArgs(
            url = _state.value.url,
            videoId = meta.id,
            title = meta.title,
            channel = meta.channel,
            durationSec = meta.durationSec,
            thumbnailUrl = meta.thumbnailUrl,
            mode = mode,
            quality = quality,
            audioExt = audioExt,
            playlistIds = playlistIds,
        )
        viewModelScope.launch {
            val duplicate = services.library.alreadyDownloaded(args.url)
            runCatching { services.scheduler.enqueue(args) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        sheetOpen = false,
                        queuedTitle = meta.title,
                        alreadyInLibrary = duplicate,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        sheetOpen = false,
                        error = "Could not queue the download: ${describe(error)}",
                    )
                }
        }
    }

    /** An enqueue failure used to reach the coroutine handler and kill the process; now it is readable. */
    private fun describe(error: Throwable): String {
        val cause = error.cause?.let { " <- ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        return "${error.javaClass.simpleName}: ${error.message}$cause".take(240)
    }

    fun cancelJob(workId: String) = services.scheduler.cancel(workId)

    /** Creates (or reuses) a playlist from the sheet and returns its id so the UI can select it. */
    suspend fun createPlaylist(name: String): Long? =
        services.library.createPlaylist(name).takeIf { it > 0 }

    fun retryJob(workId: String) {
        services.scheduler.retry(workId)
    }

    fun dismissJob(workId: String) = ActiveDownloads.dismiss(workId)

    fun clearFinished() = ActiveDownloads.clearFinished()

    fun consumeQueuedNotice() {
        _state.value = _state.value.copy(queuedTitle = null)
    }

    companion object {
        private val BARE_ID = Regex("^[A-Za-z0-9_-]{11}$")

        val Factory = viewModelFactory {
            initializer { HomeViewModel(this.services) }
        }
    }
}
