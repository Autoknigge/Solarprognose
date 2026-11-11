package de.autoknigge.solarprognose

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val store = Store(ctx)

    var plants by mutableStateOf(store.plants())
        private set

    /** Angezeigte Anlagen. Mehrere ergeben die Gesamtansicht. */
    var selectedIds by mutableStateOf<List<String>>(emptyList())
        private set

    var raws by mutableStateOf<Map<String, Raw>>(emptyMap())
        private set

    private var running by mutableIntStateOf(0)

    val loading: Boolean get() = running > 0

    var error by mutableStateOf<String?>(null)
        private set

    var fetchedAt by mutableLongStateOf(0L)
        private set

    /** Jahreswerte je kWp (Open-Meteo-Archiv oder PVGIS) je Anlage, falls geladen. */
    var years by mutableStateOf<Map<String, YearData>>(emptyMap())
        private set

    /** Börsenpreise ab heute, falls geladen. */
    var prices by mutableStateOf<List<PriceHour>?>(null)
    var mix by mutableStateOf<MixData?>(null)
        private set

    var notifyOn by mutableStateOf(store.notifyOn)
        private set

    var notifyHour by mutableStateOf(store.notifyHour)
        private set

    val selectedPlants: List<Plant>
        get() = plants.filter { it.id in selectedIds }

    /** Hauptanlage: die erste gewählte. */
    val selected: Plant?
        get() = selectedPlants.firstOrNull()

    val selectedId: String?
        get() = selected?.id

    init {
        val saved = store.selectedIds.filter { id -> plants.any { it.id == id } }
        val first = saved.ifEmpty { listOfNotNull(plants.firstOrNull { it.id == store.selectedId }?.id ?: plants.firstOrNull()?.id) }
        selectedIds = first
        showCached()
        refresh()
    }

    private fun showCached() {
        val sel = selectedPlants
        raws = sel.mapNotNull { p -> Repo.cached(ctx, p)?.let { p.id to it } }.toMap()
        fetchedAt = sel.firstOrNull()?.let { if (raws.containsKey(it.id)) Repo.cachedTime(ctx, it) else 0L } ?: 0L
        years = sel.mapNotNull { p -> Extras.cachedYear(ctx, p)?.let { p.id to it } }.toMap()
        prices = Extras.cachedPrices(ctx)
        mix = Extras.cachedMix(ctx)
    }

    /** Jahreswerte und Börsenpreise sind Zugaben: Fehler dabei stören die Hauptansicht nicht. */
    private fun refreshExtras() {
        selectedPlants.forEach { p ->
            viewModelScope.launch {
                try {
                    val y = Extras.loadYear(ctx, p)
                    years = years + (p.id to y)
                } catch (e: Exception) {
                }
            }
        }
        viewModelScope.launch {
            try {
                prices = Extras.loadPrices(ctx)
            } catch (e: Exception) {
            }
        }
        viewModelScope.launch {
            try {
                Extras.loadMix(ctx)?.let { mix = it }
            } catch (e: Exception) {
            }
        }
    }

    fun refresh() {
        val sel = selectedPlants
        if (sel.isEmpty()) return
        refreshExtras()
        error = null
        sel.forEach { p ->
            viewModelScope.launch {
                running++
                try {
                    val r = Repo.fetch(ctx, p)
                    raws = raws + (p.id to r)
                    if (p.id == selectedId) fetchedAt = System.currentTimeMillis()
                    Repo.saveSummary(ctx, p, r)
                    SolarWidget.updateAll(ctx)
                } catch (e: Exception) {
                    error = "Die Prognose konnte nicht geladen werden (" + (e.message ?: "Netzwerkfehler") + ")."
                } finally {
                    running--
                }
            }
        }
    }

    /** Zeigt genau eine Anlage. */
    fun select(id: String) = setSelection(listOf(id))

    /** Zeigt mehrere Anlagen als Gesamtleistung. */
    fun setSelection(ids: List<String>) {
        val valid = plants.filter { it.id in ids }.map { it.id }
        if (valid.isEmpty()) return
        selectedIds = valid
        store.selectedIds = valid
        store.selectedId = valid.first()
        showCached()
        refresh()
    }

    fun savePlant(p: Plant) {
        val known = plants.any { it.id == p.id }
        plants = if (known) plants.map { if (it.id == p.id) p else it } else {
            if (plants.size >= MAX_PLANTS) return
            plants + p
        }
        store.savePlants(plants)
        if (known && p.id in selectedIds) setSelection(selectedIds) else select(p.id)
    }

    fun deletePlant(id: String) {
        plants = plants.filter { it.id != id }
        store.savePlants(plants)
        Repo.forget(ctx, id)
        if (id in selectedIds) {
            val rest = selectedIds.filter { it != id }
            raws = raws - id
            years = years - id
            if (rest.isNotEmpty()) setSelection(rest)
            else {
                val next = plants.firstOrNull()?.id
                selectedIds = emptyList()
                store.selectedId = next
                store.selectedIds = listOfNotNull(next)
                if (next != null) select(next)
            }
        }
    }

    fun setNotify(on: Boolean) {
        notifyOn = on
        store.notifyOn = on
        if (on) Scheduler.scheduleDaily(ctx, notifyHour) else Scheduler.cancelDaily(ctx)
    }

    fun changeNotifyHour(hour: Int) {
        notifyHour = hour
        store.notifyHour = hour
        if (notifyOn) Scheduler.scheduleDaily(ctx, hour)
    }

    suspend fun search(query: String): List<GeoPlace> = withContext(Dispatchers.IO) { Api.geocode(query) }

    companion object {
        const val MAX_PLANTS = 10
    }
}
