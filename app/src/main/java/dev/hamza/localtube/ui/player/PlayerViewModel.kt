package dev.hamza.localtube.ui.player

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.util.appContext
import dev.hamza.localtube.util.services
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerUiState(
    val loading: Boolean = true,
    val queue: List<DownloadEntity> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val muted: Boolean = false,
    /** Device media volume, 0f..1f of the stream maximum. */
    val volume: Float = 0f,
    val error: String? = null,
) {
    val current: DownloadEntity? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex
    val hasPrevious: Boolean get() = index > 0
}

/**
 * Wraps one ExoPlayer instance for the fullscreen screen.
 *
 * Hardware decoding plus `WAKE_MODE_LOCAL` keeps the drain low: the CPU stays out of the picture and
 * no network radio is held. The player lives here rather than in the composable so that rotating the
 * device (the activity handles the config change) never restarts playback.
 */
class PlayerViewModel(
    private val services: ServiceLocator,
    context: Context,
) : ViewModel() {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var pendingSurface: Surface? = null
    private var started = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            _state.update {
                it.copy(
                    index = player.currentMediaItemIndex,
                    durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = humanize(error)) }
        }
    }

    /** Idempotent: the screen calls this once per entry, even after recomposition. */
    fun start(scopePlaylistId: Long, downloadId: Long, startAtMs: Long) {
        if (started) return
        started = true
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) { buildQueue(scopePlaylistId, downloadId) }
            if (queue.isEmpty()) {
                _state.update { it.copy(loading = false, error = "That file is not in the library any more.") }
                return@launch
            }
            val index = queue.indexOfFirst { it.id == downloadId }.coerceAtLeast(0)
            _state.update { it.copy(loading = false, queue = queue, index = index, error = null) }
            val engine = ensurePlayer()
            engine.setMediaItems(queue.map { it.toMediaItem() }, index, startAtMs.coerceAtLeast(0L))
            engine.prepare()
            engine.playWhenReady = true
        }
    }

    private suspend fun buildQueue(scopePlaylistId: Long, downloadId: Long): List<DownloadEntity> {
        val fromScope = if (scopePlaylistId > 0) {
            services.library.playlistDownloads(scopePlaylistId)
        } else {
            services.library.snapshotDownloads()
        }
        if (fromScope.any { it.id == downloadId }) return fromScope
        // The row may have been deleted or moved since navigation: fall back to the single file.
        return listOfNotNull(services.library.find(downloadId))
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val created = ExoPlayer.Builder(appContext).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(listener)
        }
        player = created
        pendingSurface?.let(created::setVideoSurface)
        return created
    }

    /**
     * Called from the `SurfaceHolder.Callback`, which may fire before or after the player exists, so
     * both sides check for the other.
     */
    fun setSurface(surface: Surface?) {
        pendingSurface = surface
        player?.setVideoSurface(surface)
    }

    fun togglePlay() {
        val engine = player ?: return
        if (engine.isPlaying) engine.pause() else {
            if (engine.playbackState == Player.STATE_ENDED) engine.seekTo(0L)
            engine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skip(seconds: Int) {
        val engine = player ?: return
        val target = (engine.currentPosition + seconds * 1000L).coerceAtLeast(0L)
        engine.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun next() {
        player?.seekToNextMediaItem()
    }

    fun previous() {
        val engine = player ?: return
        // Standard player behaviour: restart the current item unless it is already near the start.
        if (engine.currentPosition > 4_000L) engine.seekTo(0L) else engine.seekToPreviousMediaItem()
    }

    fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        player?.volume = if (muted) 0f else 1f
        _state.update { it.copy(muted = muted) }
    }

    fun setVolume(value: Float) {
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return
        val target = (value.coerceIn(0f, 1f) * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _state.update { it.copy(volume = value, muted = value <= 0f) }
    }

    fun stop() {
        runCatching { player?.stop() }
        _state.update { it.copy(isPlaying = false) }
    }

    /** Pulls the current position into the state; the screen drives this a few times a second. */
    fun refreshPosition() {
        val engine = player ?: return
        _state.update {
            it.copy(
                positionMs = engine.currentPosition.coerceAtLeast(0L),
                durationMs = engine.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                bufferedMs = engine.bufferedPosition.coerceAtLeast(0L),
                volume = readVolume(),
            )
        }
    }

    private fun readVolume(): Float {
        val manager = audioManager ?: return 0f
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max <= 0) 0f else manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun DownloadEntity.toMediaItem(): MediaItem {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(File(filePath))
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channel)
                    .build(),
            )
            .build()
    }

    private fun humanize(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "The file is gone or unreadable — it may have been deleted or moved outside the app."

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "This device cannot decode that container. Try “Open with” a system player."

        else -> error.errorCodeName
    }

    override fun onCleared() {
        runCatching {
            player?.removeListener(listener)
            player?.setVideoSurface(null)
            player?.release()
        }
        player = null
        pendingSurface = null
        super.onCleared()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlayerViewModel(this.services, this.appContext) }
        }
    }
}
