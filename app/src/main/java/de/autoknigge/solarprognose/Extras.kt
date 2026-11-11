package de.autoknigge.solarprognose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Börsenpreis einer Stunde in ct/kWh netto. [start] ist der Stundenanfang in Sekunden seit 1970. */
data class PriceHour(val start: Long, val ct: Double)

/** Monatswerte je kWp und Beschriftung der Datenbasis. */
data class YearData(val perKwp: List<Double>, val label: String)

/** Strommix-Zeitreihe: [t] Sekunden, [v] je Gruppe MW (Gruppen plus "Import" und "Export"). */
class MixSeries(val t: LongArray, val v: Map<String, DoubleArray>, val hasTrade: Boolean)

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
    private const val LABEL3 = "Mittel der fünf Jahre 2019 bis 2023 (PVGIS)"
    private const val LABEL_LT = "Langzeitmittel aus vielen Jahren (PVGIS)"

    private fun lossOf(p: Plant) = (100 - p.eff).coerceIn(0, 60)

    /** Letzte fünf volle Jahre aus dem Open-Meteo-Archiv, mit dem gleichen Leistungsmodell wie die Prognose. */
    private fun fetchFiveYears(p: Plant): Pair<List<Double>, String> {
        val end = LocalDate.now().year - 1
        val start = end - 4
        val json = Api.get(
            "https://archive-api.open-meteo.com/v1/archive?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}" +
                "&start_date=$start-01-01&end_date=$end-12-31&hourly=global_tilted_irradiance,temperature_2m&timezone=auto", false
        )
        val h = JSONObject(json).getJSONObject("hourly")
        val time = h.getJSONArray("time")
        val gti = h.getJSONArray("global_tilted_irradiance")
        val tmp = h.getJSONArray("temperature_2m")
        val sums = HashMap<Int, DoubleArray>()
        for (i in 0 until time.length()) {
            if (gti.isNull(i)) continue
            val g = gti.getDouble(i).coerceAtLeast(0.0)
            val t = if (tmp.isNull(i)) 10.0 else tmp.getDouble(i)
            val w = g * (1.0 - 0.004 * (t + g * 0.031 - 25.0)) * (p.eff / 100.0) // Watt je kWp
            val ts = time.getString(i) // 2024-05-17T13:00
            sums.getOrPut(ts.substring(0, 4).toInt()) { DoubleArray(12) }[ts.substring(5, 7).toInt() - 1] += w.coerceAtLeast(0.0)
        }
        val full = sums.filterValues { it.sum() > 0.0 }
        if (full.size < 3) throw IllegalStateException("zu wenig Jahre")
        val out = DoubleArray(12)
        for (a in full.values) for (m in 0 until 12) out[m] += a[m] / 1000.0 / full.size
        return out.toList() to "Mittel der ${full.size} Jahre ${full.keys.min()} bis ${full.keys.max()} (Open-Meteo)"
    }

    /** Stundenreihe 2019 bis 2023 (neuere Jahre hat PVGIS nicht), daraus Monatssummen und deren Mittel. */
    private fun fetchThreeYears(p: Plant): List<Double> {
        val json = Api.get(
            "$SERIES?lat=${p.lat}&lon=${p.lon}&peakpower=1&loss=${lossOf(p)}&angle=${p.tilt}&aspect=${p.az}" +
                "&pvcalculation=1&startyear=2019&endyear=2023&outputformat=json", false
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
        if (sp.getString("ysig5_${p.id}", null) != yearSig(p)) return null
        return try {
            val v = (sp.getString("ycsv5_${p.id}", null) ?: return null).split(",").map { it.toDouble() }
            if (v.size != 12) null else YearData(v, sp.getString("ylab5_${p.id}", LABEL_LT) ?: LABEL_LT)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun loadYear(ctx: Context, p: Plant): YearData = withContext(Dispatchers.IO) {
        val sp = prefs(ctx)
        val fresh = System.currentTimeMillis() - sp.getLong("yts5_${p.id}", 0L) < YEAR_TTL_MS
        val hit = if (fresh) cachedYear(ctx, p) else null
        if (hit != null) return@withContext hit
        val data = try {
            fetchFiveYears(p).let { YearData(it.first, it.second) }
        } catch (e: Exception) {
            try {
                YearData(fetchThreeYears(p), LABEL3)
            } catch (e2: Exception) {
                YearData(fetchLongTerm(p), LABEL_LT)
            }
        }
        sp.edit()
            .putString("ycsv5_${p.id}", data.perKwp.joinToString(","))
            .putString("ylab5_${p.id}", data.label)
            .putString("ysig5_${p.id}", yearSig(p))
            .putLong("yts5_${p.id}", System.currentTimeMillis())
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

    // ---------- Strommix (Energy-Charts, Fraunhofer ISE) ----------

    val MIX_GROUPS = listOf("Kohle", "Erdgas", "Biomasse", "Wind", "Wasserkraft", "Solar", "Sonstige")
    private val mixCache = HashMap<Int, Pair<Long, MixSeries>>()

    private fun mixGroup(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("load") || n.contains("share") || n.contains("consumption") -> null
            n.contains("coal") && !n.contains("gas") -> "Kohle"
            n.contains("lignite") -> "Kohle"
            n == "fossil gas" -> "Erdgas"
            n.contains("biomass") -> "Biomasse"
            n.contains("wind") -> "Wind"
            n.contains("hydro") && !n.contains("pumped") -> "Wasserkraft"
            n == "solar" -> "Solar"
            else -> "Sonstige"
        }
    }

    /**
     * Nettostromerzeugung je Gruppe (MW) plus Import/Export (MW, beides positiv). Auf höchstens [maxPoints] Zeitpunkte
     * gemittelt. Schneidet hinten alles ab, wofür noch keine Erzeugungswerte vorliegen.
     */
    fun parseMixSeries(json: String, maxPoints: Int, cbet: Map<Long, Double>? = null): MixSeries? {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val types = o.getJSONArray("production_types")
        val n = t.length()
        if (n == 0) return null
        val keys = MIX_GROUPS + listOf("Import", "Export")
        val raw = keys.associateWith { DoubleArray(n) }
        var last = -1
        var haveTrade = false
        for (k in 0 until types.length()) {
            val ty = types.getJSONObject(k)
            val name = ty.optString("name")
            val d = ty.optJSONArray("data") ?: continue
            val lower = name.lowercase()
            if (lower.contains("cross") && lower.contains("border")) {
                haveTrade = true
                for (i in 0 until minOf(n, d.length())) {
                    if (d.isNull(i)) continue
                    val v = d.getDouble(i)
                    if (v > 0) raw["Import"]!![i] = v else raw["Export"]!![i] = -v
                }
                continue
            }
            val g = mixGroup(name) ?: continue
            val a = raw[g]!!
            for (i in 0 until minOf(n, d.length())) {
                if (d.isNull(i)) continue
                val v = d.getDouble(i)
                if (v > 0) {
                    a[i] += v
                    if (i > last) last = i
                }
            }
        }
        if (last < 0) return null
        if (!haveTrade && cbet != null) {
            for (i in 0..last) {
                val v = cbet[t.getLong(i)] ?: continue
                if (v > 0) raw["Import"]!![i] = v else raw["Export"]!![i] = -v
            }
        }
        val len = last + 1
        val step = Math.ceil(len / maxPoints.toDouble()).toInt().coerceAtLeast(1)
        val cnt = (len + step - 1) / step
        val times = LongArray(cnt) { t.getLong(it * step) }
        val out = keys.associateWith { key ->
            val src = raw[key]!!
            DoubleArray(cnt) { b ->
                val from = b * step
                val to = minOf(len, from + step)
                var s = 0.0
                for (i in from until to) s += src[i]
                s / (to - from)
            }
        }
        return MixSeries(times, out, haveTrade || cbet != null)
    }

    private fun cbetMap(json: String): Map<Long, Double> {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val cs = o.optJSONArray("countries") ?: return emptyMap()
        val m = HashMap<Long, Double>()
        for (k in 0 until cs.length()) {
            val d = cs.getJSONObject(k).optJSONArray("data") ?: continue
            for (i in 0 until minOf(t.length(), d.length())) {
                if (d.isNull(i)) continue
                m[t.getLong(i)] = (m[t.getLong(i)] ?: 0.0) + d.getDouble(i) * 1000.0
            }
        }
        return m
    }

    /** range: 0 = Tag (heute, sonst gestern), 1 = Woche (7 Tage), 2 = Jahr (365 Tage). */
    suspend fun loadMixSeries(range: Int): MixSeries? = withContext(Dispatchers.IO) {
        val ttl = when (range) { 0 -> 20L * 60 * 1000; 1 -> 60L * 60 * 1000; else -> 6L * 3600 * 1000 }
        synchronized(mixCache) { mixCache[range] }?.let { if (System.currentTimeMillis() - it.first < ttl) return@withContext it.second }
        val today = LocalDate.now(berlin)
        fun fetch(from: LocalDate, to: LocalDate, max: Int): MixSeries? {
            val base = "https://api.energy-charts.info"
            val pj = Api.get("$base/public_power?country=de&start=$from&end=$to", false)
            var s = parseMixSeries(pj, max)
            if (s != null && !s.hasTrade) {
                try {
                    s = parseMixSeries(pj, max, cbetMap(Api.get("$base/cbet?country=de&start=$from&end=$to", false)))
                } catch (e: Exception) {
                }
            }
            return s
        }
        var s = when (range) {
            0 -> fetch(today, today, 96) ?: fetch(today.minusDays(1), today.minusDays(1), 96)
            1 -> fetch(today.minusDays(6), today, 168)
            else -> fetch(today.minusDays(364), today, 183)
        }
        if (s != null) synchronized(mixCache) { mixCache[range] = System.currentTimeMillis() to s }
        s
    }
}
