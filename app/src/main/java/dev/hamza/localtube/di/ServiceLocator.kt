package dev.hamza.localtube.di

import android.content.Context
import androidx.room.Room
import dev.hamza.localtube.data.local.AppDatabase
import dev.hamza.localtube.data.prefs.AppPrefs
import dev.hamza.localtube.data.repository.LibraryRepository
import dev.hamza.localtube.data.storage.StorageManager
import dev.hamza.localtube.download.DownloadScheduler
import dev.hamza.localtube.engine.YtDlpEngine

/**
 * Hand-written dependency graph.
 *
 * Hilt or Koin would add 1-3 MB and a startup cost for an app with seven long-lived objects, so the
 * graph is built by hand here: one instance, lazily, wired in dependency order.
 */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val prefs: AppPrefs = AppPrefs(appContext)

    val storage: StorageManager = StorageManager(appContext, prefs)

    val engine: YtDlpEngine = YtDlpEngine(appContext, storage)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "localtube.db").build()
    }

    val library: LibraryRepository by lazy { LibraryRepository(database, storage) }

    val scheduler: DownloadScheduler by lazy { DownloadScheduler(appContext, engine, prefs) }
}
