package dev.hamza.localtube.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24dp glyphs: the player controls plus the few icons the core Material set is missing.
 *
 * `material-icons-core` does not contain them and `material-icons-extended` is a ~1.5 MB
 * dependency, which is the wrong trade for a dozen glyphs. Paths are the public Material 24dp grid.
 */
private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Black like every generated Material icon; Icon() repaints it through its tint.
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()

val Play: ImageVector = icon("play", "M8,5 L19,12 L8,19 Z")

val Pause: ImageVector = icon("pause", "M6,19 L10,19 L10,5 L6,5 Z M14,5 L14,19 L18,19 L18,5 Z")

val SkipNext: ImageVector = icon("skip_next", "M6,18 L14.5,12 L6,6 Z M16,6 L16,18 L18,18 L18,6 Z")

val SkipPrevious: ImageVector = icon("skip_previous", "M6,6 L8,6 L8,18 L6,18 Z M9.5,12 L18,6 L18,18 Z")

/** Circular counter-clockwise arrow; the UI overlays "10" / "30" so one glyph serves both. */
val Rewind: ImageVector = icon(
    "replay",
    "M12,5 L12,2 L7,6 L12,10 L12,7 C14.76,7 17,9.24 17,12 C17,14.76 14.76,17 12,17 " +
        "C9.24,17 7,14.76 7,12 L5,12 C5,15.87 8.13,19 12,19 C15.87,19 19,15.87 19,12 " +
        "C19,8.13 15.87,5 12,5 Z",
)

val Forward: ImageVector = icon(
    "forward",
    "M12,5 L12,2 L17,6 L12,10 L12,7 C9.24,7 7,9.24 7,12 C7,14.76 9.24,17 12,17 " +
        "C14.76,17 17,14.76 17,12 L19,12 C19,15.87 15.87,19 12,19 C8.13,19 5,15.87 5,12 " +
        "C5,8.13 8.13,5 12,5 Z",
)

val VolumeUp: ImageVector = icon(
    "volume_up",
    "M3,9 L3,15 L7,15 L12,20 L12,4 L7,9 Z M16.5,12 C16.5,10.23 15.48,8.71 14,7.97 " +
        "L14,16.02 C15.48,15.29 16.5,13.77 16.5,12 Z M14,3.23 L14,5.29 C16.89,6.15 19,8.83 19,12 " +
        "C19,15.17 16.89,17.85 14,18.71 L14,20.77 C18.01,19.86 21,16.28 21,12 C21,7.72 18.01,4.14 14,3.23 Z",
)

val VolumeOff: ImageVector = icon(
    "volume_off",
    "M16.5,12 C16.5,10.23 15.48,8.71 14,7.97 L14,10.18 L16.45,12.63 C16.48,12.43 16.5,12.22 16.5,12 Z " +
        "M19,12 C19,12.94 18.8,13.82 18.46,14.64 L19.97,16.15 C20.63,14.91 21,13.5 21,12 " +
        "C21,7.72 18.01,4.14 14,3.23 L14,5.29 C16.89,6.15 19,8.83 19,12 Z M4.27,3 L3,4.27 L7.73,9 L3,9 " +
        "L3,15 L7,15 L12,20 L12,13.27 L16.25,17.52 C15.58,18.04 14.83,18.45 14,18.7 L14,20.76 " +
        "C15.38,20.45 16.63,19.81 17.69,18.95 L19.73,21 L21,19.73 L4.27,3 Z M12,4 L9.91,6.09 L12,8.18 L12,4 Z",
)

val PlaylistAdd: ImageVector = icon(
    "playlist_add",
    "M14,10 L2,10 L2,12 L14,12 Z M14,6 L2,6 L2,8 L14,8 Z M2,16 L10,16 L10,14 L2,14 Z " +
        "M19,13 L19,10 L17,10 L17,13 L14,13 L14,15 L17,15 L17,18 L19,18 L19,15 L22,15 L22,13 Z",
)

val Speed: ImageVector = icon(
    "speed",
    "M20.38,8.57 L19.17,10.3 C19.68,11.62 19.92,13.09 19.79,14.65 C19.55,17.49 18.01,19.88 15.79,21.24 " +
        "L14.71,19.32 C16.15,18.42 17.19,16.79 17.38,14.46 C17.48,13.24 17.31,12.11 16.93,11.11 Z " +
        "M3.22,12.31 L5.04,12.58 C5.2,15.04 6.34,17.13 8.03,18.36 L6.82,20.09 C4.63,18.49 3.32,15.66 3.22,12.31 Z " +
        "M6.4,6.91 C8.73,4.21 12.26,3.01 15.63,3.6 L15.35,5.61 C12.79,5.29 10.14,6.2 8.36,8.23 Z " +
        "M13,12 L18.05,5.11 L16.5,4 L11.5,10.77 C11.25,10.53 10.9,10.38 10.5,10.38 C9.67,10.38 9,11.05 9,11.88 " +
        "C9,12.71 9.67,13.38 10.5,13.38 C11.33,13.38 12,12.71 12,11.88 C12,11.84 12,11.81 12,11.77 Z",
)

val Lock: ImageVector = icon(
    "lock",
    "M18,8 L17,8 L17,6 C17,3.24 14.76,1 12,1 C9.24,1 7,3.24 7,6 L7,8 L6,8 C4.9,8 4,8.9 4,10 L4,20 " +
        "C4,21.1 4.9,22 6,22 L18,22 C19.1,22 20,21.1 20,20 L20,10 C20,8.9 19.1,8 18,8 Z M12,17 " +
        "C10.9,17 10,16.1 10,15 C10,13.9 10.9,13 12,13 C13.1,13 14,13.9 14,15 C14,16.1 13.1,17 12,17 Z " +
        "M15.1,8 L8.9,8 L8.9,6 C8.9,4.29 10.29,2.9 12,2.9 C13.71,2.9 15.1,4.29 15.1,6 L15.1,8 Z",
)

val Download: ImageVector = icon(
    "download",
    "M19,9 L15,9 L15,3 L9,3 L9,9 L5,9 L12,16 Z M5,18 L5,20 L19,20 L19,18 Z",
)

val PlaylistPlay: ImageVector = icon(
    "playlist_play",
    "M3,10 L14,10 L14,12 L3,12 Z M3,6 L14,6 L14,8 L3,8 Z M3,14 L10,14 L10,16 L3,16 Z " +
        "M16,13 L16,21 L23,17 Z",
)

val Folder: ImageVector = icon(
    "folder",
    "M10,4 L4,4 C2.9,4 2.01,4.9 2.01,7 L2,17 C2,18.1 2.9,19 4,19 L20,19 C21.1,19 22,18.1 22,17 " +
        "L22,9 C22,7.9 21.1,7 20,7 L12,7 Z",
)

/** Two-by-two squares — the library's grid/list toggle. */
val GridView: ImageVector = icon(
    "grid_view",
    "M4,4 L10,4 L10,10 L4,10 Z M14,4 L20,4 L20,10 L14,10 Z M4,14 L10,14 L10,20 L4,20 Z " +
        "M14,14 L20,14 L20,20 L14,20 Z",
)

val RowsView: ImageVector = icon(
    "rows_view",
    "M4,5 L20,5 L20,9 L4,9 Z M4,11 L20,11 L20,15 L4,15 Z M4,17 L20,17 L20,21 L4,21 Z",
)

val Stop: ImageVector = icon("stop", "M6,6 L18,6 L18,18 L6,18 Z")
