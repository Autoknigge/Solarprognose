package de.autoknigge.solarprognose

import android.content.Context
import android.util.Log
import java.io.File

/** Merkt sich den letzten Absturz in einer Datei, damit er beim nächsten Start angezeigt werden kann. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(app.filesDir, FILE).writeText(Log.getStackTraceString(e).take(6000))
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? = try {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()
    } catch (_: Throwable) {
        null
    }

    fun clear(ctx: Context) {
        try {
            File(ctx.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }
}
