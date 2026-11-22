package dev.hamza.localtube.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.hamza.localtube.data.local.DownloadEntity
import dev.hamza.localtube.data.repository.LibraryRepository
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.domain.DownloadMode

/** Intents that leave the app. Every one of them is granted read access to a single file only. */
object MediaActions {

    fun mimeOf(entity: DownloadEntity): String =
        if (DownloadMode.fromKey(entity.mode) == DownloadMode.AUDIO_ONLY) "audio/*" else "video/*"

    fun uriOf(storage: StorageManager, entity: DownloadEntity): Uri = storage.toUri(entity.filePath)

    /** "Open with" — hands the file to the system player through FileProvider or MediaStore. */
    fun openExternally(context: Context, storage: StorageManager, entity: DownloadEntity): Boolean =
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriOf(storage, entity), mimeOf(entity))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Play with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.recoverCatching { error ->
            if (error is ActivityNotFoundException) false else throw error
        }.getOrDefault(false)

    fun share(context: Context, storage: StorageManager, entity: DownloadEntity): Boolean =
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(entity)
                putExtra(Intent.EXTRA_STREAM, uriOf(storage, entity))
                putExtra(Intent.EXTRA_TEXT, entity.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)

    /**
     * Moves an app-private file into the public Movies/Music folder so other apps can see it, then
     * points the database row at the new MediaStore uri.
     */
    suspend fun exportToPublic(
        entity: DownloadEntity,
        storage: StorageManager,
        library: LibraryRepository,
        publicFolder: String,
    ): String? {
        val displayName = "${StorageManager.sanitize(entity.title)} (${entity.videoId}).${entity.ext}"
        val moved = storage.moveIntoPublic(
            storedPath = entity.filePath,
            displayName = displayName,
            mode = DownloadMode.fromKey(entity.mode),
            folder = publicFolder,
        ) ?: return null
        library.updatePath(entity.id, moved)
        return moved
    }
}
