package de.autoknigge.solarprognose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

const val PRIVACY_URL = "https://autoknigge.de/solarprognose/"
const val HOME_URL = "https://autoknigge.de"

enum class Screen { Home, Edit, Settings }

@Composable
fun SolarRoot(activity: ComponentActivity) {
    val vm: MainViewModel = viewModel()
    val store = remember { Store(activity) }
    var screen by remember { mutableStateOf(if (vm.plants.isEmpty()) Screen.Edit else Screen.Home) }
    var editId by remember { mutableStateOf<String?>(null) }
    val firstRun = remember { !store.adInfoSeen }
    var adInfoOk by remember { mutableStateOf(store.adInfoSeen) }
    var adsReady by remember { mutableStateOf(false) }

    LaunchedEffect(adInfoOk) {
        if (adInfoOk) Ads.start(activity, showAppOpen = !firstRun) { adsReady = true }
    }

    BackHandler(enabled = screen != Screen.Home && vm.plants.isNotEmpty()) { screen = Screen.Home }

    if (!adInfoOk) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Kostenlos dank Werbung") },
            text = {
                Text(
                    "Solarprognose ist kostenlos und wird durch Werbung finanziert. " +
                        "Im nächsten Schritt fragt dich Google, welche Art von Werbung du zulassen möchtest. " +
                        "Deine Anlagendaten bleiben auf dem Gerät."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.adInfoSeen = true
                    adInfoOk = true
                }) { Text("Weiter") }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Forest).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    vm = vm,
                    onAdd = { editId = null; screen = Screen.Edit },
                    onEdit = { editId = vm.selectedId; screen = Screen.Edit },
                    onSettings = { screen = Screen.Settings }
                )
                Screen.Edit -> androidx.compose.runtime.key(editId) {
                    PlantEditScreen(
                        vm = vm,
                        plantId = editId,
                        canCancel = vm.plants.isNotEmpty(),
                        onDone = { screen = Screen.Home },
                        onDeleted = {
                            if (vm.plants.isEmpty()) {
                                editId = null
                                screen = Screen.Edit
                            } else {
                                screen = Screen.Home
                            }
                        }
                    )
                }
                Screen.Settings -> SettingsScreen(vm = vm, activity = activity, onBack = { screen = Screen.Home })
            }
        }
        if (adsReady) BannerAd()
    }
}

