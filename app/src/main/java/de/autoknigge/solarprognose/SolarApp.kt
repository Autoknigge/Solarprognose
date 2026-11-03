package de.autoknigge.solarprognose

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName

class SolarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        val store = Store(this)
        if (store.notifyOn) Scheduler.scheduleDaily(this, store.notifyHour)
        val ids = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, SolarWidget::class.java))
        if (ids.isNotEmpty()) Scheduler.scheduleWidgetRefresh(this)
    }
}
