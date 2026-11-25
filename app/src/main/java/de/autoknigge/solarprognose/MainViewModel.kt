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
    var ytd by mutableStateOf<Map<String, YtdData>>(emptyMap())
    var sat by mutableStateOf<Map<String, Map<String, Double>>>(emptyMap())
    var models by mutableStateOf<Map<String, Map<String, Map<String, Double>>>>(emptyMap())
    var ens by mutableStateOf<Map<String, Extras.EnsRaw>>(emptyMap())
    var obsCloud by mutableStateOf<Map<String, Extras.ObsCloud>>(emptyMap())
        private set
    var profile by mutableStateOf<Map<String, ProfileData>>(emptyMap())
        private set
    var profileFailed by mutableStateOf(false)
        private set
    var gas by mutableStateOf<Extras.GasStorage?>(null)
    var mixRange by mutableStateOf(0)
    var mixSeries by mutableStateOf<Map<Int, MixSeries>>(emptyMap())
    var mixFailed by mutableStateOf<Set<Int>>(emptySet())
        private set

    var groups by mutableStateOf(store.groups)
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
        watchNetwork()
    }

    private fun showCached() {
        val sel = selectedPlants
        raws = sel.mapNotNull { p -> Repo.cached(ctx, p)?.let { p.id to it } }.toMap()
        fetchedAt = sel.firstOrNull()?.let { if (raws.containsKey(it.id)) Repo.cachedTime(ctx, it) else 0L } ?: 0L
        years = sel.mapNotNull { p -> Extras.cachedYear(ctx, p)?.let { p.id to it } }.toMap()
        prices = Extras.cachedPrices(ctx)
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
                try {
                    ytd = ytd + (p.id to Extras.loadYtd(p))
                } catch (e: Exception) {
                }
                try {
                    models = models + (p.id to Extras.loadModels(p))
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
        selected?.let { p ->
            viewModelScope.launch {
                try {
                    profile = profile + (p.id to Extras.loadProfile(p))
                    profileFailed = false
                } catch (e: Exception) {
                    profileFailed = true
                }
            }
        }
        viewModelScope.launch {
            try {
                gas = Extras.loadGas() ?: gas
            } catch (e: Exception) {
            }
        }
        loadMixRange(0)
        if (mixRange != 0) loadMixRange(mixRange)
    }

    fun selectMix(r: Int) {
        mixRange = r
        loadMixRange(r)
    }

    private fun loadMixRange(r: Int) {
        mixFailed = mixFailed - r
        viewModelScope.launch {
            try {
                val s = Extras.loadMixSeries(r)
                if (s != null) mixSeries = mixSeries + (r to s) else mixFailed = mixFailed + r
                if (s != null && r == 0) {
                    val e = Extras.extendMixNow(s)
                    if (e !== s) mixSeries = mixSeries + (0 to e)
                }
            } catch (e: Exception) {
                mixFailed = mixFailed + r
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
                    // Kurzzeitige Netzfehler (z. B. DNS direkt nach dem Aufwachen) automatisch wiederholen
                    val waits = longArrayOf(2_000, 6_000, 15_000)
                    var attempt = 0
                    while (true) {
                        try {
                            val r = Repo.fetch(ctx, p)
                            raws = raws + (p.id to r)
                            if (p.id == selectedId) fetchedAt = System.currentTimeMillis()
                            Repo.saveSummary(ctx, p, r)
                            SolarWidget.updateAll(ctx)
                            error = null
                            break
                        } catch (e: Exception) {
                            if (attempt >= waits.size) {
                                error = "Die Prognose konnte nicht geladen werden (" + (e.message ?: "Netzwerkfehler") +
                                    "). Die App versucht es automatisch erneut, sobald wieder Netz da ist."
                                break
                            }
                            kotlinx.coroutines.delay(waits[attempt])
                            attempt++
                        }
                    }
                } finally {
                    running--
                }
            }
        }
    }

    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /** Sobald wieder Netz da ist und die letzte Abfrage gescheitert war (oder die Daten alt sind), neu laden. */
    private fun watchNetwork() {
        try {
            val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    viewModelScope.launch {
                        val stale = System.currentTimeMillis() - fetchedAt > 20 * 60_000L
                        if (error != null || stale) {
                            kotlinx.coroutines.delay(1_500)
                            if (running == 0) refresh()
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
        }
    }

    override fun onCleared() {
        try {
            netCallback?.let { (ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) }
        } catch (e: Exception) {
        }
        super.onCleared()
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

    /** Speichert eine benannte Gruppe (gleicher Name überschreibt). */
    fun saveGroup(name: String, ids: List<String>) {
        val n = name.trim()
        val valid = plants.filter { it.id in ids }.map { it.id }
        if (n.isEmpty() || valid.size < 2) return
        val ex = groups.firstOrNull { it.name.equals(n, ignoreCase = true) }
        groups = if (ex != null) groups.map { if (it.id == ex.id) it.copy(ids = valid) else it }
        else groups + PlantGroup(java.util.UUID.randomUUID().toString(), n, valid)
        store.groups = groups
    }

    fun deleteGroup(id: String) {
        groups = groups.filter { it.id != id }
        store.groups = groups
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
        groups = groups.map { it.copy(ids = it.ids.filter { i -> i != id }) }.filter { it.ids.size >= 2 }
        store.groups = groups
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
