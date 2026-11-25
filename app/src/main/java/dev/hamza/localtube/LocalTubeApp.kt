package dev.hamza.localtube

import android.app.Application
import dev.hamza.localtube.di.ServiceLocator
import dev.hamza.localtube.util.CrashReporter
import dev.hamza.localtube.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalTubeApp : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)
        Notifier.ensureChannels(this)
        services = ServiceLocator(this)

        // The yt-dlp Python bundle is only unpacked on the first real job, never at launch:
        // that keeps cold start under ~200 ms and avoids waking the CPU when the app is just
        // being used as an offline player.
        appScope.launch { runCatching { services.scheduler.restore() } }
    }

    companion object {
        lateinit var instance: LocalTubeApp
            private set

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
