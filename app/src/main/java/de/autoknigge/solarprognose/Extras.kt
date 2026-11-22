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

/** Dieses Jahr bisher je Monat (kWh je kWp), aus dem Wetter berechnet. */
data class YtdData(val perKwp: List<Double>)

/** Strommix-Zeitreihe: [t] Sekunden, [v] je Gruppe MW (Gruppen plus "Import" und "Export"). */
class MixSeries(
    val t: LongArray, val v: Map<String, DoubleArray>, val hasTrade: Boolean,
    /** Anteil von Solar und von allen Erneuerbaren am aktuellen Strombedarf (%), mit Zeitpunkt in Sekunden. */
    val solarShare: Double? = null, val renShare: Double? = null, val shareT: Long = 0L
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
    fun parseMixSeries(json: String, maxPoints: Int, cbet: java.util.TreeMap<Long, DoubleArray>? = null): MixSeries? {
        val o = JSONObject(json)
        val t = o.getJSONArray("unix_seconds")
        val types = o.getJSONArray("production_types")
        val n = t.length()
        if (n == 0) return null
        val keys = MIX_GROUPS + listOf("Import", "Export", "Braunkohle", "Steinkohle")
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

    /** range: 0 = Tag (heute, sonst gestern), 1 = Woche (7 Tage), 2 = Jahr (365 Tage). */
    suspend fun loadMixSeries(range: Int): MixSeries? = withContext(Dispatchers.IO) {
        val ttl = when (range) { 0 -> 20L * 60 * 1000; 1 -> 60L * 60 * 1000; else -> 6L * 3600 * 1000 }
        synchronized(mixCache) { mixCache[range] }?.let { if (System.currentTimeMillis() - it.first < ttl) return@withContext it.second }
        val today = LocalDate.now(berlin)
        fun fetch(from: LocalDate, to: LocalDate, max: Int): MixSeries? {
            val base = "https://api.energy-charts.info"
            val pj = Api.get("$base/public_power?country=de&start=$from&end=$to", false)
            val trade = try {
                cbetMap(Api.get("$base/cbet?country=de&start=$from&end=$to", false))
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
