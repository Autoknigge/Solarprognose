package de.autoknigge.solarprognose

import android.content.Context
import org.json.JSONArray

/** Lokale Speicherung: Anlagen, Einstellungen, zuletzt geladene Prognose. Es verlässt nichts das Gerät. */
class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("solar", Context.MODE_PRIVATE)

    fun plants(): List<Plant> {
        val s = sp.getString("plants", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { Plant.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlants(list: List<Plant>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        sp.edit().putString("plants", a.toString()).apply()
    }

    var selectedId: String?
        get() = sp.getString("selected", null)
        set(v) {
            sp.edit().putString("selected", v).apply()
        }

    /** Gewählte Anlagen für die Gesamtansicht (Kennungen, erste = Hauptanlage). */
    var selectedIds: List<String>
        get() = sp.getString("selected_ids", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        set(v) {
            sp.edit().putString("selected_ids", v.joinToString(",")).apply()
        }

    var notifyOn: Boolean
        get() = sp.getBoolean("notify_on", false)
        set(v) {
            sp.edit().putBoolean("notify_on", v).apply()
        }

    var notifyHour: Int
        get() = sp.getInt("notify_hour", 7)
        set(v) {
            sp.edit().putInt("notify_hour", v).apply()
        }


    fun saveSummary(s: Summary) {
        sp.edit().putString("summary", s.toJson()).apply()
    }

    fun summary(): Summary? = sp.getString("summary", null)?.let { Summary.fromJson(it) }
}
