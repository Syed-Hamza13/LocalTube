package dev.hamza.localtube.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import dev.hamza.localtube.domain.StorageMode
import dev.hamza.localtube.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "localtube")

data class AppSettings(
    val onboarded: Boolean = false,
    val storageMode: StorageMode = StorageMode.APP_PRIVATE,
    val publicFolder: String = "PublicFolder",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultMode: DownloadMode = DownloadMode.VIDEO_AUDIO,
    val defaultQuality: Quality = Quality.BEST,
    val defaultAudioExt: AudioExt = AudioExt.M4A,
)

class AppPrefs(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val PUBLIC_FOLDER = stringPreferencesKey("public_folder")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_AUDIO_EXT = stringPreferencesKey("default_audio_ext")
        val QUEUE = stringPreferencesKey("download_queue")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map(::toSettings)

    /** One-shot read for code that cannot collect a flow (worker, engine init). */
    suspend fun snapshot(): AppSettings = toSettings(store.data.first())

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }

    suspend fun setStorage(mode: StorageMode, folder: String) = edit {
        it[Keys.STORAGE_MODE] = mode.name
        it[Keys.PUBLIC_FOLDER] = folder.ifBlank { DEFAULT_FOLDER }
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setDefaults(mode: DownloadMode, quality: Quality, audioExt: AudioExt) = edit {
        it[Keys.DEFAULT_MODE] = mode.key
        it[Keys.DEFAULT_QUALITY] = quality.key
        it[Keys.DEFAULT_AUDIO_EXT] = audioExt.key
    }

    /**
     * Opaque download queue owned by [dev.hamza.localtube.download.DownloadScheduler]. WorkManager
     * keeps the work itself; this is only what the app needs to label those jobs again after a kill.
     */
    suspend fun queue(): String = store.data.first()[Keys.QUEUE].orEmpty()

    suspend fun setQueue(raw: String) = edit { it[Keys.QUEUE] = raw }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun toSettings(prefs: Preferences) = AppSettings(
        onboarded = prefs[Keys.ONBOARDED] ?: false,
        storageMode = StorageMode.fromKey(prefs[Keys.STORAGE_MODE]),
        publicFolder = prefs[Keys.PUBLIC_FOLDER] ?: DEFAULT_FOLDER,
        themeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
        defaultMode = DownloadMode.fromKey(prefs[Keys.DEFAULT_MODE]),
        defaultQuality = Quality.fromKey(prefs[Keys.DEFAULT_QUALITY]),
        defaultAudioExt = AudioExt.fromKey(prefs[Keys.DEFAULT_AUDIO_EXT]),
    )

    companion object {
        const val DEFAULT_FOLDER = "LocalTube"
    }
}
