package dev.hamza.localtube.util

import android.content.Context
import android.os.Build
import android.util.Log
import dev.hamza.localtube.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last uncaught exception, kept on disk.
 *
 * A crash kills the process before any UI can show why, and there is no logcat without a PC — so the
 * trace is written here and Settings prints it on the next launch. Only the handler is installed; the
 * original handler still runs, so the crash itself behaves exactly as before.
 */
object CrashReporter {

    private const val FILE = "last-crash.txt"
    private const val MAX_TRACE_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).writeText(render(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = runCatching {
        val report = file(context)
        if (report.isFile) report.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun render(thread: Thread, error: Throwable): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = "$stamp thread=${thread.name} app=${BuildConfig.VERSION_NAME} " +
            "Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})"
        return header + "\n" + Log.getStackTraceString(error).take(MAX_TRACE_CHARS)
    }

    private fun file(context: Context): File = File(context.filesDir, FILE)
}
