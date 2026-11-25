package de.autoknigge.solarprognose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Börsenpreis einer Stunde in ct/kWh netto. [start] ist der Stundenanfang in Sekunden seit 1970. */
data class PriceHour(val start: Long, val ct: Double)

/** Monatswerte je kWp und Beschriftung der Datenbasis. */
data class YearData(val perKwp: List<Double>, val label: String)

/** Dieses Jahr bisher je Monat (kWh je kWp), aus dem Wetter berechnet. */
data class YtdData(val perKwp: List<Double>)

/** Strommix-Zeitreihe: [t] Sekunden, [v] je Gruppe MW (Gruppen plus "Import" und "Export"). */
class MixSeries(
    val t: LongArray, val v: Map<String, DoubleArray>, val hasTrade: Boolean,
    /** Anteil von Solar und von allen Erneuerbaren am aktuellen Strombedarf (%), mit Zeitpunkt in Sekunden. */
    val solarShare: Double? = null, val renShare: Double? = null, val shareT: Long = 0L,
    /** Ab diesem Index sind die Werte vorläufig (Solar/Wind aus Prognose, Rest fortgeschrieben); -1 = alles gemessen. */
    val estFrom: Int = -1
)

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

    private fun yearSig(p: Plant) = "${p.lat},${p.lon},${p.tilt},${p.az},${p.eff},${p.kwp},${p.limitW},${p.shades}"

    /** Wh je kWp und Stunde, gleiches Modell wie die Prognose: Verschattung, Temperatur, Wirkungsgrad, Wechselrichter-Grenze. */
    private fun whPerKwp(p: Plant, g: Double, t: Double, date: String, hour: Int): Double {
        val gs = g * Calc.shadeFactor(p.shades, date, hour)
        var w = gs * (1.0 - 0.004 * (t + gs * 0.031 - 25.0)) * (p.eff / 100.0)
        if (p.limitW > 0 && p.kwp > 0.0) w = minOf(w, p.limitW / p.kwp)
        return maxOf(0.0, w)
    }

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

    fun kindOfHour(h: Hour): WeatherKind = when {
        h.code in 96..99 -> WeatherKind.Hail
        h.code in 95..99 -> WeatherKind.Thunder
        h.gust >= 75.0 -> WeatherKind.Storm
        h.code in 71..77 || h.code in 85..86 || h.snowCm >= 0.3 -> WeatherKind.Snow
        h.code in 51..67 || h.code in 80..82 || h.precip >= 0.3 -> WeatherKind.Rain
        h.cloud < 25.0 -> WeatherKind.Sun
        h.cloud < 60.0 -> WeatherKind.SunCloud
        else -> WeatherKind.Cloud
    }

    fun codeText(h: Hour): String = when (h.code) {
        0 -> "klar"
        1 -> "überwiegend klar"
        2 -> "teils bewölkt"
        3 -> "bedeckt"
        45, 48 -> "Nebel"
        in 51..57 -> "Niesel"
        in 61..65 -> "Regen"
        66, 67 -> "gefrierender Regen"
        in 71..77 -> "Schnee"
        in 80..82 -> "Regenschauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> if (h.cloud < 25.0) "klar" else if (h.cloud < 60.0) "teils bewölkt" else "bewölkt"
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
            val ts = time.getString(i) // 2024-05-17T13:00
            val w = whPerKwp(p, g, t, ts.substring(0, 10), ts.substring(11, 13).toInt())
            sums.getOrPut(ts.substring(0, 4).toInt()) { DoubleArray(12) }[ts.substring(5, 7).toInt() - 1] += w
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
        return ecGet("https://api.energy-charts.info/price?bzn=DE-LU&start=$today&end=${today.plusDays(2)}")
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

    val MIX_GROUPS = listOf("Kohle", "Erdgas", "Biomasse", "Wind", "Wasserkraft", "Pumpspeicher", "Batterie", "Solar", "Sonstige")
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
            n.contains("pumped") -> "Pumpspeicher"
            n.contains("battery") -> "Batterie"
            n.contains("hydro") -> "Wasserkraft"
            n == "solar" -> "Solar"
            else -> "Sonstige"
        }
    }

    /**
     * Nettostromerzeugung je Gruppe (MW) plus Import/Export (MW, beides positiv). Auf höchstens [maxPoints] Zeitpunkte
     * gemittelt. Schneidet hinten alles ab, wofür noch keine Erzeugungswerte vorliegen.
     */
    fun parseMixSeries(json: String, maxPoints: Int, cbet: java.util.TreeMap<Long, DoubleArray>? = null): MixSeries? {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val types = o.getJSONArray("production_types")
        val n = t.length()
        if (n == 0) return null
        val keys = MIX_GROUPS + listOf("Import", "Export", "Braunkohle", "Steinkohle", "PumpLaden", "BattLaden", "Last")
        val raw = keys.associateWith { DoubleArray(n) }
        var last = -1
        var haveTrade = false
        var loadArr: org.json.JSONArray? = null
        var rslArr: org.json.JSONArray? = null
        for (k in 0 until types.length()) {
            val ty = types.getJSONObject(k)
            val name = ty.optString("name")
            val d = ty.optJSONArray("data") ?: continue
            val lower = name.lowercase()
            if (lower == "load") loadArr = d
            if (lower == "renewable share of load") rslArr = d
            if (lower.contains("cross") && lower.contains("border")) {
                haveTrade = true
                for (i in 0 until minOf(n, d.length())) {
                    if (d.isNull(i)) continue
                    val v = d.getDouble(i)
                    if (v > 0) raw["Import"]!![i] = v else raw["Export"]!![i] = -v
                }
                continue
            }
            // Speicher: Laden (negative Werte oder Verbrauchsreihe) getrennt erfassen, Abgabe läuft als eigene Gruppe
            val stor = if (lower.contains("pumped")) "Pump" else if (lower.contains("battery")) "Batt" else null
            val isCharge = lower.contains("consum") || (lower.contains("charge") && !lower.contains("discharge"))
            if (stor != null) {
                for (i in 0 until minOf(n, d.length())) {
                    if (d.isNull(i)) continue
                    val v = d.getDouble(i)
                    if (isCharge) raw[stor + "Laden"]!![i] += Math.abs(v)
                    else if (v < 0) raw[stor + "Laden"]!![i] += -v
                }
                if (isCharge) continue
            }
            val g = mixGroup(name) ?: continue
            val a = raw[g]!!
            // Kohle zusätzlich getrennt (Braun-/Steinkohle) für die CO2-Schätzung
            val sub = if (g == "Kohle") raw[if (lower.contains("brown") || lower.contains("lignite")) "Braunkohle" else "Steinkohle"] else null
            for (i in 0 until minOf(n, d.length())) {
                if (d.isNull(i)) continue
                val v = d.getDouble(i)
                if (v > 0) {
                    a[i] += v
                    if (sub != null) sub[i] += v
                    if (i > last) last = i
                }
            }
        }
        if (last < 0) return null
        loadArr?.let { la0 -> for (i in 0 until minOf(n, la0.length())) if (!la0.isNull(i)) raw["Last"]!![i] = la0.getDouble(i) }
        // Handel über alle Grenzen einzeln (brutto): Import = Summe aller Einfuhren, Export = Summe aller Ausfuhren.
        // Die Gesamtzeile aus public_power ist nur der Saldo und würde Import und Export gegeneinander aufrechnen.
        if (cbet != null && cbet.isNotEmpty()) {
            for (i in 0..last) {
                val ts = t.getLong(i)
                val en = cbet.floorEntry(ts) ?: continue
                if (ts - en.key > 3600L) continue
                raw["Import"]!![i] = en.value[0]
                raw["Export"]!![i] = en.value[1]
            }
            haveTrade = true
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
        // Anteil am aktuellen Strombedarf: letzter Zeitpunkt mit Lastwert
        var solarShare: Double? = null
        var renShare: Double? = null
        var shareT = 0L
        val la = loadArr
        if (la != null) {
            var li = -1
            for (i in minOf(n, la.length()) - 1 downTo 0) {
                if (!la.isNull(i) && la.getDouble(i) > 1000.0) { li = i; break }
            }
            if (li >= 0) {
                val load = la.getDouble(li)
                solarShare = raw["Solar"]!![li] / load * 100.0
                val rsl = rslArr
                renShare = if (rsl != null && li < rsl.length() && !rsl.isNull(li)) rsl.getDouble(li)
                else (raw["Solar"]!![li] + raw["Wind"]!![li] + raw["Biomasse"]!![li] + raw["Wasserkraft"]!![li]) / load * 100.0
                shareT = t.getLong(li)
            }
        }
        return MixSeries(times, out, haveTrade || cbet != null, solarShare, renShare, shareT)
    }

    /** Grenzüberschreitender Handel je Land (GW), als MW: je Zeitpunkt [Summe der Einfuhren, Summe der Ausfuhren]. */
    private fun cbetMap(json: String): java.util.TreeMap<Long, DoubleArray> {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val cs = o.optJSONArray("countries") ?: return java.util.TreeMap()
        val m = java.util.TreeMap<Long, DoubleArray>()
        for (k in 0 until cs.length()) {
            val d = cs.getJSONObject(k).optJSONArray("data") ?: continue
            for (i in 0 until minOf(t.length(), d.length())) {
                if (d.isNull(i)) continue
                val v = d.getDouble(i) * 1000.0
                val a = m.getOrPut(t.getLong(i)) { DoubleArray(2) }
                if (v > 0) a[0] += v else a[1] += -v
            }
        }
        return m
    }

    // Energy-Charts erlaubt nur ca. 2 Anfragen pro Minute (Burst 4): einfacher Token-Bucket, damit keine Antworten mit HTTP 429 verloren gehen
    private val ecLock = Any()
    private var ecTokens = 4.0
    private var ecLast = System.currentTimeMillis()

    private fun ecGet(url: String): String {
        var wait = 0L
        synchronized(ecLock) {
            val now = System.currentTimeMillis()
            ecTokens = minOf(4.0, ecTokens + (now - ecLast) / 30000.0)
            ecLast = now
            if (ecTokens < 1.0) wait = ((1.0 - ecTokens) * 30000.0).toLong()
            ecTokens -= 1.0
        }
        if (wait > 0) Thread.sleep(wait)
        return try {
            Api.get(url, false)
        } catch (e: java.io.IOException) {
            if (e.message?.contains("429") == true) {
                Thread.sleep(30000)
                Api.get(url, false)
            } else throw e
        }
    }

    private val fcCache = HashMap<String, Pair<Long, java.util.TreeMap<Long, Double>>>()

    /** Prognosereihe (MW) je Zeitstempel aus public_power_forecast; leer bei Fehlern. */
    private fun forecastMap(type: String, day: LocalDate): java.util.TreeMap<Long, Double> {
        val ck = "$type|$day"
        synchronized(fcCache) { fcCache[ck] }?.let { if (System.currentTimeMillis() - it.first < 25L * 60 * 1000) return it.second }
        val m = java.util.TreeMap<Long, Double>()
        try {
            val o = JSONObject(ecGet("https://api.energy-charts.info/public_power_forecast?country=de&production_type=$type&forecast_type=current&start=$day&end=$day"))
            val t = o.getJSONArray("unix_seconds")
            val d = o.optJSONArray("forecast_values") ?: o.optJSONArray("data") ?: return m
            for (i in 0 until minOf(t.length(), d.length())) if (!d.isNull(i)) m[t.getLong(i)] = d.getDouble(i)
            if (m.isNotEmpty()) synchronized(fcCache) { fcCache[ck] = System.currentTimeMillis() to m }
        } catch (e: Exception) {
        }
        return m
    }

    /** Anteil Erneuerbarer/Solar an der Last laut Energy-Charts-Prognose (ren_share_forecast) zum Zeitpunkt ts: Paar (ren %, solar %) oder null. */
    private fun renShareAt(ts: Long): Pair<Double, Double?>? {
        try {
            val o = JSONObject(ecGet("https://api.energy-charts.info/ren_share_forecast?country=de"))
            val t = o.getJSONArray("unix_seconds")
            val r = o.optJSONArray("ren_share") ?: return null
            val so = o.optJSONArray("solar_share")
            var best = -1
            for (i in 0 until minOf(t.length(), r.length())) if (t.getLong(i) <= ts && !r.isNull(i)) best = i
            if (best < 0 || ts - t.getLong(best) > 3600L) return null
            val sol = if (so != null && best < so.length() && !so.isNull(best)) so.getDouble(best) else null
            return r.getDouble(best) to sol
        } catch (e: Exception) {
            return null
        }
    }

    /** Hängt hinter den letzten Messwert bis jetzt vorläufige Punkte an: Solar und Wind aus der Prognose, alles andere fortgeschrieben. */
    private fun extendToNow(s: MixSeries, day: LocalDate): MixSeries {
        val n = s.t.size
        if (n < 2) return s
        val stepS = s.t[n - 1] - s.t[n - 2]
        if (stepS != 900L) return s
        val now = System.currentTimeMillis() / 1000
        val lastT = s.t[n - 1]
        val add = ((now - lastT) / 900).toInt().coerceAtMost(16)
        if (add < 1) return s
        val hourNow = java.time.ZonedDateTime.now(berlin).hour
        val solarNow = s.v["Solar"]?.get(n - 1) ?: 0.0
        val solar = if (solarNow < 50.0 && (hourNow >= 17 || hourNow < 7)) java.util.TreeMap<Long, Double>() else forecastMap("solar", day)
        val loadFc = java.util.TreeMap<Long, Double>()
        val wind = java.util.TreeMap<Long, Double>()
        for (ty in listOf("wind_onshore", "wind_offshore")) for ((k, v) in forecastMap(ty, day)) wind[k] = (wind[k] ?: 0.0) + v
        if (solar.isEmpty() && wind.isEmpty()) return s
        fun at(m: java.util.TreeMap<Long, Double>, ts: Long): Double? {
            val e = m.floorEntry(ts) ?: return null
            return if (ts - e.key <= 3600L) e.value else null
        }
        val t2 = LongArray(n + add) { if (it < n) s.t[it] else lastT + 900L * (it - n + 1) }
        val v2 = HashMap<String, DoubleArray>()
        for ((k, arr) in s.v) {
            val na = DoubleArray(n + add) { if (it < n) arr[it] else arr[n - 1] }
            val fc = when (k) { "Solar" -> solar; "Wind" -> wind; "Last" -> loadFc; else -> null }
            if (fc != null && fc.isNotEmpty()) for (i in n until n + add) at(fc, t2[i])?.let { na[i] = it.coerceAtLeast(0.0) }
            v2[k] = na
        }
        // Anteile am Verbrauch auf den vorläufigen Zeitpunkt umrechnen: Last aus Prognose, sonst vom letzten Messwert abgeleitet
        var sShare = s.solarShare
        var rShare = s.renShare
        var sT = s.shareT
        val mi = s.t.indexOf(s.shareT)
        val lastI = n + add - 1
        var load: Double? = at(loadFc, t2[lastI])?.takeIf { it > 1000.0 }
        if (load == null) load = v2["Last"]?.get(lastI)?.takeIf { it > 1000.0 }
        if (load == null && mi >= 0 && s.solarShare != null && s.solarShare > 0.5) load = (s.v["Solar"]?.get(mi) ?: 0.0) / s.solarShare * 100.0
        if (load != null && load > 1000.0) {
            fun g(k: String) = v2[k]?.get(lastI) ?: 0.0
            sShare = g("Solar") / load * 100.0
            rShare = (g("Solar") + g("Wind") + g("Biomasse") + g("Wasserkraft")) / load * 100.0
            sT = t2[lastI]
        }
        // Direkt aus der Energy-Charts-Prognose (Anteil an der prognostizierten Last), wenn verfügbar
        renShareAt(t2[lastI])?.let { (rs, ss) ->
            rShare = rs
            if (ss != null) sShare = ss
            sT = t2[lastI]
        }
        return MixSeries(t2, v2, s.hasTrade, sShare, rShare, sT, n)
    }

    /** range: 0 = Tag (heute, sonst gestern), 1 = Woche (7 Tage), 2 = Jahr (365 Tage). */
    suspend fun loadMixSeries(range: Int): MixSeries? = withContext(Dispatchers.IO) {
        val ttl = when (range) { 0 -> 20L * 60 * 1000; 1 -> 60L * 60 * 1000; else -> 6L * 3600 * 1000 }
        synchronized(mixCache) { mixCache[range] }?.let { if (System.currentTimeMillis() - it.first < ttl) return@withContext it.second }
        val today = LocalDate.now(berlin)
        fun fetch(from: LocalDate, to: LocalDate, max: Int): MixSeries? {
            val base = "https://api.energy-charts.info"
            val pj = ecGet("$base/public_power?country=de&start=$from&end=$to")
            val trade = try {
                cbetMap(ecGet("$base/cbet?country=de&start=$from&end=$to"))
            } catch (e: Exception) {
                null
            }
            return parseMixSeries(pj, max, trade)
        }
        var s = when (range) {
            0 -> fetch(today, today, 96) ?: fetch(today.minusDays(1), today.minusDays(1), 96)
            1 -> fetch(today.minusDays(6), today, 168)
            else -> fetch(today.minusDays(364), today, 183)
        }
        if (s != null && range == 0 && Instant.ofEpochSecond(s.t.last()).atZone(berlin).toLocalDate() == today) {
            s = try { extendToNow(s, today) } catch (e: Exception) { s }
        }
        if (s != null) synchronized(mixCache) { mixCache[range] = System.currentTimeMillis() to s }
        s
    }

    private val profileCache = HashMap<String, Pair<Long, ProfileData>>()

    fun parseProfile(json: String): ProfileData {
        val o = JSONObject(json)
        val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val n = t.length()
        fun arr(name: String): DoubleArray {
            val a = h.optJSONArray(name) ?: return DoubleArray(n) { Double.NaN }
            return DoubleArray(n) { i -> if (a.isNull(i)) Double.NaN else a.getDouble(i) }
        }
        val levels = listOf(1000, 975, 950, 925, 900, 850, 800, 700, 600, 500, 400, 300, 250, 200, 150)
        val ccs = levels.map { arr("cloud_cover_" + it + "hPa") }
        val ghs = levels.map { arr("geopotential_height_" + it + "hPa") }
        return ProfileData(
            timezone = o.optString("timezone", "Europe/Berlin"),
            time = List(n) { t.getString(it) },
            levels = levels,
            cc = List(n) { i -> DoubleArray(levels.size) { k -> ccs[k][i] } },
            gh = List(n) { i -> DoubleArray(levels.size) { k -> ghs[k][i] } },
            low = arr("cloud_cover_low"), mid = arr("cloud_cover_mid"), high = arr("cloud_cover_high"),
            rain = arr("precipitation")
        )
    }

    suspend fun loadProfile(p: Plant): ProfileData = withContext(Dispatchers.IO) {
        val key = p.lat.toString() + "/" + p.lon
        synchronized(profileCache) { profileCache[key] }?.let { if (System.currentTimeMillis() - it.first < 30L * 60 * 1000) return@withContext it.second }
        val d = parseProfile(Api.profileJson(p))
        synchronized(profileCache) { profileCache[key] = System.currentTimeMillis() to d }
        d
    }

    /** Tagessummen der Einstrahlung (Wh/m²) je Datum: Referenzlauf und Ensemble-Mitglieder desselben Modells. */
    class EnsRaw(val det: Map<String, Double>, val members: List<Map<String, Double>>)

    suspend fun loadEns(p: Plant): EnsRaw = withContext(Dispatchers.IO) {
        fun sums(t: org.json.JSONArray, a: org.json.JSONArray): Map<String, Double> {
            val m = HashMap<String, Double>()
            for (i in 0 until minOf(t.length(), a.length())) if (!a.isNull(i)) {
                val d = t.getString(i).substring(0, 10)
                m[d] = (m[d] ?: 0.0) + a.getDouble(i)
            }
            return m
        }
        val base = "latitude=${p.lat}&longitude=${p.lon}&hourly=shortwave_radiation&models=icon_seamless&forecast_days=7&timezone=auto"
        val dh = JSONObject(Api.get("https://api.open-meteo.com/v1/forecast?$base", false)).getJSONObject("hourly")
        val det = sums(dh.getJSONArray("time"), dh.getJSONArray("shortwave_radiation"))
        val eh = JSONObject(Api.get("https://ensemble-api.open-meteo.com/v1/ensemble?$base", false)).getJSONObject("hourly")
        val et = eh.getJSONArray("time")
        val members = ArrayList<Map<String, Double>>()
        val it = eh.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k.startsWith("shortwave_radiation")) members.add(sums(et, eh.getJSONArray(k)))
        }
        EnsRaw(det, members)
    }

    val COMPARE_MODELS = listOf("icon_seamless" to "ICON", "ecmwf_ifs025" to "ECMWF", "gfs_seamless" to "GFS", "meteofrance_seamless" to "Arpège")

    /** Tagessummen der Einstrahlung je Modell: Modell-Id -> Datum -> Wh/m². */
    suspend fun loadModels(p: Plant): Map<String, Map<String, Double>> = withContext(Dispatchers.IO) {
        val ids = COMPARE_MODELS.joinToString(",") { it.first }
        val h = JSONObject(
            Api.get("https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}&hourly=shortwave_radiation,sunshine_duration,cloud_cover,temperature_2m,precipitation,snowfall,weather_code,wind_gusts_10m,uv_index&models=$ids&forecast_days=7&timezone=auto", false)
        ).getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val out = HashMap<String, Map<String, Double>>()
        for ((id, _) in COMPARE_MODELS) {
            val a = h.optJSONArray("shortwave_radiation_$id") ?: continue
            val m = HashMap<String, Double>()
            for (i in 0 until minOf(t.length(), a.length())) if (!a.isNull(i)) {
                val d = t.getString(i).substring(0, 10)
                m[d] = (m[d] ?: 0.0) + a.getDouble(i)
            }
            out[id] = m
            for ((suffix, key) in listOf("cloud" to "cloud_cover", "temp" to "temperature_2m", "rain" to "precipitation", "snow" to "snowfall", "code" to "weather_code", "gust" to "wind_gusts_10m", "uv" to "uv_index")) {
                val ha = h.optJSONArray(key + "_" + id) ?: continue
                val hm = HashMap<String, Double>()
                for (i in 0 until minOf(t.length(), ha.length())) if (!ha.isNull(i)) hm[t.getString(i)] = ha.getDouble(i)
                out["$id#$suffix"] = hm
            }
            val sa = h.optJSONArray("sunshine_duration_$id")
            if (sa != null) {
                val sm = HashMap<String, Double>()
                for (i in 0 until minOf(t.length(), sa.length())) if (!sa.isNull(i)) {
                    val d = t.getString(i).substring(0, 10)
                    sm[d] = (sm[d] ?: 0.0) + sa.getDouble(i) / 3600.0
                }
                out["$id#sun"] = sm
            }
        }
        out
    }

    class GasStorage(val full: Double, val avg: Double?, val date: String)

    private var gasCache: Pair<Long, GasStorage>? = null

    /** Füllstand der deutschen Gasspeicher (AGSI+/GIE) und Durchschnitt derselben Tage der letzten 5 Jahre. Ohne Schlüssel null. */
    suspend fun loadGas(): GasStorage? = withContext(Dispatchers.IO) {
        val key = BuildConfig.AGSI_KEY.trim()
        if (key.isBlank()) return@withContext null
        gasCache?.let { if (System.currentTimeMillis() - it.first < 6L * 3600 * 1000) return@withContext it.second }
        fun fetch(from: LocalDate, to: LocalDate): org.json.JSONArray? {
            val c = java.net.URL("https://agsi.gie.eu/api?country=de&from=$from&to=$to&size=20").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("x-key", key)
            c.setRequestProperty("User-Agent", "Solarprognose-Android/" + BuildConfig.VERSION_NAME)
            try {
                if (c.responseCode !in 200..299) return null
                return JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data")
            } finally {
                c.disconnect()
            }
        }
        val today = LocalDate.now(berlin)
        val cur = fetch(today.minusDays(5), today) ?: return@withContext null
        var best: LocalDate? = null
        var full = 0.0
        for (i in 0 until cur.length()) {
            val o = cur.getJSONObject(i)
            val dt = LocalDate.parse(o.getString("gasDayStart"))
            val f = o.optString("full").toDoubleOrNull() ?: continue
            if (best == null || dt.isAfter(best)) { best = dt; full = f }
        }
        val bd = best ?: return@withContext null
        val past = ArrayList<Double>()
        for (n in 1..5) {
            try {
                val dd = bd.minusYears(n.toLong())
                val arr = fetch(dd, dd)
                val v = arr?.optJSONObject(0)?.optString("full")?.toDoubleOrNull()
                if (v != null) past.add(v)
            } catch (e: Exception) {
            }
        }
        val g = GasStorage(full, if (past.size >= 3) past.average() else null, bd.toString())
        gasCache = System.currentTimeMillis() to g
        g
    }

    class ObsCloud(val pct: Double, val station: String?)

    /** Beobachtete Bewölkung (DWD über Bright Sky), auch nachts verfügbar. */
    suspend fun loadObsCloud(p: Plant): ObsCloud? = withContext(Dispatchers.IO) {
        val o = JSONObject(Api.get("https://api.brightsky.dev/current_weather?lat=${p.lat}&lon=${p.lon}", false))
        val w = o.optJSONObject("weather") ?: return@withContext null
        if (w.isNull("cloud_cover")) return@withContext null
        val src = o.optJSONArray("sources")?.optJSONObject(0)?.optString("station_name")?.takeIf { it.isNotBlank() }
        ObsCloud(w.getDouble("cloud_cover"), src)
    }

    /** Gemessene Einstrahlung (Satellit, auf die Modulfläche gerechnet) für heute: Zeit "yyyy-MM-ddTHH:mm" -> W/m². */
    suspend fun loadSatGti(p: Plant): Map<String, Double> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val o = JSONObject(
            Api.get(
                "https://satellite-api.open-meteo.com/v1/archive?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}" +
                    "&hourly=global_tilted_irradiance&models=satellite_radiation_seamless&start_date=$today&end_date=$today&timezone=auto", false
            )
        )
        val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val g = h.getJSONArray("global_tilted_irradiance")
        val m = HashMap<String, Double>()
        for (i in 0 until minOf(t.length(), g.length())) if (!g.isNull(i)) m[t.getString(i)] = g.getDouble(i)
        m
    }

    private val ytdCache = HashMap<String, Pair<Long, YtdData>>()

    /** Dieses Jahr bisher je Monat: Archiv (bis etwa vor 5 Tagen), die letzten Tage aus der Prognose-Schnittstelle (past_days). */
    suspend fun loadYtd(p: Plant): YtdData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val key = yearSig(p) + "/" + today
        synchronized(ytdCache) { ytdCache[key] }?.let { if (System.currentTimeMillis() - it.first < 3L * 3600 * 1000) return@withContext it.second }
        val sums = DoubleArray(12)
        var last = ""
        fun add(json: String, after: String, nowKey: String?) {
            val h = JSONObject(json).getJSONObject("hourly")
            val time = h.getJSONArray("time")
            val gti = h.getJSONArray("global_tilted_irradiance")
            val tmp = h.getJSONArray("temperature_2m")
            for (i in 0 until time.length()) {
                if (gti.isNull(i)) continue
                val ts = time.getString(i)
                val d = ts.substring(0, 10)
                if (d <= after || !d.startsWith(today.year.toString())) continue
                if (nowKey != null && ts.substring(0, 13) > nowKey) continue
                val g = gti.getDouble(i).coerceAtLeast(0.0)
                val t = if (tmp.isNull(i)) 10.0 else tmp.getDouble(i)
                val w = whPerKwp(p, g, t, d, ts.substring(11, 13).toInt())
                sums[ts.substring(5, 7).toInt() - 1] += w
                if (d > last) last = d
            }
        }
        if (today.dayOfYear > 1) {
            val json = Api.get(
                "https://archive-api.open-meteo.com/v1/archive?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}" +
                    "&start_date=${today.year}-01-01&end_date=${today.minusDays(1)}&hourly=global_tilted_irradiance,temperature_2m&timezone=auto", false
            )
            add(json, "", null)
        }
        if (last < today.toString()) {
            val json = Api.get(
                "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}" +
                    "&hourly=global_tilted_irradiance,temperature_2m&past_days=10&forecast_days=1&timezone=auto", false
            )
            val z = try { java.time.ZoneId.of(JSONObject(json).optString("timezone", "Europe/Berlin")) } catch (e: Exception) { java.time.ZoneId.systemDefault() }
            add(json, last, java.time.LocalDateTime.now(z).toString().substring(0, 13))
        }
        val d = YtdData(sums.map { it / 1000.0 })
        synchronized(ytdCache) { ytdCache[key] = System.currentTimeMillis() to d }
        d
    }
}
