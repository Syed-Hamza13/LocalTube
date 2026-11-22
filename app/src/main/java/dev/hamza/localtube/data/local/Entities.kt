package dev.hamza.localtube.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One finished download. [filePath] is either an absolute path inside the app-private media
 * dir or a `content://` URI when the file was published into Movies through MediaStore.
 */
@Entity(
    tableName = "downloads",
    indices = [Index("createdAt"), Index("videoId")],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val url: String,
    val title: String,
    val channel: String?,
    @ColumnInfo(name = "durationSec") val durationSec: Long,
    val filePath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long,
    /** [dev.hamza.localtube.domain.DownloadMode] key */
    val mode: String,
    /** e.g. "1080p", "m4a", "mp3" */
    val quality: String?,
    val ext: String,
    val createdAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "downloadId"],
    indices = [Index("playlistId"), Index("downloadId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val downloadId: Long,
    val position: Int,
    val addedAt: Long,
)

/** Projection used by the playlists tab. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val totalBytes: Long,
    val coverPath: String?,
)
