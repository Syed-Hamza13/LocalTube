package dev.hamza.localtube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hamza.localtube.data.local.PlaylistEntity

/**
 * Playlist chips plus inline creation. Reused by the download sheet (multi-select, applied when the
 * file finishes) and by the library menu (applies immediately).
 */
@Composable
fun PlaylistPicker(
    playlists: List<PlaylistEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Add to playlist",
) {
    var showField by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
            TextButton(onClick = { showField = !showField }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(if (showField) "Close" else "New")
            }
        }

        if (showField) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onCreate(draft.trim())
                            draft = ""
                            showField = false
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Save") }
            }
        }

        if (playlists.isEmpty() && !showField) {
            Text(
                text = "No playlists yet — tap New to create one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        } else if (playlists.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    FilterChip(
                        selected = playlist.id in selectedIds,
                        onClick = { onToggle(playlist.id) },
                        label = { Text(playlist.name, maxLines = 1) },
                    )
                }
            }
        }
    }
}

/** Non-scrolling variant used inside dialogs where the parent already scrolls. */
@Composable
fun PlaylistChipRow(
    playlists: List<PlaylistEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        playlists.forEach { playlist ->
            FilterChip(
                selected = playlist.id in selectedIds,
                onClick = { onToggle(playlist.id) },
                label = { Text(playlist.name, maxLines = 1) },
            )
        }
    }
}
