package dev.hamza.localtube.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.data.local.PlaylistSummary
import dev.hamza.localtube.data.prefs.AppSettings
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.util.MediaActions
import dev.hamza.localtube.util.services
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val open: Long? = null,
    val pendingDeletePlaylist: PlaylistSummary? = null,
    val pendingDeleteDownload: DownloadEntity? = null,
    val pickerFor: DownloadEntity? = null,
    val memberOf: Set<Long> = emptySet(),
    val message: String? = null,
)

class PlaylistsViewModel(private val services: ServiceLocator) : ViewModel() {

    val storage: StorageManager = services.storage

    private val _state = MutableStateFlow(PlaylistsUiState())
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    val playlists = services.library.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pickable = services.library.observePlayablePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = services.prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun downloadsOf(playlistId: Long): Flow<List<DownloadEntity>> =
        services.library.observePlaylistDownloads(playlistId)

    /** The detail screen calls this on entry; its view model belongs to that navigation entry. */
    fun select(playlistId: Long) {
        if (_state.value.open != playlistId) _state.update { it.copy(open = playlistId) }
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = services.library.createPlaylist(trimmed)
            _state.update {
                it.copy(message = if (id > 0) "Created $trimmed" else "Pick a name first")
            }
        }
    }

    fun askDeletePlaylist(summary: PlaylistSummary) {
        _state.update { it.copy(pendingDeletePlaylist = summary) }
    }

    fun dismissDialogs() {
        _state.update { it.copy(pendingDeletePlaylist = null, pendingDeleteDownload = null) }
    }

    fun confirmDeletePlaylist() {
        val summary = _state.value.pendingDeletePlaylist ?: return
        _state.update { it.copy(pendingDeletePlaylist = null) }
        viewModelScope.launch {
            services.library.deletePlaylist(summary.id)
            if (_state.value.open == summary.id) _state.update { it.copy(open = null) }
            _state.update { it.copy(message = "Deleted ${summary.name}. The files stay in your library.") }
        }
    }

    fun askDeleteDownload(entity: DownloadEntity) {
        _state.update { it.copy(pendingDeleteDownload = entity) }
    }

    fun confirmDeleteDownload() {
        val entity = _state.value.pendingDeleteDownload ?: return
        _state.update { it.copy(pendingDeleteDownload = null) }
        viewModelScope.launch {
            services.library.deleteDownload(entity.id)
            _state.update { it.copy(message = "Deleted ${entity.title.take(40)}") }
        }
    }

    fun removeFromPlaylist(entity: DownloadEntity) {
        val playlistId = _state.value.open ?: return
        viewModelScope.launch {
            services.library.removeFromPlaylist(playlistId, entity.id)
            _state.update { it.copy(message = "Removed from playlist. The file stays on disk.") }
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
        val moved = MediaActions.exportToPublic(
            entity = entity,
            storage = services.storage,
            library = services.library,
            publicFolder = publicFolder,
        )
        _state.update {
            it.copy(message = if (moved != null) "Copied into $publicFolder" else "MediaStore refused the copy")
        }
    }

    fun report(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlaylistsViewModel(this.services) }
        }
    }
}