// ---------- Bausteine ----------

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun Tile(label: String, value: String, unit: String?, note: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Panel2, Panel)))
            .border(1.dp, Gold.copy(alpha = 0.55f), shape)
            .padding(12.dp)
    ) {
        Text(label.uppercase(Locale.GERMAN), fontSize = 11.sp, letterSpacing = 0.8.sp, color = Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
            if (unit != null) Text(" $unit", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(note, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
    }
}

private fun pad2(n: Int) = String.format(Locale.US, "%02d", n)

private fun hourInfo(f: Forecast, i: Int): String {
    val x = f.hours[i]
    return Calc.weekday(x.date) + " " + Calc.dm(x.date) + ", " + pad2((x.hour + 23) % 24) + "–" + pad2(x.hour) + " Uhr\n" +
        Calc.fmt0(x.power) + " W · " + Calc.fmt0(x.gti) + " W/m² · Wolken " + Calc.fmt0(x.cloud) + " % · " + Calc.fmt0(x.temp) + " °C"
}

private fun dayInfo(d: Day): String =
    Calc.weekday(d.date) + " " + Calc.dm(d.date) + ": " + Calc.fmt1(d.kwh) + " kWh\n" +
        "Spitze " + Calc.fmt0(d.peak) + " W gegen " + d.peakHour + " Uhr · Wolken " + (d.cloudAvg?.let { Calc.fmt0(it) + " %" } ?: "–")

// ---------- Startseite ----------

@Composable
fun HomeScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit) {
    val plant = vm.selected ?: return
    val forecast = remember(vm.raw, plant) { vm.raw?.let { Calc.compute(it, plant) } }
    var range by rememberSaveable { mutableIntStateOf(48) }
    var selHour by remember(plant.id) { mutableStateOf<Int?>(null) }
    var selDay by remember(plant.id) { mutableStateOf<Int?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val nowC = remember(forecast, tick) { forecast?.let { Calc.nowCoord(it) } }
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Solarprognose", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren", tint = Ink) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen", tint = Ink) }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.plants, key = { it.id }) { p ->
                        FilterChip(selected = p.id == vm.selectedId, onClick = { vm.select(p.id) }, label = { Text(p.name) })
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Anlage bearbeiten", tint = Ink) }
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Anlage hinzufügen", tint = Ink) }
            }

            val stand = if (vm.fetchedAt > 0) " · Stand " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(vm.fetchedAt)) + " Uhr" else ""
            Text(
                plant.place + " · " + Calc.fmt1(plant.kwp) + " kWp · " + plant.tilt + "° · " + Calc.azName(plant.az) + stand,
                fontSize = 13.sp, color = Muted
            )

            vm.error?.let { msg ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel2).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    TextButton(onClick = { vm.refresh() }) { Text("Erneut") }
                }
            }

            if (forecast == null || forecast.days.isEmpty()) {
                if (!vm.loading && vm.error == null) Text("Noch keine Daten.", color = Muted)
            } else {
                val d0 = forecast.days[0]
                val d1 = forecast.days.getOrNull(1)
                val total = forecast.days.sumOf { it.kwh }
                val best = forecast.days.maxByOrNull { it.kwh } ?: d0
                val rest = nowC?.let { Calc.remainingToday(forecast, it) }

                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Heute", Calc.fmt1(d0.kwh), "kWh", if (rest != null) "noch " + Calc.fmt1(rest) + " kWh bis Sonnenuntergang" else "ganzer Tag",
                        Modifier.weight(1f).fillMaxHeight())
                    Tile("Morgen", d1?.let { Calc.fmt1(it.kwh) } ?: "–", "kWh", d1?.let { "Spitze " + Calc.fmt0(it.peak) + " W gegen " + it.peakHour + " Uhr" } ?: "",
                        Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("7 Tage", Calc.fmt0(total), "kWh", "Ø " + Calc.fmt1(total / forecast.days.size) + " kWh pro Tag", Modifier.weight(1f).fillMaxHeight())
                    Tile("Bester Tag", Calc.weekday(best.date) + " " + Calc.dm(best.date), null, Calc.fmt1(best.kwh) + " kWh erwartet", Modifier.weight(1f).fillMaxHeight())
                }

                SectionCard(
                    title = "Leistung pro Stunde (Watt)",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = range == 48, onClick = { range = 48; selHour = null }, label = { Text("2 Tage") })
                            FilterChip(selected = range != 48, onClick = { range = 168; selHour = null }, label = { Text("7 Tage") })
                        }
                    }
                ) {
                    val sh = selHour
                    Text(
                        if (sh != null && sh < forecast.hours.size) hourInfo(forecast, sh) else "Tippe oder wische ins Diagramm für Details.\n ",
                        fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp
                    )
                    HourlyChart(
                        f = forecast,
                        count = if (range == 48) 48 else forecast.hours.size,
                        nowC = nowC,
                        selected = sh,
                        onSelect = { selHour = it }
                    )
                }

                SectionCard(title = "Ertrag pro Tag (kWh)") {
                    val sd = selDay
                    Text(
                        if (sd != null && sd < forecast.days.size) dayInfo(forecast.days[sd]) else "Tippe auf einen Balken für Details.\n ",
                        fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp
                    )
                    DailyChart(f = forecast, selected = sd, onSelect = { selDay = it })
                }

                SectionCard(title = "Tabelle") {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Tag" to 1.3f, "Ertrag" to 1f, "Spitze" to 1f, "Wolken" to 0.9f).forEach { (t, w) ->
                            Text(t, fontSize = 11.sp, color = Muted, modifier = Modifier.weight(w))
                        }
                    }
                    forecast.days.forEachIndexed { k, d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text((if (k == 0) "Heute" else Calc.weekday(d.date)) + " " + Calc.dm(d.date), fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1.3f))
                            Text(Calc.fmt1(d.kwh) + " kWh", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                            Text(Calc.fmt0(d.peak) + " W", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                            Text(d.cloudAvg?.let { Calc.fmt0(it) + " %" } ?: "–", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(0.9f))
                        }
                    }
                }
            }

            Text(
                "Wetterdaten von Open-Meteo.com (CC BY 4.0). Die Werte sind eine Schätzung ohne Verschattung und Schnee.",
                fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
            )
            Text(
                "Eine Idee von autoknigge.de",
                fontSize = 12.sp, color = Muted, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uri.openUri(HOME_URL) }.padding(vertical = 4.dp)
            )
        }
    }
}

