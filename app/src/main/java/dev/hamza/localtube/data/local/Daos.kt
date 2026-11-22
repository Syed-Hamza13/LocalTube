package dev.hamza.localtube.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query(
        """
        SELECT * FROM downloads
        WHERE :query = '' OR title LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun observeAll(query: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun byId(id: Long): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE url = :url")
    suspend fun countForUrl(url: String): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE thumbnailPath = :thumbnailPath")
    suspend fun countUsingThumbnail(thumbnailPath: String): Int

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads")
    fun observeTotalBytes(): Flow<Long>

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("UPDATE downloads SET filePath = :path WHERE id = :id")
    suspend fun updatePath(id: Long, path: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT p.id, p.name, p.createdAt,
               (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
               (SELECT COALESCE(SUM(d.sizeBytes), 0) FROM playlist_items i
                   JOIN downloads d ON d.id = i.downloadId WHERE i.playlistId = p.id) AS totalBytes,
               (SELECT d.thumbnailPath FROM playlist_items i
                   JOIN downloads d ON d.id = i.downloadId
                   WHERE i.playlistId = p.id ORDER BY i.position LIMIT 1) AS coverPath
        FROM playlists p
        ORDER BY p.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observeAllForPicker(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND downloadId = :downloadId")
    suspend fun removeItem(playlistId: Long, downloadId: Long)

    @Query("SELECT i.* FROM playlist_items i JOIN downloads d ON d.id = i.downloadId WHERE i.downloadId = :downloadId")
    suspend fun itemsOf(downloadId: Long): List<PlaylistItemEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun itemCount(playlistId: Long): Int

    @Query(
        """
        SELECT d.* FROM downloads d
        JOIN playlist_items i ON i.downloadId = d.id
        WHERE i.playlistId = :playlistId
        ORDER BY i.position ASC
        """
    )
    fun observeDownloadsOf(playlistId: Long): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT d.* FROM downloads d
        JOIN playlist_items i ON i.downloadId = d.id
        WHERE i.playlistId = :playlistId
        ORDER BY i.position ASC
        """
    )
    suspend fun downloadsOf(playlistId: Long): List<DownloadEntity>

    @Query("SELECT DISTINCT i.downloadId FROM playlist_items i WHERE i.downloadId IN (:ids)")
    suspend fun idsAlreadyInPlaylist(ids: List<Long>): List<Long>

    @Query("SELECT DISTINCT playlistId FROM playlist_items WHERE downloadId = :downloadId")
    fun observePlaylistIdsOf(downloadId: Long): Flow<List<Long>>
}
