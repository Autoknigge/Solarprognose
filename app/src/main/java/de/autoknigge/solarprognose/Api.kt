package de.autoknigge.solarprognose

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Zwei Betriebsarten:
 * - API_BASE_URL gesetzt: Die App spricht nur den eigenen Worker an (Release). Ein Open-Meteo-Schlüssel, falls vorhanden, liegt dort.
 * - API_BASE_URL leer: Direktabruf bei Open-Meteo über die kostenlose Schnittstelle (für Tests auf dem eigenen Handy).
 */
object Api {
    internal val proxy: String = BuildConfig.API_BASE_URL.trim().trimEnd('/')
    private val token: String = BuildConfig.APP_TOKEN

    val hasProxy: Boolean get() = proxy.isNotBlank()

    internal fun get(url: String, sendToken: Boolean): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 45_000
        c.setRequestProperty("User-Agent", "Solarprognose-Android/" + BuildConfig.VERSION_NAME)
        if (sendToken && token.isNotBlank()) c.setRequestProperty("X-App-Token", token)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP " + c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun forecastJson(p: Plant): String {
        val params = "latitude=${p.lat}&longitude=${p.lon}&tilt=${p.tilt}&azimuth=${p.az}"
        if (proxy.isNotBlank()) return get("$proxy/forecast?$params", true)
        return get(
            "https://api.open-meteo.com/v1/forecast?$params" +
                "&hourly=global_tilted_irradiance,temperature_2m,cloud_cover,precipitation,snowfall,weather_code,wind_gusts_10m,uv_index,sunshine_duration&forecast_days=7&past_days=2&timezone=auto",
            false
        )
    }

    /** Wolkenprofil und Regen. Zusatzdaten: Fehler dürfen die Hauptansicht nicht stören. Ohne Worker-Route direkt bei Open-Meteo. */
    fun profileJson(p: Plant): String {
        val params = "latitude=${p.lat}&longitude=${p.lon}"
        if (proxy.isNotBlank()) {
            try {
                return get("$proxy/profile?$params", true)
            } catch (e: IOException) {
            }
        }
        return get(
            "https://api.open-meteo.com/v1/forecast?$params" +
                "&hourly=precipitation,cloud_cover_low,cloud_cover_mid,cloud_cover_high,cloud_cover_1000hPa,cloud_cover_975hPa,cloud_cover_950hPa,cloud_cover_925hPa,cloud_cover_900hPa,cloud_cover_850hPa,cloud_cover_800hPa,cloud_cover_700hPa,cloud_cover_600hPa,cloud_cover_500hPa,cloud_cover_400hPa,cloud_cover_300hPa,cloud_cover_250hPa,cloud_cover_200hPa,cloud_cover_150hPa,geopotential_height_1000hPa,geopotential_height_975hPa,geopotential_height_950hPa,geopotential_height_925hPa,geopotential_height_900hPa,geopotential_height_850hPa,geopotential_height_800hPa,geopotential_height_700hPa,geopotential_height_600hPa,geopotential_height_500hPa,geopotential_height_400hPa,geopotential_height_300hPa,geopotential_height_250hPa,geopotential_height_200hPa,geopotential_height_150hPa&forecast_days=5&timezone=auto",
            false
        )
    }

    fun geocode(query: String): List<GeoPlace> {
        val name = URLEncoder.encode(query.trim(), "UTF-8")
        val body = if (proxy.isNotBlank()) {
            get("$proxy/search?name=$name", true)
        } else {
            get("https://geocoding-api.open-meteo.com/v1/search?name=$name&count=8&language=de&format=json", false)
        }
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            GeoPlace(
                name = r.optString("name"),
                admin1 = r.optString("admin1"),
                admin2 = r.optString("admin2"),
                country = r.optString("country"),
                lat = r.getDouble("latitude"),
                lon = r.getDouble("longitude"),
                post = r.optJSONArray("postcodes")?.let { if (it.length() > 0) it.optString(0) else "" } ?: ""
            )
        }
    }
}