// ---------- Anlage anlegen und bearbeiten ----------

@Composable
fun PlantEditScreen(vm: MainViewModel, plantId: String?, canCancel: Boolean, onDone: () -> Unit, onDeleted: () -> Unit) {
    val existing = remember(plantId) { vm.plants.firstOrNull { it.id == plantId } }
    var name by remember { mutableStateOf(existing?.name ?: if (vm.plants.isEmpty()) "Meine Anlage" else "Anlage " + (vm.plants.size + 1)) }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var lat by remember { mutableStateOf(existing?.lat) }
    var lon by remember { mutableStateOf(existing?.lon) }
    var kwp by remember { mutableStateOf(existing?.kwp?.let { Calc.fmt1(it) } ?: "4,0") }
    var tilt by remember { mutableFloatStateOf((existing?.tilt ?: 35).toFloat()) }
    var az by remember { mutableFloatStateOf((existing?.az ?: 0).toFloat()) }
    var eff by remember { mutableStateOf((existing?.eff ?: 85).toString()) }
    var limit by remember { mutableStateOf(existing?.limitW?.takeIf { it > 0 }?.toString() ?: "") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            searchMsg = null
            try {
                results = vm.search(q)
                if (results.isEmpty()) searchMsg = "Kein Treffer. Versuche es mit einem anderen Ortsnamen."
            } catch (e: Exception) {
                searchMsg = "Die Ortssuche ist gerade nicht erreichbar."
            } finally {
                searching = false
            }
        }
    }

    val kwpVal = kwp.replace(',', '.').toDoubleOrNull()
    val valid = lat != null && lon != null && kwpVal != null && kwpVal > 0.0
    val welcome = existing == null && vm.plants.isEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canCancel) IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Ink) }
            Text(
                if (welcome) "Willkommen" else if (existing == null) "Neue Anlage" else "Anlage bearbeiten",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink
            )
        }
        if (welcome) {
            Text(
                "Für die Prognose brauche ich den Standort deiner Anlage sowie Leistung, Neigung und Ausrichtung der Module. " +
                    "Alles bleibt auf deinem Gerät.",
                color = Muted, fontSize = 14.sp
            )
        }

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name der Anlage") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionCard(title = "Standort") {
            Text(
                if (lat != null) "Gewählt: $place" else "Noch kein Ort gewählt",
                color = if (lat != null) Gold else Muted, fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Ort suchen") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { doSearch() }, enabled = !searching && query.isNotBlank()) {
                    Icon(Icons.Filled.Search, contentDescription = "Suchen")
                }
            }
            searchMsg?.let { Text(it, color = Muted, fontSize = 13.sp) }
            results.forEach { r ->
                Text(
                    r.label + "  (" + String.format(Locale.US, "%.2f, %.2f", r.lat, r.lon) + ")",
                    color = Ink, fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Panel2)
                        .clickable {
                            place = r.name
                            lat = r.lat
                            lon = r.lon
                            results = emptyList()
                            query = ""
                        }
                        .padding(12.dp)
                )
            }
        }

        SectionCard(title = "Module") {
            OutlinedTextField(
                value = kwp, onValueChange = { kwp = it }, label = { Text("Modulleistung (kWp)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Neigung: " + tilt.roundToInt() + "°", color = Ink, fontSize = 14.sp)
            Slider(value = tilt, onValueChange = { tilt = it }, valueRange = 0f..90f, steps = 89)
            Text("Ausrichtung: " + Calc.azName(az.roundToInt()) + " (" + az.roundToInt() + "°)", color = Ink, fontSize = 14.sp)
            Slider(value = az, onValueChange = { az = it }, valueRange = -180f..180f, steps = 71)
            Text("0° = Süd, −90° = Ost, 90° = West", color = Muted, fontSize = 12.sp)
        }

        SectionCard(title = "Verluste und Grenzen") {
            OutlinedTextField(
                value = eff, onValueChange = { eff = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Systemwirkungsgrad (%)") }, singleLine = true,
                supportingText = { Text("Wechselrichter, Kabel, Verschmutzung. Üblich sind 80 bis 90.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = limit, onValueChange = { limit = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("PV-Eingang max. (W), optional") }, singleLine = true,
                supportingText = { Text("Kappt die Prognose bei der Eingangsgrenze von Wechselrichter oder Laderegler. Leer = keine Grenze.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = valid,
                onClick = {
                    val la = lat
                    val lo = lon
                    val kw = kwpVal
                    if (la != null && lo != null && kw != null) {
                        vm.savePlant(
                            Plant(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifBlank { "Meine Anlage" },
                                place = place,
                                lat = la,
                                lon = lo,
                                kwp = kw,
                                tilt = tilt.roundToInt(),
                                az = az.roundToInt(),
                                eff = (eff.toIntOrNull() ?: 85).coerceIn(40, 100),
                                limitW = limit.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            )
                        )
                        onDone()
                    }
                }
            ) { Text("Speichern") }
            if (canCancel) TextButton(onClick = onDone) { Text("Abbrechen") }
        }
        if (!valid) Text("Wähle einen Ort und gib eine Modulleistung größer 0 ein.", color = Muted, fontSize = 12.sp)

        if (existing != null) {
            if (!confirmDelete) {
                TextButton(onClick = { confirmDelete = true }) { Text("Anlage löschen", color = MaterialTheme.colorScheme.error) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wirklich löschen?", color = Ink, fontSize = 14.sp)
                    TextButton(onClick = {
                        vm.deletePlant(existing.id)
                        onDeleted()
                    }) { Text("Ja, löschen", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmDelete = false }) { Text("Nein") }
                }
            }
        }
    }
}

// ---------- Einstellungen ----------

@Composable
fun SettingsScreen(vm: MainViewModel, activity: ComponentActivity, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setNotify(true)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Ink) }
            Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        SectionCard(title = "Tägliche Nachricht") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tagesprognose als Benachrichtigung", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.notifyOn,
                    onCheckedChange = { on ->
                        val needsPermission = on && Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotify(on)
                    }
                )
            }
            if (vm.notifyOn) {
                Text("Uhrzeit (ungefähr)", color = Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 7, 8, 9).forEach { h ->
                        FilterChip(selected = vm.notifyHour == h, onClick = { vm.changeNotifyHour(h) }, label = { Text("$h Uhr") })
                    }
                }
            }
        }

        SectionCard(title = "Widget") {
            Text(
                "Halte auf dem Startbildschirm eine freie Stelle gedrückt, wähle Widgets und dann Solarprognose. " +
                    "Das Widget zeigt die gerade gewählte Anlage.",
                color = Muted, fontSize = 13.sp
            )
        }

        SectionCard(title = "Datenschutz und Werbung") {
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Datenschutzerklärung öffnen") }
            if (Ads.privacyOptionsRequired(ctx)) {
                TextButton(onClick = { Ads.showPrivacyOptions(activity) }) { Text("Werbe-Einwilligung ändern") }
            }
        }

        SectionCard(title = "Info") {
            Text("Version " + BuildConfig.VERSION_NAME, color = Ink, fontSize = 14.sp)
            Text(
                "Wetter- und Strahlungsdaten: Open-Meteo.com, Lizenz CC BY 4.0. " +
                    "Die Prognose ist eine Modellrechnung und kein Messwert.",
                color = Muted, fontSize = 13.sp
            )
            TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text("open-meteo.com") }
            Text(
                "Eine Idee von autoknigge.de",
                color = Gold, fontSize = 14.sp, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { uri.openUri(HOME_URL) }.padding(vertical = 4.dp)
            )
        }
    }
}
