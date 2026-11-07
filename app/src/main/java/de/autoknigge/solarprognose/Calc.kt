package de.autoknigge.solarprognose

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

object Calc {

    fun parse(json: String): Raw {
        val o = JSONObject(json)
        val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val n = t.length()
        fun arr(name: String): List<Double> {
            val a = h.getJSONArray(name)
            return List(n) { i -> if (a.isNull(i)) Double.NaN else a.getDouble(i) }
        }
        return Raw(
            timezone = o.optString("timezone", "Europe/Berlin"),
            time = List(n) { t.getString(it) },
            gti = arr("global_tilted_irradiance"),
            temp = arr("temperature_2m"),
            cloud = arr("cloud_cover"),
            precip = if (h.has("precipitation")) arr("precipitation") else emptyList(),
            snow = if (h.has("snowfall")) arr("snowfall") else emptyList(),
            code = if (h.has("weather_code")) arr("weather_code") else emptyList(),
            gust = if (h.has("wind_gusts_10m")) arr("wind_gusts_10m") else emptyList()
        )
    }

    /**
     * Leistung je Stunde: Modulleistung x Einstrahlung/1000 x Temperaturfaktor x Systemwirkungsgrad.
     * Zelltemperatur = Lufttemperatur + 0,031 K je W/m², Temperaturkoeffizient -0,4 %/K über 25 °C.
     */
    fun compute(raw: Raw, p: Plant, keepPast: Boolean = false): Forecast {
        val zone = try {
            ZoneId.of(raw.timezone)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
        val today = LocalDate.now(zone).toString()
        val hours = ArrayList<Hour>(raw.time.size)
        for (i in raw.time.indices) {
            val t = raw.time[i]
            if (!keepPast && t.substring(0, 10) < today) continue
            val g = raw.gti[i].let { if (it.isNaN()) 0.0 else it }
            val temp = raw.temp[i].let { if (it.isNaN()) 10.0 else it }
            val cloud = raw.cloud[i].let { if (it.isNaN()) 0.0 else it }
            val tcell = temp + g * 0.031
            var pw = p.kwp * 1000.0 * (g / 1000.0) * (1.0 - 0.004 * (tcell - 25.0)) * (p.eff / 100.0)
            if (p.limitW > 0) pw = minOf(pw, p.limitW.toDouble())
            pw = maxOf(0.0, pw)
            val rain = raw.precip.getOrNull(i)?.let { if (it.isNaN()) 0.0 else it } ?: 0.0
            val snow = raw.snow.getOrNull(i)?.let { if (it.isNaN()) 0.0 else it } ?: 0.0
            val code = raw.code.getOrNull(i)?.let { if (it.isNaN()) 0 else it.toInt() } ?: 0
            val gust = raw.gust.getOrNull(i)?.let { if (it.isNaN()) 0.0 else it } ?: 0.0
            hours.add(Hour(t, t.substring(0, 10), t.substring(11, 13).toInt(), g, temp, cloud, pw, rain, snow, code, gust))
        }
        val days = ArrayList<Day>()
        var i = 0
        while (i < hours.size) {
            val date = hours[i].date
            var j = i
            var kwh = 0.0
            var peak = 0.0
            var peakH = 0
            var cs = 0.0
            var cn = 0
            var ts = 0.0
            var rain = 0.0
            var snow = 0.0
            var thunder = false
            var hail = false
            var gustMax = 0.0
            while (j < hours.size && hours[j].date == date) {
                val h = hours[j]
                kwh += h.power / 1000.0
                if (h.power > peak) {
                    peak = h.power
                    peakH = h.hour
                }
                if (h.gti > 20.0) {
                    cs += h.cloud
                    cn++
                    rain += h.precip
                    snow += h.snowCm
                    if (h.code == 95 || h.code == 96 || h.code == 99) thunder = true
                    if (h.code == 96 || h.code == 99) hail = true
                    if (h.gust > gustMax) gustMax = h.gust
                }
                ts += h.temp
                j++
            }
            days.add(Day(date, i, j, kwh, peak, peakH, if (cn > 0) cs / cn else null, ts / (j - i), rain, snow, thunder, hail, gustMax))
            i = j
        }
        val ti = days.indexOfFirst { it.date >= today }.let { if (it < 0) 0 else it }
        return Forecast(raw.timezone, hours, days, ti)
    }

    /** Aktuelle Position im Stundenraster (Index + Minutenanteil) oder null, wenn "jetzt" nicht im Datensatz liegt. */
    fun nowCoord(f: Forecast): Double? {
        val zone = try {
            ZoneId.of(f.timezone)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
        val n = ZonedDateTime.now(zone)
        val key = n.toLocalDate().toString() + "T" + String.format(Locale.US, "%02d:00", n.hour)
        val idx = f.hours.indexOfFirst { it.time == key }
        return if (idx < 0) null else idx + n.minute / 60.0
    }

    fun remainingToday(f: Forecast, nowC: Double): Double {
        val d = f.days.firstOrNull() ?: return 0.0
        var r = 0.0
        for (i in d.start until d.end) {
            r += f.hours[i].power / 1000.0 * (i - nowC).coerceIn(0.0, 1.0)
        }
        return r
    }

    fun summarize(f: Forecast, p: Plant): Summary {
        val d0 = f.days.first()
        return Summary(p.name, d0.kwh, f.days.getOrNull(1)?.kwh, d0.peak, System.currentTimeMillis())
    }

    // ---------- Darstellung ----------

    fun azName(a: Int): String {
        val n = Math.abs(a)
        val side = if (a < 0) "ost" else "west"
        return when {
            n <= 10 -> "Süd"
            n >= 170 -> "Nord"
            n in 80..100 -> if (a < 0) "Ost" else "West"
            n < 80 -> "Süd$side"
            else -> "Nord$side"
        }
    }

    fun weekday(date: String): String =
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "")

    fun dm(date: String): String = date.substring(8, 10) + "." + date.substring(5, 7) + "."

    fun fmt1(v: Double): String = String.format(Locale.GERMANY, "%.1f", v)
    fun fmt0(v: Double): String = String.format(Locale.GERMANY, "%,.0f", v)

    /** Schrittweite und Obergrenze für die Y-Achse. */
    fun niceScale(max: Double, steps: DoubleArray, maxTicks: Int): Pair<Double, Double> {
        val top = maxOf(max, steps[0] * 2)
        var st = steps.last()
        for (s in steps) {
            if (top / s <= maxTicks) {
                st = s
                break
            }
        }
        return Pair(st, Math.ceil(top / st) * st)
    }

    /** Sonnenaufgang und -untergang (Ortszeit, "HH:mm") nach dem NOAA-Verfahren; null bei Polartag/-nacht. */
    fun sunTimes(lat: Double, lon: Double, timezone: String): Pair<String, String>? {
        val zone = try { ZoneId.of(timezone) } catch (e: Exception) { ZoneId.systemDefault() }
        val date = LocalDate.now(zone)
        val g = 2 * Math.PI / 365.0 * (date.dayOfYear - 1 + 0.5)
        val eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) -
            0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g))
        val decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) -
            0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) -
            0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g)
        val la = Math.toRadians(lat)
        val cosH = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl)
        if (cosH < -1.0 || cosH > 1.0) return null
        val ha = Math.toDegrees(Math.acos(cosH))
        val rise = 720 - 4 * (lon + ha) - eq
        val set = 720 - 4 * (lon - ha) - eq
        fun fmt(minUtc: Double): String {
            val t = date.atStartOfDay(java.time.ZoneOffset.UTC).plusSeconds(Math.round(minUtc * 60)).withZoneSameInstant(zone)
            return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
        }
        return fmt(rise) to fmt(set)
    }
}
