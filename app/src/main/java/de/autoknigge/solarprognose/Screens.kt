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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
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

const val PRIVACY_URL = "https://autoknigge.de/solarprognose/datenschutz.html"
const val HOME_URL = "https://autoknigge.de"
const val IMPRINT_URL = "https://autoknigge.de/impressum.html"

enum class Screen { Home, Edit, Settings }

@Composable
fun SolarRoot(activity: ComponentActivity) {
    val vm: MainViewModel = viewModel()
    var screen by remember { mutableStateOf(if (vm.plants.isEmpty()) Screen.Edit else Screen.Home) }
    var editId by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = screen != Screen.Home && vm.plants.isNotEmpty()) { screen = Screen.Home }

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
    }
}

// ---------- Bausteine ----------

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    bg: androidx.compose.ui.graphics.Color = Panel,
    borderColor: androidx.compose.ui.graphics.Color = Line,
    inkColor: androidx.compose.ui.graphics.Color = Ink,
    mutedColor: androidx.compose.ui.graphics.Color = Muted,
    darken: Float = 0.45f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBrush(bg, darken))
            .border(1.dp, borderColor, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = inkColor, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalInk provides inkColor, LocalMuted provides mutedColor) {
            content()
        }
    }
}

@Composable
fun Tile(
    label: String,
    value: String,
    unit: String?,
    note: String,
    modifier: Modifier = Modifier,
    icon: WeatherKind? = null,
    bg: androidx.compose.ui.graphics.Color = Panel2,
    mutedColor: androidx.compose.ui.graphics.Color = Muted
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(cardBrush(bg))
            .border(1.dp, Gold.copy(alpha = 0.55f), shape)
            .padding(12.dp)
    ) {
        Column {
            Text(label.uppercase(Locale.GERMAN), fontSize = 11.sp, letterSpacing = 0.8.sp, color = mutedColor)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                if (unit != null) Text(" $unit", fontSize = 13.sp, color = mutedColor, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(note, fontSize = 12.sp, color = mutedColor, lineHeight = 16.sp)
        }
        if (icon != null) WeatherIcon(icon, Modifier.align(Alignment.TopEnd))
    }
}

private fun pad2(n: Int) = String.format(Locale.US, "%02d", n)

private fun hourInfo(f: Forecast, i: Int): String {
    val x = f.hours[i]
    return Calc.weekday(x.date) + " " + Calc.dm(x.date) + ", " + pad2((x.hour + 23) % 24) + "–" + pad2(x.hour) + " Uhr\n" +
        Calc.fmt0(x.power) + " W · " + Calc.fmt0(x.gti) + " W/m² · Wolken " + Calc.fmt0(x.cloud) + " % · " + Calc.fmt0(x.temp) + " °C"
}

private fun wxInfo(f: Forecast, i: Int): String {
    val x = f.hours[i]
    return Calc.weekday(x.date) + " " + Calc.dm(x.date) + ", " + pad2((x.hour + 23) % 24) + "–" + pad2(x.hour) + " Uhr\n" +
        "Temperatur " + Calc.fmt0(x.temp) + " °C · Bewölkung " + Calc.fmt0(x.cloud) + " %" +
        (if (x.precip >= 0.1) " · Regen " + Calc.fmt1(x.precip) + " mm" else "")
}

private fun dayInfo(d: Day): String =
    Calc.weekday(d.date) + " " + Calc.dm(d.date) + ": " + Calc.fmt1(d.kwh) + " kWh\n" +
        "Spitze " + Calc.fmt0(d.peak) + " W gegen " + d.peakHour + " Uhr · Wolken " + (d.cloudAvg?.let { Calc.fmt0(it) + " %" } ?: "–") +
        (d.sunH?.let { " · Sonne " + Calc.fmt1(it) + " h" } ?: "")

/** Zeitraum der Stundendiagramme: -2 = zwei Tage zurück bis heute, sonst Tage ab heute. */
@Composable
private fun RangeChips(range: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(-2 to "-2 Tage", 1 to "1 Tag", 3 to "3 Tage", 7 to "7 Tage").forEach { (v, l) ->
            FilterChip(selected = range == v, onClick = { onSelect(v) }, label = { Text(l) })
        }
    }
}

