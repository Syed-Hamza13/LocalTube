package dev.hamza.localtube.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import dev.hamza.localtube.domain.StorageMode
import dev.hamza.localtube.domain.ThemeMode
import dev.hamza.localtube.ui.components.MessageBanner
import dev.hamza.localtube.util.Fmt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.consumeMessage()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { MessageBanner(state.message, modifier = Modifier.padding(horizontal = 0.dp)) }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where videos are kept", style = MaterialTheme.typography.titleSmall)
                    StorageOption(
                        selected = settings.storageMode == StorageMode.APP_PRIVATE,
                        title = "Private to LocalTube (recommended)",
                        subtitle = "Android/data/dev.hamza.localtube/files/Movies/LocalTube — no permission " +
                            "is ever requested, and nothing is copied. Uninstalling removes the files.",
                        onClick = { viewModel.setStorage(StorageMode.APP_PRIVATE, settings.publicFolder) },
                    )
                    StorageOption(
                        selected = settings.storageMode == StorageMode.PUBLIC_MOVIES,
                        title = "Public Movies and Music folders",
                        subtitle = "Visible in the Files app and to other players. Each finished download is " +
                            "copied once into MediaStore (video → Movies, audio → Music).",
                        onClick = { viewModel.setStorage(StorageMode.PUBLIC_MOVIES, settings.publicFolder) },
                    )
                    if (settings.storageMode == StorageMode.PUBLIC_MOVIES) {
                        OutlinedTextField(
                            value = settings.publicFolder,
                            onValueChange = { viewModel.setStorage(StorageMode.PUBLIC_MOVIES, it) },
                            singleLine = true,
                            label = { Text("Folder name") },
                            supportingText = { Text("Used as Movies/<folder> and Music/<folder>") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        settings.storageMode == StorageMode.PUBLIC_MOVIES
                    ) {
                        Text(
                            text = "Public folders need Android 10 or newer — files stay private for now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${Fmt.bytes(state.appBytes)} used on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = viewModel::clearThumbnails, enabled = !state.busy) {
                            Text("Clear previews")
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                    ChipRow(
                        label = "Theme",
                        options = ThemeMode.entries.map { it.name to it.label() },
                        selected = settings.themeMode.name,
                        onSelect = { key -> viewModel.setTheme(ThemeMode.valueOf(key)) },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Use wallpaper colours",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Defaults for new downloads", style = MaterialTheme.typography.titleSmall)
                    ChipRow(
                        label = "Kind",
                        options = DownloadMode.entries.map { it.key to it.label },
                        selected = settings.defaultMode.key,
                        onSelect = { key ->
                            viewModel.setDefaults(
                                mode = DownloadMode.fromKey(key),
                                quality = settings.defaultQuality,
                                audioExt = settings.defaultAudioExt,
                            )
                        },
                    )
                    ChipRow(
                        label = "Max height",
                        options = Quality.entries.map { it.key to it.label },
                        selected = settings.defaultQuality.key,
                        onSelect = { key ->
                            viewModel.setDefaults(
                                mode = settings.defaultMode,
                                quality = Quality.fromKey(key),
                                audioExt = settings.defaultAudioExt,
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Audio",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        AudioExt.entries.forEach { ext ->
                            FilterChip(
                                selected = settings.defaultAudioExt == ext,
                                onClick = {
                                    viewModel.setDefaults(
                                        mode = settings.defaultMode,
                                        quality = settings.defaultQuality,
                                        audioExt = ext,
                                    )
                                },
                                label = { Text(ext.label) },
                                enabled = viewModel.hasFfmpeg || ext == AudioExt.M4A,
                            )
                        }
                    }
                    if (!viewModel.hasFfmpeg) {
                        Text(
                            text = "This is the slim build: no FFmpeg, so streams are not merged and mp3 " +
                                "is unavailable. Install the full build for 1080p and mp3.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Engine", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = state.engineInfo ?: "yt-dlp and Python 3 ship inside the APK and start on the " +
                            "first download. Nothing is uploaded anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::checkEngine, enabled = !state.engineChecking) {
                            if (state.engineChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text("  Starting…")
                            } else {
                                Text("Check yt-dlp")
                            }
                        }
                        Text(
                            text = when {
                                viewModel.hasFfmpeg -> "FFmpeg: bundled"
                                viewModel.ffmpegNote != null -> "FFmpeg: bundled but not running"
                                else -> "FFmpeg: not included"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // A full build that lost FFmpeg still downloads, just without merging or mp3.
                    viewModel.ffmpegNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        state.crash?.let { report ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Last crash", style = MaterialTheme.typography.titleSmall)
                        // Selectable so the trace can be copied out with no PC attached.
                        SelectionContainer {
                            Text(
                                text = report,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 24,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = viewModel::clearCrash) { Text("Clear") }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "LocalTube ${viewModel.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Permissions: internet, network state, wake lock and notifications. No storage " +
                            "permission, no account, no analytics, no ads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    TextButton(onClick = viewModel::refreshUsage) { Text("Re-measure disk use") }
                }
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun StorageOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
        options.chunked(3).forEach { rowItems ->
            // Hand-rolled wrapping: the flexbox artefact is not worth it for a handful of chips.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { (key, text) ->
                    FilterChip(
                        selected = key == selected,
                        onClick = { onSelect(key) },
                        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

/** First launch: the user picks the storage location here instead of being silently defaulted. */
@Composable
fun FirstRunDialog(
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(StorageMode.APP_PRIVATE) }
    var folder by remember { mutableStateOf(SettingsViewModel.DEFAULT_FOLDER) }

    AlertDialog(
        onDismissRequest = { viewModel.completeOnboarding(settings.storageMode, settings.publicFolder) },
        title = { Text("Where should videos be saved?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LocalTube never asks for storage permission. You can change this any time in " +
                        "Settings → Where videos are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StorageOption(
                    selected = mode == StorageMode.APP_PRIVATE,
                    title = "Private app folder (fastest, recommended)",
                    subtitle = "internal Movies/LocalTube · no copy, no permission",
                    onClick = { mode = StorageMode.APP_PRIVATE },
                )
                StorageOption(
                    selected = mode == StorageMode.PUBLIC_MOVIES,
                    title = "Public Movies / Music folders",
                    subtitle = "visible to other apps · one extra copy per download",
                    onClick = { mode = StorageMode.PUBLIC_MOVIES },
                )
                if (mode == StorageMode.PUBLIC_MOVIES) {
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        singleLine = true,
                        label = { Text("Folder name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.completeOnboarding(mode, folder) }) { Text("Start") }
        },
        shape = RoundedCornerShape(20.dp),
    )
}
