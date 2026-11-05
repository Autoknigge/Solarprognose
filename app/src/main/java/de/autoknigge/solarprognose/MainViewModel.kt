package de.autoknigge.solarprognose

import android.app.Application
import androidx.compose.runtime.getValue
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

    var selectedId by mutableStateOf<String?>(null)
        private set

    var raw by mutableStateOf<Raw?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var fetchedAt by mutableLongStateOf(0L)
        private set

    /** Langzeit-Monatserträge je kWp (PVGIS), falls geladen. */
    var year by mutableStateOf<List<Double>?>(null)
        private set

    /** Börsenpreise ab heute, falls geladen. */
    var prices by mutableStateOf<List<PriceHour>?>(null)
        private set

    var notifyOn by mutableStateOf(store.notifyOn)
        private set

    var notifyHour by mutableStateOf(store.notifyHour)
        private set

    val selected: Plant?
        get() = plants.firstOrNull { it.id == selectedId }

    init {
        val saved = store.selectedId
        selectedId = plants.firstOrNull { it.id == saved }?.id ?: plants.firstOrNull()?.id
        selected?.let { showCached(it) }
        refresh()
    }

    private fun showCached(p: Plant) {
        raw = Repo.cached(ctx, p)
        fetchedAt = if (raw != null) Repo.cachedTime(ctx, p) else 0L
        year = Extras.cachedYear(ctx, p)
        prices = Extras.cachedPrices(ctx)
    }

    /** Jahreswerte und Börsenpreise sind Zugaben: Fehler dabei stören die Hauptansicht nicht. */
    private fun refreshExtras(p: Plant) {
        viewModelScope.launch {
            try {
                val y = Extras.loadYear(ctx, p)
                if (selectedId == p.id) year = y
            } catch (e: Exception) {
            }
        }
        viewModelScope.launch {
            try {
                prices = Extras.loadPrices(ctx)
            } catch (e: Exception) {
            }
        }
    }

    fun refresh() {
        val p = selected ?: return
        refreshExtras(p)
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val r = Repo.fetch(ctx, p)
                if (selectedId == p.id) {
                    raw = r
                    fetchedAt = System.currentTimeMillis()
                }
                Repo.saveSummary(ctx, p, r)
                SolarWidget.updateAll(ctx)
            } catch (e: Exception) {
                error = "Die Prognose konnte nicht geladen werden (" + (e.message ?: "Netzwerkfehler") + ")."
            } finally {
                loading = false
            }
        }
    }

    fun select(id: String) {
        selectedId = id
        store.selectedId = id
        selected?.let { showCached(it) }
        refresh()
    }

    fun savePlant(p: Plant) {
        plants = if (plants.any { it.id == p.id }) plants.map { if (it.id == p.id) p else it } else plants + p
        store.savePlants(plants)
        select(p.id)
    }

    fun deletePlant(id: String) {
        plants = plants.filter { it.id != id }
        store.savePlants(plants)
        Repo.forget(ctx, id)
        if (selectedId == id) {
            val next = plants.firstOrNull()?.id
            selectedId = next
            store.selectedId = next
            raw = null
            year = null
            if (next != null) select(next)
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
}
