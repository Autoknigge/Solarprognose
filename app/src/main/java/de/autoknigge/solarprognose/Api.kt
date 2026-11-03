package de.autoknigge.solarprognose

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Zwei Betriebsarten:
 * - API_BASE_URL gesetzt: Die App spricht nur den eigenen Worker an (Release mit Werbung). Der Open-Meteo-Schlüssel liegt dort.
 * - API_BASE_URL leer: Direktabruf bei Open-Meteo über die kostenlose Schnittstelle. Nur für private Tests erlaubt,
 *   weil Apps mit Werbung laut Nutzungsbedingungen einen kostenpflichtigen Tarif brauchen.
 */
object Api {
    private val proxy: String = BuildConfig.API_BASE_URL.trim().trimEnd('/')
    private val token: String = BuildConfig.APP_TOKEN

    private fun get(url: String, sendToken: Boolean): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
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
                "&hourly=global_tilted_irradiance,temperature_2m,cloud_cover&forecast_days=7&timezone=auto",
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
                lon = r.getDouble("longitude")
            )
        }
    }
}
