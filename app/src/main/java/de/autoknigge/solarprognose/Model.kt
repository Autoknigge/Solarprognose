package de.autoknigge.solarprognose

import org.json.JSONObject

/** Eine PV-Anlage (Dachfläche) mit Standort und Anlagendaten. */
data class Plant(
    val id: String,
    val name: String,
    val place: String,
    val lat: Double,
    val lon: Double,
    val kwp: Double,
    val tilt: Int,
    val az: Int,
    val eff: Int,
    val limitW: Int
) {
    /** Alles, was die Einstrahlungsdaten beim Anbieter beeinflusst. */
    val geoSig: String get() = "$lat,$lon,$tilt,$az"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("place", place)
        .put("lat", lat)
        .put("lon", lon)
        .put("kwp", kwp)
        .put("tilt", tilt)
        .put("az", az)
        .put("eff", eff)
        .put("limitW", limitW)

    companion object {
        fun fromJson(o: JSONObject) = Plant(
            id = o.getString("id"),
            name = o.getString("name"),
            place = o.optString("place", ""),
            lat = o.getDouble("lat"),
            lon = o.getDouble("lon"),
            kwp = o.getDouble("kwp"),
            tilt = o.getInt("tilt"),
            az = o.getInt("az"),
            eff = o.optInt("eff", 85),
            limitW = o.optInt("limitW", 0)
        )
    }
}

/** Rohdaten vom Wetterdienst, stündlich. Zeiten sind lokale Ortszeit ohne Offset. */
data class Raw(
    val timezone: String,
    val time: List<String>,
    val gti: List<Double>,
    val temp: List<Double>,
    val cloud: List<Double>,
    val precip: List<Double> = emptyList(),
    val snow: List<Double> = emptyList(),
    val code: List<Double> = emptyList(),
    val gust: List<Double> = emptyList()
)

/** Eine Stunde. Der Wert gilt für die Stunde, die zur angegebenen Uhrzeit endet. */
data class Hour(
    val time: String,
    val date: String,
    val hour: Int,
    val gti: Double,
    val temp: Double,
    val cloud: Double,
    val power: Double,
    val precip: Double = 0.0,
    val snowCm: Double = 0.0,
    val code: Int = 0,
    val gust: Double = 0.0
)

data class Day(
    val date: String,
    val start: Int,
    val end: Int,
    val kwh: Double,
    val peak: Double,
    val peakHour: Int,
    val cloudAvg: Double?,
    val tempAvg: Double,
    val rainMm: Double = 0.0,
    val snowCm: Double = 0.0,
    val thunder: Boolean = false,
    val hail: Boolean = false,
    val gustMax: Double = 0.0
)

/** [todayIndex]: Position von heute in [days]. Ohne Rückblick ist das 0, mit 2 Tagen Rückblick 2. */
data class Forecast(val timezone: String, val hours: List<Hour>, val days: List<Day>, val todayIndex: Int = 0)

/** Kurzfassung für Widget und Benachrichtigung. */
data class Summary(
    val name: String,
    val todayKwh: Double,
    val tomorrowKwh: Double?,
    val peakToday: Double,
    val ts: Long
) {
    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("today", todayKwh)
        .put("tomorrow", tomorrowKwh ?: JSONObject.NULL)
        .put("peak", peakToday)
        .put("ts", ts)
        .toString()

    companion object {
        fun fromJson(s: String): Summary? = try {
            val o = JSONObject(s)
            Summary(
                o.getString("name"),
                o.getDouble("today"),
                if (o.isNull("tomorrow")) null else o.getDouble("tomorrow"),
                o.getDouble("peak"),
                o.getLong("ts")
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class GeoPlace(
    val name: String,
    val admin1: String,
    val admin2: String,
    val country: String,
    val lat: Double,
    val lon: Double
) {
    val label: String
        get() = listOf(name, admin2, admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}
