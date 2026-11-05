package de.autoknigge.solarprognose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Börsenpreis einer Stunde in ct/kWh netto. [start] ist der Stundenanfang in Sekunden seit 1970. */
data class PriceHour(val start: Long, val ct: Double)

enum class WeatherKind { Sun, SunCloud, Cloud, Rain }

/**
 * Zugaben zur Prognose:
 * - Jahreswerte je Monat aus PVGIS (Europäische Kommission, JRC), Langzeitmittel
 * - Börsenstrompreise (Day-Ahead) aus Energy-Charts (Fraunhofer ISE)
 * - Wettersymbol für einen Tag
 */
object Extras {
    private const val PVGIS = "https://re.jrc.ec.europa.eu/api/v5_3/PVcalc"
    private const val YEAR_TTL_MS = 30L * 24 * 3600 * 1000
    private const val PRICE_TTL_MS = 20L * 60 * 1000
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("extras", Context.MODE_PRIVATE)

    private fun yearSig(p: Plant) = "${p.lat},${p.lon},${p.tilt},${p.az},${p.eff}"

    // ---------- Wettersymbol ----------

    fun kindOf(d: Day): WeatherKind {
        val c = d.cloudAvg ?: 100.0
        return when {
            d.rainMm >= 1.0 -> WeatherKind.Rain
            c < 25.0 -> WeatherKind.Sun
            c < 60.0 -> WeatherKind.SunCloud
            else -> WeatherKind.Cloud
        }
    }

    // ---------- Jahreswerte ----------

    private fun yearJson(p: Plant): String {
        val loss = (100 - p.eff).coerceIn(0, 60)
        return if (Api.hasProxy) {
            Api.get(
                "${Api.proxy}/yearly?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}&loss=$loss",
                true
            )
        } else {
            Api.get("$PVGIS?lat=${p.lat}&lon=${p.lon}&peakpower=1&loss=$loss&angle=${p.tilt}&aspect=${p.az}&outputformat=json", false)
        }
    }

    /** kWh je kWp und Monat, 12 Werte (Januar bis Dezember). */
    fun parseYear(json: String): List<Double> {
        val arr = JSONObject(json).getJSONObject("outputs").getJSONObject("monthly").getJSONArray("fixed")
        val out = DoubleArray(12)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val m = o.getInt("month")
            if (m in 1..12) out[m - 1] = o.getDouble("E_m")
        }
        if (out.sum() <= 0.0) throw IllegalStateException("leer")
        return out.toList()
    }

    fun cachedYear(ctx: Context, p: Plant): List<Double>? {
        val sp = prefs(ctx)
        if (sp.getString("ysig_${p.id}", null) != yearSig(p)) return null
        return try {
            parseYear(sp.getString("yjson_${p.id}", null) ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadYear(ctx: Context, p: Plant): List<Double> = withContext(Dispatchers.IO) {
        val sp = prefs(ctx)
        val fresh = System.currentTimeMillis() - sp.getLong("yts_${p.id}", 0L) < YEAR_TTL_MS
        val hit = if (fresh) cachedYear(ctx, p) else null
        if (hit != null) return@withContext hit
        val json = yearJson(p)
        val v = parseYear(json)
        sp.edit()
            .putString("yjson_${p.id}", json)
            .putString("ysig_${p.id}", yearSig(p))
            .putLong("yts_${p.id}", System.currentTimeMillis())
            .apply()
        v
    }

    // ---------- Börsenpreise ----------

    private fun pricesJson(): String {
        if (Api.hasProxy) return Api.get("${Api.proxy}/prices?bzn=DE-LU", true)
        val today = LocalDate.now(berlin)
        return Api.get("https://api.energy-charts.info/price?bzn=DE-LU&start=$today&end=${today.plusDays(2)}", false)
    }

    /** Fasst Viertelstundenwerte zu Stundenmittel zusammen. EUR/MWh geteilt durch 10 ergibt ct/kWh. Nur ab heute 0 Uhr. */
    fun parsePrices(json: String): List<PriceHour> {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val pr = o.getJSONArray("price")
        val dayStart = LocalDate.now(berlin).atStartOfDay(berlin).toEpochSecond()
        val sums = LinkedHashMap<Long, DoubleArray>()
        for (i in 0 until minOf(t.length(), pr.length())) {
            if (pr.isNull(i)) continue
            val h = t.getLong(i) / 3600 * 3600
            if (h < dayStart) continue
            val a = sums.getOrPut(h) { DoubleArray(2) }
            a[0] += pr.getDouble(i)
            a[1] += 1.0
        }
        val out = sums.entries.map { PriceHour(it.key, it.value[0] / it.value[1] / 10.0) }.sortedBy { it.start }
        if (out.isEmpty()) throw IllegalStateException("leer")
        return out
    }

    fun cachedPrices(ctx: Context): List<PriceHour>? {
        val json = prefs(ctx).getString("prices_json", null) ?: return null
        return try {
            parsePrices(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadPrices(ctx: Context): List<PriceHour> = withContext(Dispatchers.IO) {
        val sp = prefs(ctx)
        if (System.currentTimeMillis() - sp.getLong("prices_ts", 0L) < PRICE_TTL_MS) {
            cachedPrices(ctx)?.let { return@withContext it }
        }
        val json = pricesJson()
        val v = parsePrices(json)
        sp.edit().putString("prices_json", json).putLong("prices_ts", System.currentTimeMillis()).apply()
        v
    }
}
