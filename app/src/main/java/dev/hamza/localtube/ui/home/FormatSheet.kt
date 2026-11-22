package dev.hamza.localtube.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hamza.localtube.data.local.PlaylistEntity
import dev.hamza.localtube.data.prefs.AppSettings
import dev.hamza.localtube.domain.AudioExt
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import dev.hamza.localtube.engine.FormatSpec
import dev.hamza.localtube.engine.VideoMeta
import dev.hamza.localtube.ui.components.PlaylistPicker
import dev.hamza.localtube.util.Fmt
import java.io.File

/**
 * The one decision screen: mode, quality, audio container and optional playlists.
 *
 * Defaults carry over from the last download, so a repeat visit is a single tap. The thumbnail is
 * read from the local cache (it was downloaded during [HomeViewModel.fetch]) because bundling a Coil
 * network engine just for one preview image is not worth ~0.5 MB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(
    meta: VideoMeta,
    thumbnailPath: String?,
    defaults: AppSettings,
    hasFfmpeg: Boolean,
    playlists: List<PlaylistEntity>,
    selectedPlaylistIds: Set<Long>,
    onTogglePlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (DownloadMode, Quality, AudioExt) -> Unit,
) {
    var mode by remember { mutableStateOf(defaults.defaultMode) }
    var quality by remember { mutableStateOf(defaults.defaultQuality) }
    var audioExt by remember { mutableStateOf(defaults.defaultAudioExt) }

    val qualities = remember(meta.heights) { FormatSpec.offeredQualities(meta.heights) }
    // A stored 1080p default is meaningless for a 480p source.
    val effectiveQuality = when {
        mode == DownloadMode.AUDIO_ONLY -> Quality.BEST
        else -> qualities.firstOrNull { it == quality } ?: Quality.BEST
    }
    val plan = FormatSpec.plan(mode, effectiveQuality, audioExt, hasFfmpeg)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(16 / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val local = thumbnailPath?.let(::File)
                    if (local != null && local.exists()) {
                        AsyncImage(
                            model = local,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = meta.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            meta.channel,
                            if (meta.durationSec > 0) Fmt.duration(meta.durationSec) else "live/short",
                            meta.heights.maxOrNull()?.let { "up to ${it}p" },
                            meta.approxBytes.takeIf { it > 0 }?.let { Fmt.bytes(it) },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider()

            Text(text = "Save as", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DownloadMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { mode = option },
                        shape = SegmentedButtonDefaults.itemShape(index, DownloadMode.entries.size),
                        label = {
                            Text(
                                text = option.label.substringBefore(" ("),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            if (mode == DownloadMode.AUDIO_ONLY) {
                LabeledChips(
                    label = "Audio format",
                    options = AudioExt.entries.map { it.label },
                    selectedIndex = AudioExt.entries.indexOf(audioExt),
                    onSelect = { audioExt = AudioExt.entries[it] },
                )
            } else {
                LabeledChips(
                    label = "Quality",
                    options = qualities.map { it.label },
                    selectedIndex = qualities.indexOf(effectiveQuality),
                    onSelect = { quality = qualities[it] },
                )
            }

            if (plan.degraded) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (hasFfmpeg) {
                            "This selection exceeds what the source offers; yt-dlp will take the best match."
                        } else {
                            "Slim build (no FFmpeg): cannot merge separate streams, so quality is capped at " +
                                "the progressive 720p mix and mp3 is not re-encoded."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            PlaylistPicker(
                playlists = playlists,
                selectedIds = selectedPlaylistIds,
                onToggle = onTogglePlaylist,
                onCreate = onCreatePlaylist,
            )

            Button(
                onClick = { onConfirm(mode, effectiveQuality, audioExt) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = when {
                        selectedPlaylistIds.isEmpty() -> "Start download"
                        else -> "Download and add to ${selectedPlaylistIds.size} playlist" +
                            if (selectedPlaylistIds.size == 1) "" else "s"
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledChips(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    label = { Text(option) },
                )
            }
        }
    }
}