// ---------- Startseite ----------

@Composable
fun HomeScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit) {
    val plant = vm.selected ?: return
    val sel = vm.selectedPlants
    val group = sel.size > 1
    var showGroupDialog by remember { mutableStateOf(false) }
    val forecast = remember(vm.raws, sel) { Calc.forecastFor(vm.raws, sel, false) }
    // Mit 2 Tagen Rückblick für Ertrag pro Tag, Leistung, Bewölkung/Temperatur und Tabelle
    val full = remember(vm.raws, sel) { Calc.forecastFor(vm.raws, sel, true) }
    var range by rememberSaveable { mutableIntStateOf(3) }
    var selHour by remember(plant.id) { mutableStateOf<Int?>(null) }
    var selDay by remember(plant.id) { mutableStateOf<Int?>(null) }
    var selWx by remember(plant.id) { mutableStateOf<Int?>(null) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val nowC = remember(forecast, tick) { forecast?.let { Calc.nowCoord(it) } }
    val nowFull = remember(full, tick) { full?.let { Calc.nowCoord(it) } }
    val nowSec = remember(tick) { System.currentTimeMillis() / 1000 }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60_000L)
            vm.refresh()
        }
    }
    val uri = LocalUriHandler.current
    if (showGroupDialog) GroupDialog(vm) { showGroupDialog = false }

    Column(Modifier.fillMaxSize()) {
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Logo 3 mm höher als zuvor (40 dp + rund 19 dp), daneben klein die Versionsnummer
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Image(
                    painter = painterResource(R.drawable.logo_header),
                    contentDescription = "Solarprognose – autoknigge.de öffnen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(59.dp).aspectRatio(1139f / 537f).clip(RoundedCornerShape(10.dp)).clickable { uri.openUri(HOME_URL) }
                )
                Text(
                    BuildConfig.VERSION_NAME,
                    fontSize = 10.sp,
                    color = Muted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                )
            }
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren", tint = Ink) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen", tint = Ink) }
        }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.plants, key = { it.id }) { p ->
                        PlantChip(p.name, p.id in vm.selectedIds) { vm.select(p.id) }
                    }
                    if (vm.plants.size >= 2) {
                        item(key = "total") { PlantChip("Σ Gesamt", group) { showGroupDialog = true } }
                    }
                }
                if (!group) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Anlage bearbeiten", tint = Ink) }
                if (vm.plants.size < MainViewModel.MAX_PLANTS) {
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Anlage hinzufügen", tint = Ink) }
                }
            }

            val stand = if (vm.fetchedAt > 0) " · Stand " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(vm.fetchedAt)) + " Uhr" else ""
            Text(
                if (group) "Gesamt aus " + sel.size + " Anlagen · " + Calc.fmt1(sel.sumOf { it.kwp }) + " kWp" + stand
                else plant.place + " · " + Calc.fmt1(plant.kwp) + " kWp · " + plant.tilt + "° · " + Calc.azName(plant.az) + stand
                    + (if (plant.shades.isNotEmpty()) " · " + plant.shades.size + " Verschattung" + (if (plant.shades.size > 1) "en" else "") else ""),
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
                        Modifier.weight(1f).fillMaxHeight(), icon = Extras.kindOf(d0), bg = TileToday, mutedColor = Color(0xFFFFE6DA))
                    Tile("Morgen", d1?.let { Calc.fmt1(it.kwh) } ?: "–", "kWh", d1?.let { "Spitze " + Calc.fmt0(it.peak) + " W gegen " + it.peakHour + " Uhr" } ?: "",
                        Modifier.weight(1f).fillMaxHeight(), icon = d1?.let { Extras.kindOf(it) }, bg = TileTomorrow, mutedColor = Color(0xFFFFEBD6))
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("7 Tage", Calc.fmt0(total), "kWh", "Ø " + Calc.fmt1(total / forecast.days.size) + " kWh pro Tag", Modifier.weight(1f).fillMaxHeight())
                    Tile("Bester Tag", Calc.weekday(best.date) + " " + Calc.dm(best.date), null, Calc.fmt1(best.kwh) + " kWh erwartet", Modifier.weight(1f).fillMaxHeight())
                }

                val uvNow = nowC?.let { c ->
                    val cur = forecast.hours.getOrNull(Math.floor(c).toInt() + 1)?.uv ?: 0.0
                    if (cur > 0.0) cur else d0.uvMax
                }?.takeIf { it > 0.0 }
                val nowKw = nowC?.let { forecast.hours.getOrNull(Math.floor(it).toInt() + 1)?.power?.div(1000.0) }
                if (rest != null) TodayCard(total = d0.kwh, rest = rest, nowKw = nowKw, uv = uvNow, sun = Calc.sunTimes(plant.lat, plant.lon, forecast.timezone), sunTotal = d0.sunH, sunRest = nowC?.let { c -> if (d0.sunH == null) null else (Math.floor(c).toInt() + 1 until d0.end).sumOf { forecast.hours.getOrNull(it)?.sunH?.coerceAtLeast(0.0) ?: 0.0 } })

                val dayF = full ?: forecast
                val hourF = if (range == -2) dayF else forecast
                val hourNow = if (range == -2 && full != null) nowFull else nowC
                val nowIdx = hourNow?.let { (Math.floor(it).toInt() + 1).coerceIn(0, (hourF.hours.size - 1).coerceAtLeast(0)) }
                val hourCount = when (range) {
                    -2 -> hourF.days.getOrNull(hourF.todayIndex)?.end ?: 24
                    1 -> 24
                    7 -> forecast.hours.size
                    else -> 72
                }

                SectionCard(title = "Ertrag pro Tag (kWh)") {
                    val sd = selDay
                    Text(
                        if (sd != null && sd < dayF.days.size) dayInfo(dayF.days[sd]) else "Tippe auf einen Balken für Details.\n ",
                        fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp
                    )
                    DailyChart(f = dayF, selected = sd, onSelect = { selDay = it })
                }

                SectionCard(
                    title = "Leistung pro Stunde (Watt)",
                    bg = TileToday,
                    borderColor = Color(0xFFF08A70),
                                    mutedColor = WarmMuted,
                ) {
                    RangeChips(range) { range = it; selHour = null; selWx = null }
                    val sh = selHour
                    Text(
                        if (sh != null && sh < hourF.hours.size) hourInfo(hourF, sh)
                        else if (nowIdx != null && hourF.hours.isNotEmpty()) "Jetzt · " + hourInfo(hourF, nowIdx)
                        else "\n ",
                        fontSize = 12.5.sp, color = WarmMuted, lineHeight = 17.sp
                    )
                    HourlyChart(
                        f = hourF,
                        count = hourCount,
                        nowC = hourNow,
                        selected = sh,
                        onSelect = { selHour = it },
                        ink = Ink,
                        muted = WarmMuted,
                        grid = Color(0x33FFFFFF),
                        axis = Color(0x66FFFFFF),
                        lineColor = Color.Black
                    )
                    Text("Tippe oder wische ins Diagramm für Details.", fontSize = 11.5.sp, color = WarmMuted)
                }

                SectionCard(
                    title = "Bewölkung und Temperatur",
                    bg = TileTomorrow,
                    borderColor = Color(0xFFE8A060),
                                    mutedColor = WarmMuted,
                ) {
                    RangeChips(range) { range = it; selHour = null; selWx = null }
                    val sw = selWx
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("━ Temperatur (°C, links)", fontSize = 11.5.sp, color = Color.Black)
                        Text("▇ Bewölkung (%, rechts)", fontSize = 11.5.sp, color = CloudColorLight)
                    }
                    Text(
                        if (sw != null && sw < hourF.hours.size) wxInfo(hourF, sw)
                        else if (nowIdx != null && hourF.hours.isNotEmpty()) "Jetzt · " + wxInfo(hourF, nowIdx)
                        else "\n ",
                        fontSize = 12.5.sp, color = WarmMuted, lineHeight = 17.sp
                    )
                    WeatherChart(
                        f = hourF,
                        count = hourCount,
                        nowC = hourNow,
                        selected = sw,
                        onSelect = { selWx = it },
                        ink = Ink,
                        muted = WarmMuted,
                        grid = Color(0x33FFFFFF),
                        axis = Color(0x66FFFFFF),
                        tempColor = Color.Black,
                        cloudColor = CloudColorLight
                    )
                    Text("Tippe oder wische ins Diagramm für Details.", fontSize = 11.5.sp, color = WarmMuted)
                }

                val ys = sel.mapNotNull { p -> vm.years[p.id]?.let { p to it } }
                if (ys.isNotEmpty()) {
                    val kwpSum = ys.sumOf { it.first.kwp }
                    val weighted = (0 until 12).map { m -> ys.sumOf { it.second.perKwp[m] * it.first.kwp } / kwpSum }
                    YearCard(kwpSum, YearData(weighted, ys.first().second.label))
                }

                SectionCard(title = "Tabelle", bg = TileTomorrow, borderColor = Color(0xFFE8A060), mutedColor = WarmMuted) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Tag" to 1.2f, "Ertrag" to 1f, "Spitze" to 0.95f, "Wolken" to 0.85f, "Sonne" to 0.75f).forEach { (t, w) ->
                            Text(t, fontSize = 11.sp, color = WarmMuted, modifier = Modifier.weight(w))
                        }
                    }
                    dayF.days.forEachIndexed { k, d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(dayName(k - dayF.todayIndex, d.date).let { if (k - dayF.todayIndex in -1..1 || k - dayF.todayIndex == -2) it else Calc.weekday(d.date) } + " " + Calc.dm(d.date), fontSize = 13.sp, color = Ink, fontWeight = if (k == dayF.todayIndex) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1.2f))
                            Text(Calc.fmt1(d.kwh) + " kWh", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
                            Text(Calc.fmt0(d.peak) + " W", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(0.95f))
                            Text(d.cloudAvg?.let { Calc.fmt0(it) + " %" } ?: "–", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(0.85f))
                            Text(d.sunH?.let { Calc.fmt1(it) + " h" } ?: "–", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(0.75f))
                        }
                    }
                }

                vm.prices?.let { pr -> if (pr.isNotEmpty()) PriceCard(pr, nowSec) }
                MixCard(vm.mixRange, vm.mixSeries[vm.mixRange], vm.mixRange in vm.mixFailed) { vm.selectMix(it) }
            }

            Text(
                "Wetterdaten: Open-Meteo.com (CC BY 4.0). Jahreswerte: PVGIS, Europäische Kommission (JRC). Börsenpreise: Energy-Charts, Fraunhofer ISE (CC BY 4.0). Die Werte sind eine Schätzung ohne Verschattung und Schnee.",
                fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Eine Idee von autoknigge.de",
                    fontSize = 12.sp, color = Muted, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uri.openUri(HOME_URL) }.padding(vertical = 6.dp)
                )
                Text(
                    "Datenschutz",
                    fontSize = 12.sp, color = Muted, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uri.openUri(PRIVACY_URL) }.padding(vertical = 6.dp)
                )
                Text(
                    "Impressum",
                    fontSize = 12.sp, color = Muted, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uri.openUri(IMPRINT_URL) }.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PlantChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = TileToday,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun GroupDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var ids by remember { mutableStateOf(vm.selectedIds.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gesamtleistung") },
        text = {
            Column {
                Text("Welche Anlagen sollen zusammengezählt werden?", fontSize = 13.sp)
                vm.plants.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { ids = if (p.id in ids) ids - p.id else ids + p.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(checked = p.id in ids, onCheckedChange = { ids = if (it) ids + p.id else ids - p.id })
                        Text(p.name + " · " + Calc.fmt1(p.kwp) + " kWp", fontSize = 14.sp)
                    }
                }
                TextButton(onClick = { ids = vm.plants.map { it.id }.toSet() }) { Text("Alle wählen") }
            }
        },
        confirmButton = {
            TextButton(enabled = ids.isNotEmpty(), onClick = { vm.setSelection(vm.plants.map { it.id }.filter { it in ids }); onDismiss() }) { Text("Anzeigen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
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
    var eff by remember { mutableStateOf((existing?.eff ?: 80).toString()) }
    var limit by remember { mutableStateOf(existing?.limitW?.takeIf { it > 0 }?.toString() ?: "") }
    var shades by remember { mutableStateOf(existing?.shades ?: emptyList()) }
    var shadeEdit by remember { mutableStateOf<Pair<Int, Shade>?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    fun applyLoc(l: android.location.Location) {
        lat = Math.round(l.latitude * 100000) / 100000.0
        lon = Math.round(l.longitude * 100000) / 100000.0
        place = "Mein Standort"
        results = emptyList(); searchMsg = null
    }

    fun useLocation() {
        try {
            val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            val provs = lm.getProviders(true)
            var best: android.location.Location? = null
            for (p in provs) {
                val l = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
                if (l != null && (best == null || l.accuracy < best.accuracy)) best = l
            }
            if (best != null) applyLoc(best)
            else if (android.os.Build.VERSION.SDK_INT >= 30 && provs.isNotEmpty()) {
                searchMsg = "Standort wird ermittelt …"
                lm.getCurrentLocation(provs.first(), null, ctx.mainExecutor) { l ->
                    if (l != null) applyLoc(l) else searchMsg = "Standort konnte nicht ermittelt werden. Ist die Ortung am Handy eingeschaltet?"
                }
            } else searchMsg = "Standort konnte nicht ermittelt werden. Ist die Ortung am Handy eingeschaltet?"
        } catch (e: Exception) {
            searchMsg = "Standort konnte nicht ermittelt werden."
        }
    }
    val locLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) useLocation() else searchMsg = "Ohne Standortfreigabe geht es über die Suche oder die Koordinaten."
    }

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
    shadeEdit?.let { (idx, sh) ->
        ShadeDialog(
            initial = sh,
            isNew = idx < 0,
            onSave = { n -> shades = if (idx < 0) shades + n else shades.mapIndexed { k, o -> if (k == idx) n else o }; shadeEdit = null },
            onDelete = { shades = shades.filterIndexed { k, _ -> k != idx }; shadeEdit = null },
            onDismiss = { shadeEdit = null }
        )
    }

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
            if (lat != null && lon != null) {
                Text("Breite " + String.format(Locale.US, "%.5f", lat) + "° · Länge " + String.format(Locale.US, "%.5f", lon) + "°", color = Ink, fontSize = 13.sp)
            }
            OutlinedButton(onClick = {
                val fine = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarse = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (fine || coarse) useLocation() else locLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.fillMaxWidth()) { Text("📍 Standort des Handys verwenden") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, label = { Text("Ort oder Postleitzahl suchen") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { doSearch() }, enabled = !searching && query.isNotBlank()) {
                    Icon(Icons.Filled.Search, contentDescription = "Suchen")
                }
            }
            searchMsg?.let { Text(it, color = Muted, fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = coords, onValueChange = { coords = it }, label = { Text("Koordinaten, z. B. 53.1235, 8.8017") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val nums = Regex("-?\\d+(?:[.,]\\d+)?").findAll(coords).map { it.value.replace(',', '.').toDouble() }.toList()
                    if (nums.size >= 2 && nums[0] in -90.0..90.0 && nums[1] in -180.0..180.0) {
                        lat = nums[0]; lon = nums[1]
                        place = "Koordinaten"
                        coords = ""; searchMsg = null; results = emptyList()
                    } else searchMsg = "Koordinaten nicht erkannt. Beispiel: 53.1235, 8.8017 (Breite, Länge)."
                }, enabled = coords.isNotBlank()) { Text("OK") }
            }
            results.forEach { r ->
                Text(
                    r.label + "  (" + String.format(Locale.US, "%.2f, %.2f", r.lat, r.lon) + ")",
                    color = Ink, fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Panel2)
                        .clickable {
                            place = r.name + (if (r.post.isNotBlank()) " (" + r.post + ")" else "")
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
                value = eff, onValueChange = { eff = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Systemwirkungsgrad (%), üblich 75–90") }, singleLine = true,
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

        SectionCard(title = "Verschattung (Zeitfenster)") {
            Text(
                "Für Zeiten, in denen Baum, Haus oder Schornstein die Module beschatten: Die Prognose wird in dem Zeitfenster um den eingestellten Prozentsatz gemindert, an den gewählten Wochentagen.",
                color = Muted, fontSize = 12.5.sp
            )
            shades.forEachIndexed { i, sh ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel2).clickable { shadeEdit = i to sh }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(shadeText(sh), color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("ändern", color = Gold, fontSize = 13.sp)
                }
            }
            TextButton(onClick = { shadeEdit = -1 to Shade(Shade.ALL_DAYS, 8 * 60, 10 * 60, 20) }) { Text("+ Zeitfenster hinzufügen") }
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
                                eff = (eff.toIntOrNull() ?: 80).coerceIn(40, 100),
                                limitW = limit.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                shades = shades
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

private val DAY_NAMES = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private fun minText(m: Int): String = if (m >= 1440) "24:00" else String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

private fun daysText(mask: Int): String = when (mask) {
    Shade.ALL_DAYS -> "Täglich"
    31 -> "Mo–Fr"
    96 -> "Sa–So"
    else -> DAY_NAMES.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(", ")
}

private fun shadeText(s: Shade): String = daysText(s.days) + " " + minText(s.startMin) + "–" + minText(s.endMin) + " Uhr · −" + s.pct + " %"

@Composable
private fun ShadeDialog(initial: Shade, isNew: Boolean, onSave: (Shade) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(initial.days) }
    var start by remember { mutableFloatStateOf((initial.startMin / 15).toFloat()) }
    var end by remember { mutableFloatStateOf((initial.endMin / 15).toFloat()) }
    var pct by remember { mutableFloatStateOf(initial.pct.toFloat()) }
    val s = start.roundToInt() * 15
    val e = end.roundToInt() * 15
    val ok = days != 0 && e > s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neues Zeitfenster" else "Zeitfenster ändern") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Wochentage", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_NAMES.forEachIndexed { i, n ->
                        FilterChip(
                            selected = (days shr i) and 1 == 1,
                            onClick = { days = days xor (1 shl i) },
                            label = { Text(n, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row {
                    TextButton(onClick = { days = Shade.ALL_DAYS }) { Text("Täglich", fontSize = 12.sp) }
                    TextButton(onClick = { days = 31 }) { Text("Mo–Fr", fontSize = 12.sp) }
                    TextButton(onClick = { days = 96 }) { Text("Sa–So", fontSize = 12.sp) }
                }
                Text("Von " + minText(s) + " Uhr", fontSize = 14.sp)
                Slider(value = start, onValueChange = { start = it }, valueRange = 0f..95f, steps = 94)
                Text("Bis " + minText(e) + " Uhr", fontSize = 14.sp)
                Slider(value = end, onValueChange = { end = it }, valueRange = 1f..96f, steps = 94)
                Text("Minderung: " + pct.roundToInt() + " %", fontSize = 14.sp)
                Slider(value = pct, onValueChange = { pct = it }, valueRange = 5f..100f, steps = 18)
                if (e <= s) Text("Das Ende muss nach dem Anfang liegen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                if (!isNew) TextButton(onClick = onDelete) { Text("Zeitfenster löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(Shade(days, s, e, pct.roundToInt())) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
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

        SectionCard(title = "Datenschutz und Impressum") {
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text("Datenschutzerklärung öffnen") }
            TextButton(onClick = { uri.openUri(IMPRINT_URL) }) { Text("Impressum öffnen") }
        }

        SectionCard(title = "Info") {
            Text("Version " + BuildConfig.VERSION_NAME, color = Ink, fontSize = 14.sp)
            Text(
                "Wetter- und Strahlungsdaten: Open-Meteo.com, Lizenz CC BY 4.0. " +
                    "Jahreswerte: PVGIS, Europäische Kommission (JRC). " +
                    "Börsenpreise: Energy-Charts, Fraunhofer ISE, Lizenz CC BY 4.0. " +
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
