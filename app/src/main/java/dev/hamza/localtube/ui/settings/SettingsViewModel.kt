package dev.hamza.localtube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hamza.localtube.BuildConfig
import dev.hamza.localtube.data.prefs.AppSettings
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import dev.hamza.localtube.domain.StorageMode
import dev.hamza.localtube.domain.ThemeMode
import dev.hamza.localtube.util.CrashReporter
import dev.hamza.localtube.util.services
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val appBytes: Long = 0L,
    val engineInfo: String? = null,
    val engineChecking: Boolean = false,
    val message: String? = null,
    val crash: String? = null,
)

class SettingsViewModel(private val services: ServiceLocator) : ViewModel() {

    val settings = services.prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val hasFfmpeg: Boolean get() = services.engine.hasFfmpeg

    /** Non-null only in a full build where FFmpeg failed to start; explains the degraded formats. */
    val ffmpegNote: String? get() = services.engine.ffmpegNote

    val appVersion: String get() = BuildConfig.VERSION_NAME

    init {
        refreshUsage()
        refreshCrash()
    }

    fun refreshUsage() = viewModelScope.launch {
        val bytes = services.storage.appMediaBytes()
        _state.update { it.copy(appBytes = bytes) }
    }

    /** The trace of the last crash, so a phone with no PC attached can still report one. */
    fun refreshCrash() = viewModelScope.launch {
        val report = withContext(Dispatchers.IO) { CrashReporter.read(services.appContext) }
        _state.update { it.copy(crash = report) }
    }

    fun clearCrash() = viewModelScope.launch {
        withContext(Dispatchers.IO) { CrashReporter.clear(services.appContext) }
        _state.update { it.copy(crash = null) }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { services.prefs.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        services.prefs.setDynamicColor(enabled)
    }

    /** Also used by the first-run dialog, which decides both the location and the folder in one go. */
    fun setStorage(mode: StorageMode, folder: String) = viewModelScope.launch {
        services.prefs.setStorage(mode, folder.trim().ifBlank { DEFAULT_FOLDER })
        if (mode == StorageMode.PUBLIC_MOVIES && !services.storage.publicModeAvailable()) {
            _state.update { it.copy(message = "Android 10 or newer is needed for public folders; files stay private.") }
        }
        refreshUsage()
    }

    fun setDefaults(mode: DownloadMode, quality: Quality, audioExt: AudioExt) =
        viewModelScope.launch { services.prefs.setDefaults(mode, quality, audioExt) }

    fun completeOnboarding(mode: StorageMode, folder: String) = viewModelScope.launch {
        services.prefs.setStorage(mode, folder.trim().ifBlank { DEFAULT_FOLDER })
        services.prefs.setOnboarded(true)
    }

    fun clearThumbnails() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        services.storage.clearThumbnailCache()
        val bytes = services.storage.appMediaBytes()
        _state.update { it.copy(busy = false, message = "Thumbnail cache cleared.", appBytes = bytes) }
    }

    /** Initialises the bundled yt-dlp on demand, since that is what unpacks Python on first use. */
    fun checkEngine() = viewModelScope.launch {
        _state.update { it.copy(engineChecking = true, engineInfo = null) }
        val info = runCatching {
            withContext(Dispatchers.IO) {
                services.engine.ensureReady()
                services.engine.engineVersion()
            }
        }.getOrElse { error -> "Failed: ${error.message ?: "unknown error"}" }
        _state.update { it.copy(engineChecking = false, engineInfo = info ?: "Ready") }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        const val DEFAULT_FOLDER = "LocalTube"

        val Factory = viewModelFactory {
            initializer { SettingsViewModel(this.services) }
        }
    }
}
