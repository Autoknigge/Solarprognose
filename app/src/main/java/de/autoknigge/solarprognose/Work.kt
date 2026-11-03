package de.autoknigge.solarprognose

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

const val CHANNEL_DAILY = "daily"

object Scheduler {
    private const val DAILY = "daily_notification"
    private const val WIDGET = "widget_refresh"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Tägliche Nachricht, ungefähr zur gewählten Uhrzeit (Android darf um einige Minuten verschieben). */
    fun scheduleDaily(ctx: Context, hour: Int) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancelDaily(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(DAILY)
    }

    /** Hält die Widget-Anzeige aktuell, solange ein Widget auf dem Startbildschirm liegt. */
    fun scheduleWidgetRefresh(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(3, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WIDGET, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelWidgetRefresh(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WIDGET)
    }
}

fun createNotificationChannel(ctx: Context) {
    val ch = NotificationChannel(CHANNEL_DAILY, "Tagesprognose", NotificationManager.IMPORTANCE_DEFAULT)
    ch.description = "Erwarteter Solarertrag für heute und morgen"
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        if (!store.notifyOn) return Result.success()
        val lines = ArrayList<String>()
        for (p in store.plants().take(5)) {
            var raw: Raw? = null
            try {
                raw = Repo.fetch(ctx, p)
            } catch (e: Exception) {
                raw = Repo.cached(ctx, p)
            }
            if (raw == null) continue
            val f = Calc.compute(raw, p)
            val d0 = f.days.firstOrNull() ?: continue
            val tomorrow = f.days.getOrNull(1)
            var line = p.name + ": heute " + Calc.fmt1(d0.kwh) + " kWh"
            if (tomorrow != null) line += ", morgen " + Calc.fmt1(tomorrow.kwh) + " kWh"
            lines.add(line)
            if (p.id == store.selectedId) Repo.saveSummary(ctx, p, raw)
        }
        if (lines.isEmpty()) return Result.retry()
        SolarWidget.updateAll(ctx)

        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL_DAILY)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle("Solarprognose")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(1, n)
        }
        return Result.success()
    }
}

/** Aktualisiert Prognose und Widget für die aktuell gewählte Anlage. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        val plants = store.plants()
        val p = plants.firstOrNull { it.id == store.selectedId } ?: plants.firstOrNull() ?: return Result.success()
        return try {
            val raw = Repo.fetch(ctx, p)
            Repo.saveSummary(ctx, p, raw)
            SolarWidget.updateAll(ctx)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
