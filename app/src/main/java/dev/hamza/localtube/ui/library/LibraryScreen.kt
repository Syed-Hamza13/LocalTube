package dev.hamza.localtube.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hamza.localtube.ui.components.ConfirmDialog
import dev.hamza.localtube.ui.components.DownloadMenu
import dev.hamza.localtube.ui.components.DownloadRow
import dev.hamza.localtube.ui.components.DownloadTile
import dev.hamza.localtube.ui.components.EmptyState
import dev.hamza.localtube.ui.components.LiveRow
import dev.hamza.localtube.ui.components.PlaylistPicker
import dev.hamza.localtube.ui.components.SectionHeader
import dev.hamza.localtube.ui.icons.Download
import dev.hamza.localtube.ui.icons.GridView
import dev.hamza.localtube.ui.icons.RowsView
import dev.hamza.localtube.download.JobStatus
import dev.hamza.localtube.util.Fmt
import dev.hamza.localtube.util.MediaActions
import dev.hamza.localtube.util.groupByBucket
import kotlinx.coroutines.delay

/**
 * The offline gallery: everything on disk grouped by day, searchable without a network, and one tap
 * away from the built-in player. Long-press a row to file it into playlists.
 */
@Composable
fun LibraryScreen(
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var grid by rememberSaveable { mutableStateOf(false) }
    val live = jobs.filter { it.isLive || it.status == JobStatus.DONE }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2_600)
            viewModel.consumeMessage()
        }
    }

    val menu = DownloadMenu(
        onPlaylists = viewModel::askPlaylists,
        onOpenWith = { entity ->
            if (!MediaActions.openExternally(context, viewModel.storage, entity)) {
                viewModel.report("No other app can open this file.")
            }
        },
        onShare = { entity ->
            if (!MediaActions.share(context, viewModel.storage, entity)) {
                viewModel.report("Sharing is not available for this file.")
            }
        },
        onExport = { entity -> viewModel.exportToPublic(entity, settings.publicFolder) },
        onDelete = viewModel::askDelete,
    )

    val groups = remember(items) { items.groupByBucket { it.createdAt } }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    placeholder = { Text("Search downloaded titles") },
                )
                IconButton(onClick = { grid = !grid }) {
                    Icon(
                        imageVector = if (grid) RowsView else GridView,
                        contentDescription = if (grid) "Switch to list" else "Switch to grid",
                    )
                }
            }
            Text(
                text = buildString {
                    append(stats.count)
                    append(if (stats.count == 1) " file · " else " files · ")
                    append(Fmt.bytes(stats.totalBytes))
                    val running = jobs.count { it.isLive }
                    if (running > 0) append(" · $running running")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        state.message?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        val full = items.isEmpty() && live.isEmpty()
        when {
            full -> EmptyState(
                icon = Download,
                title = if (state.query.isBlank()) "Library is empty" else "Nothing matches “${state.query}”",
                subtitle = if (state.query.isBlank()) {
                    "Finished downloads land here, grouped by day. Everything plays offline — no " +
                        "network and no account needed."
                } else {
                    "Search reads the titles stored when each download finished."
                },
                modifier = Modifier.padding(top = 32.dp),
            )

            grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (live.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(text = "Downloading") }
                    live.forEach { job ->
                        item(key = "job-${job.workId}", span = { GridItemSpan(maxLineSpan) }) {
                            LiveRow(
                                job = job,
                                onCancel = { viewModel.cancelJob(job.workId) },
                                onDismiss = { viewModel.dismissJob(job.workId) },
                                onPlay = { job.downloadId?.let(onPlay) },
                            )
                        }
                    }
                }
                groups.forEach { group ->
                    item(key = "h-${group.bucket.label}", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(text = group.bucket.label)
                    }
                    gridItems(
                        items = group.items,
                        key = { it.id },
                    ) { entity ->
                        DownloadTile(
                            entity = entity,
                            menu = menu,
                            onPlay = { onPlay(entity.id) },
                        )
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            ) {
                if (live.isNotEmpty()) {
                    item { SectionHeader(text = "Downloading") }
                    items(live, key = { "job-" + it.workId }) { job ->
                        LiveRow(
                            job = job,
                            onCancel = { viewModel.cancelJob(job.workId) },
                            onDismiss = { viewModel.dismissJob(job.workId) },
                            onPlay = { job.downloadId?.let(onPlay) },
                        )
                    }
                }
                groups.forEach { group ->
                    item(key = "h-${group.bucket.label}") { SectionHeader(text = group.bucket.label) }
                    items(group.items, key = { it.id }) { entity ->
                        DownloadRow(
                            entity = entity,
                            menu = menu,
                            onPlay = { onPlay(entity.id) },
                        )
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { entity ->
        ConfirmDialog(
            title = "Delete download?",
            message = "“${entity.title}” and its ${Fmt.bytes(entity.sizeBytes)} are removed from this " +
                "device, together with the library entry. This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

    if (state.pickerFor != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPlaylists,
            title = { Text("Playlists", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    PlaylistPicker(
                        playlists = playlists,
                        selectedIds = state.memberOf,
                        onToggle = viewModel::toggleMembership,
                        onCreate = viewModel::createAndAdd,
                        label = if (playlists.isEmpty()) {
                            "Create your first playlist"
                        } else {
                            "Tap to add or remove"
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Changes apply immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissPlaylists) { Text("Done") } },
        )
    }
}
