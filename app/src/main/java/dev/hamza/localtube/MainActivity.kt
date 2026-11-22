package dev.hamza.localtube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hamza.localtube.ui.navigation.LocalTubeRoot
import dev.hamza.localtube.ui.settings.SettingsViewModel
import dev.hamza.localtube.ui.theme.LocalTubeTheme
import dev.hamza.localtube.util.Intents

/**
 * Single activity: the manifest lets it handle orientation/screen-size changes itself, so rotating
 * during playback never rebuilds the player.
 */
class MainActivity : ComponentActivity() {

    /** A link handed over by the share sheet or a `VIEW` intent, waiting for the Home screen. */
    private var pendingUrl by mutableStateOf<String?>(null)
    private var startTab by mutableStateOf<String?>(null)

    private val services get() = (application as LocalTubeApp).services

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            LocalTubeTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                LocalTubeRoot(
                    pendingUrl = pendingUrl,
                    onPendingConsumed = { pendingUrl = null },
                    startTab = startTab,
                    onStartTabConsumed = { startTab = null },
                    settingsViewModel = settingsViewModel,
                )
            }
        }
    }

    /** The activity is `singleTask`, so a second share arrives here rather than in a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(intent: Intent?) {
        intent ?: return

        intent.getStringExtra(EXTRA_TAB)?.let { startTab = it }

        when (intent.action) {
            Intents.ACTION_CANCEL_JOB -> {
                val workId = intent.getStringExtra(Intents.EXTRA_WORK_ID)
                if (workId != null) services.scheduler.cancel(workId)
            }

            Intent.ACTION_SEND -> {
                val payload = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                payload?.let { shared -> pendingUrl = shared.extractUrl() }
            }

            Intent.ACTION_VIEW -> {
                intent.dataString?.let { pendingUrl = it }
            }
        }
    }

    companion object {
        private const val EXTRA_TAB = "nav_tab"

        /** Shared text is usually "Title\nhttps://youtu.be/…", so pull out the first link. */
        private val URL_IN_TEXT = Regex("https?://\\S+")

        fun String.extractUrl(): String? =
            trimmed().let { text -> if (text.startsWith("http")) text else URL_IN_TEXT.find(text)?.value }
    }
}
