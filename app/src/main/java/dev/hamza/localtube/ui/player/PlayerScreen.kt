package dev.hamza.localtube.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hamza.localtube.ui.icons.Forward
import dev.hamza.localtube.ui.icons.Lock
import dev.hamza.localtube.ui.icons.Pause
import dev.hamza.localtube.ui.icons.Play
import dev.hamza.localtube.ui.icons.Rewind
import dev.hamza.localtube.ui.icons.SkipNext
import dev.hamza.localtube.ui.icons.SkipPrevious
import dev.hamza.localtube.ui.icons.Speed
import dev.hamza.localtube.ui.icons.Stop
import dev.hamza.localtube.ui.icons.VolumeOff
import dev.hamza.localtube.ui.icons.VolumeUp
import dev.hamza.localtube.util.Fmt
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Fullscreen offline player with hand-drawn controls: back, stop, previous, ±10 s, play/pause, next,
 * speed, device volume and a lock for the pocket.
 */
@Composable
fun PlayerScreen(
    playlistId: Long,
    downloadId: Long,
    startAtMs: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var interaction by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.start(playlistId, downloadId, startAtMs) }

    // Keep the screen awake and the system bars out of the way for as long as this screen is shown.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(state.isPlaying, state.queue.size) {
        while (isActive) {
            viewModel.refreshPosition()
            delay(if (state.isPlaying) 250L else 900L)
        }
    }

    LaunchedEffect(controlsVisible, interaction, state.isPlaying, locked) {
        if (controlsVisible && state.isPlaying && !locked) {
            delay(3_400)
            controlsVisible = false
            showSpeed = false
        }
    }

    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) controlsVisible = true
    }

    fun poke() {
        interaction++
        controlsVisible = !controlsVisible || !state.isPlaying
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { if (locked) locked = false else poke() },
                    onDoubleTap = { offset ->
                        if (!locked) {
                            if (offset.x < size.width / 2) viewModel.skip(-10) else viewModel.skip(10)
                            poke()
                        }
                    },
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
                                viewModel.setSurface(surfaceHolder.surface)
                            }

                            override fun surfaceChanged(
                                surfaceHolder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
                                viewModel.setSurface(null)
                            }
                        },
                    )
                }
            },
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
            }
        }

        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        val current = state.current
        if (!locked && controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = current?.title ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                current?.channel?.let { append(it).append(" · ") }
                                if (state.queue.size > 1) append("${state.index + 1}/${state.queue.size}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { locked = true; controlsVisible = false }) {
                        Icon(Lock, contentDescription = "Lock controls", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            viewModel.stop()
                            onBack()
                        },
                    ) {
                        Icon(Stop, contentDescription = "Stop and close", tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIcon(
                    imageVector = SkipPrevious,
                    description = "Previous",
                    enabled = state.hasPrevious,
                    size = 30.dp,
                    onClick = viewModel::previous,
                )
                PlayerIcon(Rewind, "Back 10 seconds", size = 30.dp, onClick = { viewModel.skip(-10) })
                PlayerIcon(
                    imageVector = if (state.isPlaying) Pause else Play,
                    description = if (state.isPlaying) "Pause" else "Play",
                    size = 46.dp,
                    onClick = viewModel::togglePlay,
                )
                PlayerIcon(Forward, "Forward 10 seconds", size = 30.dp, onClick = { viewModel.skip(10) })
                PlayerIcon(
                    imageVector = SkipNext,
                    description = "Next",
                    enabled = state.hasNext,
                    size = 30.dp,
                    onClick = viewModel::next,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 10.dp),
            ) {
                if (showSpeed) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        SPEEDS.forEach { value ->
                            FilterChip(
                                selected = abs(state.speed - value) < 0.01f,
                                onClick = { viewModel.setSpeed(value) },
                                label = { Text(if (value == 1f) "1×" else "${value}×") },
                            )
                        }
                    }
                }

                val duration = state.durationMs.coerceAtLeast(1L)
                val shown = (scrubMs ?: state.positionMs).coerceIn(0L, duration)
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { scrubMs = it.toLong() },
                    valueRange = 0f..duration.toFloat(),
                    steps = 0,
                    onValueChangeFinished = {
                        scrubMs?.let(viewModel::seekTo)
                        scrubMs = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${Fmt.duration(shown / 1000)} / ${Fmt.duration(duration / 1000)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerIcon(
                            imageVector = if (state.muted || state.volume <= 0f) VolumeOff else VolumeUp,
                            description = if (state.muted) "Unmute" else "Mute",
                            size = 22.dp,
                            onClick = viewModel::toggleMute,
                        )
                        Slider(
                            value = if (state.muted) 0f else state.volume,
                            onValueChange = { viewModel.setVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.width(92.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.85f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        PlayerIcon(Speed, "Playback speed", size = 22.dp, onClick = { showSpeed = !showSpeed })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        } else if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color(0x66000000), RoundedCornerShape(20.dp)),
            ) {
                Text(
                    text = "  locked · tap to unlock  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerIcon(
    imageVector: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
