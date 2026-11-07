package de.autoknigge.solarprognose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Börsenpreis einer Stunde in ct/kWh netto. [start] ist der Stundenanfang in Sekunden seit 1970. */
data class PriceHour(val start: Long, val ct: Double)

/** Monatswerte je kWp und Beschriftung der Datenbasis. */
data class YearData(val perKwp: List<Double>, val label: String)

enum class WeatherKind { Sun, SunCloud, Cloud, Rain, Snow, Hail, Thunder, Storm }

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

    /** Tagessymbol. Reihenfolge: Hagel, Gewitter, Sturm (Böen ab 75 km/h), Schnee, Regen, sonst nach Bewölkung. */
    fun kindOf(d: Day): WeatherKind {
        val c = d.cloudAvg ?: 100.0
        return when {
            d.hail -> WeatherKind.Hail
            d.thunder -> WeatherKind.Thunder
            d.gustMax >= 75.0 -> WeatherKind.Storm
            d.snowCm >= 0.3 -> WeatherKind.Snow
            d.rainMm >= 1.0 -> WeatherKind.Rain
            c < 25.0 -> WeatherKind.Sun
            c < 60.0 -> WeatherKind.SunCloud
            else -> WeatherKind.Cloud
        }
    }

    // ---------- Jahreswerte ----------

    private const val SERIES = "https://re.jrc.ec.europa.eu/api/v5_3/seriescalc"
    private const val LABEL3 = "Mittel der drei Jahre 2021 bis 2023 (PVGIS)"
    private const val LABEL_LT = "Langzeitmittel aus vielen Jahren (PVGIS)"

    private fun lossOf(p: Plant) = (100 - p.eff).coerceIn(0, 60)

    /** Stundenreihe 2021 bis 2023, daraus Monatssummen und deren Mittel. */
    private fun fetchThreeYears(p: Plant): List<Double> {
        val json = Api.get(
            "$SERIES?lat=${p.lat}&lon=${p.lon}&peakpower=1&loss=${lossOf(p)}&angle=${p.tilt}&aspect=${p.az}" +
                "&pvcalculation=1&startyear=2021&endyear=2023&outputformat=json", false
        )
        val arr = JSONObject(json).getJSONObject("outputs").getJSONArray("hourly")
        val sums = HashMap<Int, DoubleArray>() // Jahr -> 12 Monatssummen in Wh
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.getString("time")
            val y = t.substring(0, 4).toInt()
            val m = t.substring(4, 6).toInt()
            if (m !in 1..12) continue
            sums.getOrPut(y) { DoubleArray(12) }[m - 1] += o.optDouble("P", 0.0)
        }
        if (sums.size < 2) throw IllegalStateException("zu wenig Jahre")
        val out = DoubleArray(12)
        for (a in sums.values) for (m in 0 until 12) out[m] += a[m] / 1000.0 / sums.size
        if (out.sum() <= 0.0) throw IllegalStateException("leer")
        return out.toList()
    }

    private fun fetchLongTerm(p: Plant): List<Double> {
        val json = if (Api.hasProxy) {
            Api.get("${Api.proxy}/yearly?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}&loss=${lossOf(p)}", true)
        } else {
            Api.get("$PVGIS?lat=${p.lat}&lon=${p.lon}&peakpower=1&loss=${lossOf(p)}&angle=${p.tilt}&aspect=${p.az}&outputformat=json", false)
        }
        return parseYear(json)
    }

    /** kWh je kWp und Monat, 12 Werte (Januar bis Dezember), aus der PVcalc-Antwort. */
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

    fun cachedYear(ctx: Context, p: Plant): YearData? {
        val sp = prefs(ctx)
        if (sp.getString("ysig3_${p.id}", null) != yearSig(p)) return null
        return try {
            val v = (sp.getString("ycsv3_${p.id}", null) ?: return null).split(",").map { it.toDouble() }
            if (v.size != 12) null else YearData(v, sp.getString("ylab3_${p.id}", LABEL_LT) ?: LABEL_LT)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadYear(ctx: Context, p: Plant): YearData = withContext(Dispatchers.IO) {
        val sp = prefs(ctx)
        val fresh = System.currentTimeMillis() - sp.getLong("yts3_${p.id}", 0L) < YEAR_TTL_MS
        val hit = if (fresh) cachedYear(ctx, p) else null
        if (hit != null) return@withContext hit
        val data = try {
            YearData(fetchThreeYears(p), LABEL3)
        } catch (e: Exception) {
            YearData(fetchLongTerm(p), LABEL_LT)
        }
        sp.edit()
            .putString("ycsv3_${p.id}", data.perKwp.joinToString(","))
            .putString("ylab3_${p.id}", data.label)
            .putString("ysig3_${p.id}", yearSig(p))
            .putLong("yts3_${p.id}", System.currentTimeMillis())
            .apply()
        data
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
