package de.autoknigge.solarprognose

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Startbildschirm-Widget mit Ertrag für heute und morgen. Liest die zuletzt gespeicherte Kurzfassung. */
class SolarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
        Scheduler.scheduleWidgetRefresh(context)
    }

    override fun onEnabled(context: Context) {
        Scheduler.scheduleWidgetRefresh(context)
    }

    override fun onDisabled(context: Context) {
        Scheduler.cancelWidgetRefresh(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SolarWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget)
            val s = Store(context).summary()
            if (s == null) {
                v.setTextViewText(R.id.w_title, "Solarprognose")
                v.setTextViewText(R.id.w_today, "–")
                v.setTextViewText(R.id.w_tomorrow, "–")
                v.setTextViewText(R.id.w_note, "App öffnen und Anlage anlegen")
            } else {
                v.setTextViewText(R.id.w_title, s.name)
                v.setTextViewText(R.id.w_today, Calc.fmt1(s.todayKwh) + " kWh")
                v.setTextViewText(R.id.w_tomorrow, (s.tomorrowKwh?.let { Calc.fmt1(it) } ?: "–") + " kWh")
                v.setTextViewText(
                    R.id.w_note,
                    "Stand " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(s.ts)) + " Uhr"
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
