package dev.hamza.localtube.data.repository

import dev.hamza.localtube.data.local.AppDatabase
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.data.local.PlaylistEntity
import dev.hamza.localtube.data.local.PlaylistItemEntity
import dev.hamza.localtube.data.local.PlaylistSummary
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.data.storage.StoredMedia
import dev.hamza.localtube.domain.DownloadMode
import dev.hamza.localtube.domain.Quality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class LibraryStats(val count: Int, val totalBytes: Long)

/** Single entry point for everything stored in Room + on disk. */
class LibraryRepository(
    db: AppDatabase,
    private val storage: StorageManager,
) {
    private val downloads = db.downloadDao()
    private val playlists = db.playlistDao()

    fun observeDownloads(query: String): Flow<List<DownloadEntity>> = downloads.observeAll(query)

    /** One-shot snapshot for the player queue, which only needs the list once. */
    suspend fun snapshotDownloads(): List<DownloadEntity> = downloads.observeAll().first()

    fun observeStats(): Flow<LibraryStats> =
        combine(downloads.observeCount(), downloads.observeTotalBytes()) { count, bytes ->
            LibraryStats(count, bytes)
        }

    suspend fun find(id: Long): DownloadEntity? = downloads.byId(id)

    suspend fun alreadyDownloaded(url: String): Boolean = downloads.countForUrl(url) > 0

    suspend fun record(
        videoId: String,
        url: String,
        title: String,
        channel: String?,
        durationSec: Long,
        stored: StoredMedia,
        thumbnailPath: String?,
        mode: DownloadMode,
        quality: Quality?,
    ): Long = downloads.insert(
        DownloadEntity(
            videoId = videoId,
            url = url,
            title = title,
            channel = channel,
            durationSec = durationSec,
            filePath = stored.path,
            thumbnailPath = thumbnailPath,
            sizeBytes = stored.sizeBytes,
            mode = mode.key,
            quality = qualityLabel(mode, quality, stored.ext),
            ext = stored.ext,
            createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun updatePath(id: Long, path: String) = downloads.updatePath(id, path)

    /** Removes the file (or MediaStore row) and the database entry together. */
    suspend fun deleteDownload(id: Long) {
        val entity = downloads.byId(id) ?: return
        storage.delete(entity.filePath)
        entity.thumbnailPath?.let { path ->
            // Thumbnails are keyed by remote URL, so two formats of one video share a file.
            if (downloads.countUsingThumbnail(path) <= 1) storage.delete(path)
        }
        downloads.deleteById(id)
    }

    fun observePlaylists(): Flow<List<PlaylistSummary>> = playlists.observeSummaries()

    fun observePlayablePlaylists(): Flow<List<PlaylistEntity>> = playlists.observeAllForPicker()

    suspend fun playlistName(id: Long): String? = playlists.byId(id)?.name

    /** Returns the id of the playlist called [name], creating it when it does not exist yet. */
    suspend fun createPlaylist(name: String): Long {
        val trimmed = name.trim().ifBlank { return -1L }
        playlists.byName(trimmed)?.let { return it.id }
        return playlists.insert(PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(id: Long) = playlists.deleteById(id)

    suspend fun addToPlaylist(playlistId: Long, downloadId: Long) {
        playlists.insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                downloadId = downloadId,
                position = playlists.nextPosition(playlistId),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun addToPlaylists(playlistIds: List<Long>, downloadId: Long) {
        playlistIds.forEach { addToPlaylist(it, downloadId) }
    }

    suspend fun removeFromPlaylist(playlistId: Long, downloadId: Long) =
        playlists.removeItem(playlistId, downloadId)

    suspend fun playlistsOf(downloadId: Long): List<Long> =
        playlists.itemsOf(downloadId).map { it.playlistId }

    fun observePlaylistDownloads(playlistId: Long): Flow<List<DownloadEntity>> =
        playlists.observeDownloadsOf(playlistId)

    suspend fun playlistDownloads(playlistId: Long): List<DownloadEntity> =
        playlists.downloadsOf(playlistId)

    fun observePlaylistIdsOf(downloadId: Long): Flow<List<Long>> =
        playlists.observePlaylistIdsOf(downloadId)

    private fun qualityLabel(mode: DownloadMode, quality: Quality?, ext: String): String = when (mode) {
        DownloadMode.AUDIO_ONLY -> ext.uppercase()
        DownloadMode.VIDEO_ONLY -> quality?.let { "${it.height ?: 0}p" } ?: "video"
        DownloadMode.VIDEO_AUDIO -> quality?.label?.let { if (it == "Best") "best" else it } ?: "best"
    }
}
