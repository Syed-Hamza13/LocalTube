package dev.hamza.localtube.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.hamza.localtube.data.local.PlaylistSummary
import dev.hamza.localtube.ui.components.ConfirmDialog
import dev.hamza.localtube.ui.components.DownloadMenu
import dev.hamza.localtube.ui.components.DownloadRow
import dev.hamza.localtube.ui.components.EmptyState
import dev.hamza.localtube.ui.components.MessageBanner
import dev.hamza.localtube.ui.components.PlaylistPicker
import dev.hamza.localtube.ui.components.SectionHeader
import dev.hamza.localtube.ui.icons.PlaylistPlay
import dev.hamza.localtube.util.Fmt
import dev.hamza.localtube.util.MediaActions
import java.io.File
import kotlinx.coroutines.delay

/** Auto-clearing notice, shared by both playlist screens. */
@Composable
private fun AutoDismissMessage(message: String?, onConsumed: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_600)
            onConsumed()
        }
    }
}

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    AutoDismissMessage(state.message, viewModel::consumeMessage)

    Column(modifier = modifier.fillMaxSize()) {
        SectionHeader(text = "Your playlists", modifier = Modifier.padding(horizontal = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                placeholder = { Text("New playlist name") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    viewModel.create(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) { Icon(Icons.Default.Add, contentDescription = "Create playlist") }
        }

        MessageBanner(state.message)

        if (playlists.isEmpty()) {
            EmptyState(
                icon = PlaylistPlay,
                title = "No playlists yet",
                subtitle = "Group downloads into series — lectures, music, one channel you follow. A video " +
                    "can sit in several playlists, and deleting a playlist never deletes a file.",
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(playlists, key = { it.id }) { summary ->
                    PlaylistRow(
                        summary = summary,
                        onOpen = { onOpenPlaylist(summary.id) },
                        onDelete = { viewModel.askDeletePlaylist(summary) },
                    )
                }
            }
        }
    }

    state.pendingDeletePlaylist?.let { summary ->
        ConfirmDialog(
            title = "Delete “${summary.name}”?",
            message = "The ${summary.itemCount} ${if (summary.itemCount == 1) "video" else "videos"} inside " +
                "stay in your library and on this device.",
            confirmLabel = "Delete playlist",
            destructive = true,
            onConfirm = viewModel::confirmDeletePlaylist,
            onDismiss = viewModel::dismissDialogs,
        )
    }
}

@Composable
private fun PlaylistRow(summary: PlaylistSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(summary.coverPath)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${summary.itemCount} ${if (summary.itemCount == 1) "video" else "videos"} · " +
                    Fmt.bytes(summary.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${summary.name}")
        }
    }
}

@Composable
private fun Cover(path: String?) {
    Box(
        modifier = Modifier
            .size(width = 86.dp, height = 50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val file = remember(path) {
            path?.takeIf { !it.startsWith("content://") }?.let(::File)?.takeIf { it.exists() }
        }
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlay: (playlistId: Long, downloadId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val pickable by viewModel.pickable.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.select(playlistId) }
    AutoDismissMessage(state.message, viewModel::consumeMessage)

    val downloads by remember(playlistId) { viewModel.downloadsOf(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val name = playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${downloads.size} ${if (downloads.size == 1) "video" else "videos"} · " +
                        Fmt.bytes(downloads.sumOf { it.sizeBytes }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloads.isNotEmpty()) {
                OutlinedButton(onClick = { onPlay(playlistId, downloads.first().id) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" Play all")
                }
            }
        }

        MessageBanner(state.message)

        if (downloads.isEmpty()) {
            EmptyState(
                icon = PlaylistPlay,
                title = "Nothing in here yet",
                subtitle = "Open a download's menu in the library and choose “Add to playlist”, or tick this " +
                    "playlist in the sheet just before you download.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 28.dp),
            ) {
                items(downloads, key = { it.id }) { entity ->
                    DownloadRow(
                        entity = entity,
                        menu = DownloadMenu(
                            onPlaylists = viewModel::askPlaylists,
                            onOpenWith = {
                                if (!MediaActions.openExternally(context, viewModel.storage, it)) {
                                    viewModel.report("No other app can open this file.")
                                }
                            },
                            onShare = {
                                if (!MediaActions.share(context, viewModel.storage, it)) {
                                    viewModel.report("Sharing is not available for this file.")
                                }
                            },
                            onExport = { viewModel.exportToPublic(it, settings.publicFolder) },
                            onDelete = viewModel::askDeleteDownload,
                            onRemoveFromList = viewModel::removeFromPlaylist,
                        ),
                        onPlay = { onPlay(playlistId, entity.id) },
                    )
                }
            }
        }
    }

    state.pendingDeleteDownload?.let { entity ->
        ConfirmDialog(
            title = "Delete download?",
            message = "“${entity.title}” is removed from this device and from every playlist.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDeleteDownload,
            onDismiss = viewModel::dismissDialogs,
        )
    }

    val pickerFor = state.pickerFor
    if (pickerFor != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPlaylists,
            title = {
                Text(pickerFor.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column {
                    PlaylistPicker(
                        playlists = pickable,
                        selectedIds = state.memberOf,
                        onToggle = viewModel::toggleMembership,
                        onCreate = viewModel::createAndAdd,
                        label = "Tap to add or remove",
                    )
                    Spacer(Modifier.height(8.dp))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissPlaylists) { Text("Done") } },
        )
    }
}
