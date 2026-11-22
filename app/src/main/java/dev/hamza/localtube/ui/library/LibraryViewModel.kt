package dev.hamza.localtube.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.data.prefs.AppSettings
import dev.hamza.localtube.data.repository.LibraryStats
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.download.ActiveDownloads
import dev.hamza.localtube.util.MediaActions
import dev.hamza.localtube.util.services
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val query: String = "",
    val pendingDelete: DownloadEntity? = null,
    val pickerFor: DownloadEntity? = null,
    val memberOf: Set<Long> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val services: ServiceLocator) : ViewModel() {

    val storage: StorageManager = services.storage

    private val queryFlow = MutableStateFlow("")
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    val jobs = ActiveDownloads.jobs

    val items: StateFlow<List<DownloadEntity>> = queryFlow
        .flatMapLatest { query -> services.library.observeDownloads(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats = services.library.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats(0, 0))

    val playlists = services.library.observePlayablePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = services.prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setQuery(value: String) {
        queryFlow.value = value
        _state.update { it.copy(query = value) }
    }

    fun report(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun cancelJob(workId: String) = services.scheduler.cancel(workId)

    fun dismissJob(workId: String) = ActiveDownloads.dismiss(workId)

    fun askDelete(entity: DownloadEntity) {
        _state.update { it.copy(pendingDelete = entity) }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val entity = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null, busy = true) }
        viewModelScope.launch {
            services.library.deleteDownload(entity.id)
            _state.update { it.copy(busy = false, message = "Deleted ${entity.title.take(40)}") }
        }
    }

    fun askPlaylists(entity: DownloadEntity) = viewModelScope.launch {
        val memberOf = services.library.playlistsOf(entity.id).toSet()
        _state.update { it.copy(pickerFor = entity, memberOf = memberOf) }
    }

    fun dismissPlaylists() {
        _state.update { it.copy(pickerFor = null, memberOf = emptySet()) }
    }

    fun toggleMembership(playlistId: Long) {
        val entity = _state.value.pickerFor ?: return
        viewModelScope.launch {
            if (playlistId in _state.value.memberOf) {
                services.library.removeFromPlaylist(playlistId, entity.id)
                _state.update { it.copy(memberOf = it.memberOf - playlistId) }
            } else {
                services.library.addToPlaylist(playlistId, entity.id)
                _state.update { it.copy(memberOf = it.memberOf + playlistId) }
            }
        }
    }

    fun createAndAdd(name: String) {
        val entity = _state.value.pickerFor ?: return
        viewModelScope.launch {
            val id = services.library.createPlaylist(name)
            if (id > 0) {
                services.library.addToPlaylist(id, entity.id)
                _state.update { it.copy(memberOf = it.memberOf + id) }
            }
        }
    }

    fun exportToPublic(entity: DownloadEntity, publicFolder: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        val moved = MediaActions.exportToPublic(
            entity = entity,
            storage = services.storage,
            library = services.library,
            publicFolder = publicFolder,
        )
        _state.update {
            it.copy(
                busy = false,
                message = if (moved != null) "Exported to $publicFolder" else "Already public, or MediaStore refused",
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(this.services) }
        }
    }
}
