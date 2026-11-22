package dev.hamza.localtube.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.download.ActiveDownload
import dev.hamza.localtube.download.JobStatus
import dev.hamza.localtube.ui.icons.Download
import dev.hamza.localtube.ui.icons.PlaylistAdd
import dev.hamza.localtube.util.Fmt

/** The actions behind the overflow button; one instance is shared by every row on a screen. */
data class DownloadMenu(
    val onPlaylists: (DownloadEntity) -> Unit,
    val onOpenWith: (DownloadEntity) -> Unit,
    val onShare: (DownloadEntity) -> Unit,
    val onExport: (DownloadEntity) -> Unit,
    val onDelete: (DownloadEntity) -> Unit,
    /** Only set inside a playlist, where "take it out of this list" is a different thing from deleting. */
    val onRemoveFromList: ((DownloadEntity) -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadRow(
    entity: DownloadEntity,
    menu: DownloadMenu,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = { menu.onPlaylists(entity) },
) {
    val mode = DownloadMode.fromKey(entity.mode)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Thumbnail(
            entity = entity,
            modifier = Modifier
                .width(132.dp)
                .height(74.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entity.channel?.takeIf { it.isNotBlank() } ?: Fmt.dateTime(entity.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FormatBadge(mode = mode, quality = entity.quality)
                Text(
                    text = Fmt.bytes(entity.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entity.filePath.startsWith("content://")) PublicTag()
            }
        }
        OverflowButton(entity = entity, menu = menu, onPlay = onPlay)
    }
}

/** Two-column card used by the library grid and by playlist details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadTile(
    entity: DownloadEntity,
    menu: DownloadMenu,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = DownloadMode.fromKey(entity.mode)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = onPlay,
                onLongClick = { menu.onPlaylists(entity) },
            ),
        ) {
            Thumbnail(
                entity = entity,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    OverflowButton(entity = entity, menu = menu, onPlay = onPlay)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FormatBadge(mode = mode, quality = entity.quality)
                    Text(
                        text = Fmt.bytes(entity.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowButton(entity: DownloadEntity, menu: DownloadMenu, onPlay: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${entity.title}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Item(Icons.Default.PlayArrow, "Play here") {
                open = false
                onPlay()
            }
            Item(PlaylistAdd, "Add to playlist") {
                open = false
                menu.onPlaylists(entity)
            }
            Item(Icons.Default.Share, "Share file") {
                open = false
                menu.onShare(entity)
            }
            val remove = menu.onRemoveFromList
            if (remove != null) {
                Item(Icons.Default.Close, "Remove from this list") {
                    open = false
                    remove(entity)
                }
            }
            if (!entity.filePath.startsWith("content://")) {
                Item(Download, "Copy to Movies/Music") {
                    open = false
                    menu.onExport(entity)
                }
            }
            Item(Icons.Default.ExitToApp, "Open with…") {
                open = false
                menu.onOpenWith(entity)
            }
            Item(Icons.Default.Delete, "Delete") {
                open = false
                menu.onDelete(entity)
            }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun PublicTag(modifier: Modifier = Modifier) {
    Text(
        text = "Public",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Compact progress card for a job that is queued or running right now. */
@Composable
fun LiveRow(
    job: ActiveDownload,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (job.status) {
                        JobStatus.QUEUED -> "Queued"
                        JobStatus.RUNNING -> buildString {
                            append(Fmt.percent(job.percent))
                            job.speed?.let { append(" · $it") }
                            if (job.percent in 0.01f..99.99f) append(" · ETA ${Fmt.eta(job.etaSeconds)}")
                        }

                        JobStatus.DONE -> "Saved to library"
                        JobStatus.CANCELLED -> "Cancelled"
                        JobStatus.FAILED -> job.message ?: "Failed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (job.status) {
                        JobStatus.FAILED, JobStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (job.status == JobStatus.RUNNING) {
                    LinearProgressIndicator(
                        progress = { (job.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            when (job.status) {
                JobStatus.RUNNING, JobStatus.QUEUED -> TextButton(onClick = onCancel) { Text("Cancel") }
                JobStatus.DONE -> TextButton(onClick = onPlay) { Text("Play") }
                else -> TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
