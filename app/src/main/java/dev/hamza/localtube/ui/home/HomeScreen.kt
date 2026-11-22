package dev.hamza.localtube.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hamza.localtube.download.ActiveDownload
import dev.hamza.localtube.download.JobStatus
import dev.hamza.localtube.ui.components.EmptyState
import dev.hamza.localtube.ui.components.FormatBadge
import dev.hamza.localtube.ui.components.SectionHeader
import dev.hamza.localtube.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    pendingUrl: String?,
    onPendingConsumed: () -> Unit,
    onOpenDownload: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var sheetPlaylists by remember { mutableStateOf(setOf<Long>()) }

    // Asked once, on the screen where the progress notification is actually used.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPrompt) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(pendingUrl) {
        if (!pendingUrl.isNullOrBlank()) {
            viewModel.submitUrl(pendingUrl)
            onPendingConsumed()
        }
    }

    LaunchedEffect(state.queuedTitle) {
        if (state.queuedTitle != null) {
            delay(2_600)
            viewModel.consumeQueuedNotice()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("YouTube or yt-dlp link") },
                    placeholder = { Text("https://youtu.be/…") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let(viewModel::submitUrl)
                        }) {
                            Text("Paste")
                        }
                    },
                    supportingText = {
                        Text(
                            text = if (viewModel.hasFfmpeg) {
                                "Video + audio merges up to the source resolution."
                            } else {
                                "Slim build: without FFmpeg, 1080p merging and mp3 are unavailable."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = viewModel::fetch,
                        enabled = !state.fetching && state.url.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("  Reading link…")
                        } else {
                            Text("Fetch options")
                        }
                    }
                    FilledTonalButton(onClick = onOpenLibrary) { Text("Library") }
                }
            }
        }

        state.error?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        state.queuedTitle?.let { title ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Queued · $title",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenLibrary) { Text("Library") }
                    }
                }
            }
        }

        if (jobs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        text = "This session",
                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp),
                    )
                    TextButton(onClick = viewModel::clearFinished) { Text("Clear") }
                }
            }

            items(jobs, key = { it.workId }) { job ->
                JobRow(
                    job = job,
                    onCancel = { viewModel.cancelJob(job.workId) },
                    onRetry = { viewModel.retryJob(job.workId) },
                    onDismiss = {
                        sheetPlaylists = emptySet()
                        viewModel.dismissJob(job.workId)
                    },
                    onOpen = { job.downloadId?.let(onOpenDownload) },
                )
            }
        } else if (state.meta == null && !state.fetching) {
            item {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Nothing running",
                    subtitle = "Paste a link above, or share one from the YouTube app into LocalTube. " +
                        "Downloads continue with the screen off and land in the Library when finished.",
                )
            }
        }
    }

    val meta = state.meta
    if (state.sheetOpen && meta != null) {
        val scope = rememberCoroutineScope()
        FormatSheet(
            meta = meta,
            thumbnailPath = state.thumbnailPath,
            defaults = settings,
            hasFfmpeg = viewModel.hasFfmpeg,
            playlists = playlists,
            selectedPlaylistIds = sheetPlaylists,
            onTogglePlaylist = { id ->
                sheetPlaylists = if (id in sheetPlaylists) sheetPlaylists - id else sheetPlaylists + id
            },
            onCreatePlaylist = { name ->
                scope.launch {
                    viewModel.createPlaylist(name)?.let { sheetPlaylists = sheetPlaylists + it }
                }
            },
            onDismiss = {
                viewModel.closeSheet()
                sheetPlaylists = emptySet()
            },
            onConfirm = { mode, quality, audioExt ->
                viewModel.queueDownload(mode, quality, audioExt, sheetPlaylists.toList())
                sheetPlaylists = emptySet()
            },
        )
    }
}

@Composable
private fun JobRow(
    job: ActiveDownload,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FormatBadge(mode = job.args.mode, quality = job.args.quality.label)
            }

            when (job.status) {
                JobStatus.QUEUED -> Text(
                    text = "Waiting for its turn…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                JobStatus.RUNNING -> {
                    LinearProgressIndicator(
                        progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = buildString {
                            append(Fmt.percent(job.percent))
                            job.speed?.let { append(" · $it") }
                            if (job.percent in 0.01f..99.99f) append(" · ETA ${Fmt.eta(job.etaSeconds)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Cancel")
                    }
                }

                JobStatus.DONE -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Saved to library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = onOpen) { Text("Play") }
                }

                JobStatus.FAILED, JobStatus.CANCELLED -> {
                    Text(
                        text = if (job.status == JobStatus.CANCELLED) "Cancelled" else job.message ?: "Failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (job.status == JobStatus.FAILED) {
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Retry")
                            }
                        }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
