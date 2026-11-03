package de.autoknigge.solarprognose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Holt Prognosen und hält die letzte Antwort pro Anlage als Zwischenspeicher. */
object Repo {
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("cache", Context.MODE_PRIVATE)

    /** Zwischengespeicherte Daten, nur gültig, solange Ort, Neigung und Ausrichtung gleich sind. */
    fun cached(ctx: Context, p: Plant): Raw? {
        val sp = prefs(ctx)
        if (sp.getString("sig_${p.id}", null) != p.geoSig) return null
        val json = sp.getString("raw_${p.id}", null) ?: return null
        return try {
            Calc.parse(json)
        } catch (e: Exception) {
            null
        }
    }

    fun cachedTime(ctx: Context, p: Plant): Long = prefs(ctx).getLong("ts_${p.id}", 0L)

    suspend fun fetch(ctx: Context, p: Plant): Raw = withContext(Dispatchers.IO) {
        val json = Api.forecastJson(p)
        val raw = Calc.parse(json)
        prefs(ctx).edit()
            .putString("raw_${p.id}", json)
            .putString("sig_${p.id}", p.geoSig)
            .putLong("ts_${p.id}", System.currentTimeMillis())
            .apply()
        raw
    }

    fun forget(ctx: Context, id: String) {
        prefs(ctx).edit().remove("raw_$id").remove("sig_$id").remove("ts_$id").apply()
    }

    /** Legt die Kurzfassung für das Widget ab. */
    fun saveSummary(ctx: Context, p: Plant, raw: Raw) {
        val f = Calc.compute(raw, p)
        if (f.days.isEmpty()) return
        Store(ctx).saveSummary(Calc.summarize(f, p))
    }
}
