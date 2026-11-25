package de.autoknigge.solarprognose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

// ---------- Wettersymbol (gezeichnet, ohne Bilddateien) ----------

@Composable
fun WeatherIcon(kind: WeatherKind, modifier: Modifier = Modifier) {
    Canvas(modifier.size(36.dp)) {
        val s = size.minDimension

        fun sun(cx: Float, cy: Float, r: Float) {
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val c = Math.cos(a).toFloat()
                val sn = Math.sin(a).toFloat()
                drawLine(
                    color = Color(0xFFFFC53D),
                    start = Offset(cx + c * r * 1.35f, cy + sn * r * 1.35f),
                    end = Offset(cx + c * r * 1.8f, cy + sn * r * 1.8f),
                    strokeWidth = s * 0.06f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = Color(0xFFFFB81F), radius = r, center = Offset(cx, cy))
        }

        fun cloud(x: Float, y: Float, w: Float, col: Color) {
            val h = w * 0.55f
            drawCircle(col, h * 0.5f, Offset(x + w * 0.28f, y + h * 0.62f))
            drawCircle(col, h * 0.62f, Offset(x + w * 0.52f, y + h * 0.42f))
            drawCircle(col, h * 0.45f, Offset(x + w * 0.76f, y + h * 0.64f))
            drawRoundRect(col, Offset(x + w * 0.18f, y + h * 0.62f), Size(w * 0.66f, h * 0.45f), CornerRadius(h * 0.22f))
        }

        when (kind) {
            WeatherKind.Sun -> sun(0.5f * s, 0.5f * s, 0.2f * s)
            WeatherKind.SunCloud -> {
                sun(0.36f * s, 0.36f * s, 0.15f * s)
                cloud(0.22f * s, 0.34f * s, 0.72f * s, Color(0xFFF2F6F8))
            }
            WeatherKind.Cloud -> cloud(0.06f * s, 0.24f * s, 0.88f * s, Color(0xFFCBD5DB))
            WeatherKind.Rain -> {
                cloud(0.1f * s, 0.06f * s, 0.8f * s, Color(0xFFB8C4CC))
                val blue = Color(0xFF6CB6F2)
                for (dx in floatArrayOf(0.30f, 0.50f, 0.70f)) {
                    drawLine(blue, Offset(dx * s, 0.66f * s), Offset((dx - 0.05f) * s, 0.86f * s), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                }
            }
            WeatherKind.Snow -> {
                cloud(0.1f * s, 0.06f * s, 0.8f * s, Color(0xFFB8C4CC))
                for (dx in floatArrayOf(0.30f, 0.50f, 0.70f)) {
                    val cx = dx * s
                    val cy = (if (dx == 0.50f) 0.84f else 0.76f) * s
                    val r = 0.055f * s
                    for (k in 0 until 3) {
                        val a = Math.toRadians(k * 60.0 + 90.0)
                        val ex = (Math.cos(a) * r).toFloat()
                        val ey = (Math.sin(a) * r).toFloat()
                        drawLine(Color.White, Offset(cx - ex, cy - ey), Offset(cx + ex, cy + ey), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
                    }
                }
            }
            WeatherKind.Hail -> {
                cloud(0.1f * s, 0.06f * s, 0.8f * s, Color(0xFF9AA8B2))
                for (p in listOf(0.30f to 0.76f, 0.50f to 0.86f, 0.70f to 0.76f)) {
                    drawCircle(Color(0xFFE8F4FF), 0.045f * s, Offset(p.first * s, p.second * s))
                }
            }
            WeatherKind.Thunder -> {
                cloud(0.1f * s, 0.04f * s, 0.8f * s, Color(0xFF7D8A94))
                val bolt = Path().apply {
                    moveTo(0.54f * s, 0.50f * s)
                    lineTo(0.38f * s, 0.74f * s)
                    lineTo(0.49f * s, 0.74f * s)
                    lineTo(0.42f * s, 0.96f * s)
                    lineTo(0.64f * s, 0.67f * s)
                    lineTo(0.52f * s, 0.67f * s)
                    close()
                }
                drawPath(bolt, Color(0xFFFFD43B))
            }
            WeatherKind.Storm -> {
                val wind = Color(0xFFCBD5DB)
                val st = Stroke(width = s * 0.06f, cap = StrokeCap.Round)
                fun gust(y: Float, x0: Float, x1: Float, curl: Float) {
                    drawLine(wind, Offset(x0 * s, y * s), Offset(x1 * s, y * s), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
                    drawArc(
                        color = wind, startAngle = -90f, sweepAngle = 270f, useCenter = false,
                        topLeft = Offset((x1 - curl) * s, (y - 2 * curl) * s), size = Size(2 * curl * s, 2 * curl * s), style = st
                    )
                }
                gust(0.34f, 0.08f, 0.62f, 0.10f)
                gust(0.56f, 0.20f, 0.74f, 0.09f)
                drawLine(wind, Offset(0.08f * s, 0.78f * s), Offset(0.50f * s, 0.78f * s), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
            }
        }
    }
}

// ---------- Heute im Verlauf ----------

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color = Ink) {
    Column {
        Text(label, fontSize = 11.sp, color = Muted)
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(" $unit", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** Tagesprognose, bisher erzeugte Menge (aus dem Wetter berechnet) und erwarteter Rest, kompakt. */
class ModelRow(val id: String, val name: String, val v0: Double?, val v1: Double?, val s0: Double?, val s1: Double?)

@Composable
fun ModelCard(rows: List<ModelRow>, label0: String, label1: String, selected: String, onSelect: (String) -> Unit) {
    var info by remember { mutableStateOf(false) }
    if (info) androidx.compose.material3.AlertDialog(
        onDismissRequest = { info = false },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { info = false }) { Text("Schließen") } },
        title = { Text("Die Wettermodelle") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vier große numerische Wettermodelle, auf deren Strahlungs- und Wetterdaten die Ertragsprognose beruht.", fontSize = 13.sp)
                Text("ICON – Globales Modell des Deutschen Wetterdienstes (mit hochauflösenden Ablegern ICON-EU und ICON-D2). Wird mehrmals täglich gerechnet; in der App Grundlage der Umrechnung auf deine Anlage.", fontSize = 13.sp)
                Text("ECMWF – Europäisches Zentrum für mittelfristige Wettervorhersage (Reading). Gilt oft als Referenz, besonders für 3 bis 10 Tage.", fontSize = 13.sp)
                Text("GFS – Globales Modell der US-Wetterbehörde NOAA. Läuft viermal täglich, dient oft als Zweitmeinung.", fontSize = 13.sp)
                Text("Arpège – Modell des französischen Wetterdienstes Météo-France, höher aufgelöst über Europa, vor allem für die nächsten Tage (bis etwa 4).", fontSize = 13.sp)
                Text("Welches ist das beste? Keines gewinnt in jeder Lage. In Mitteleuropa sind ECMWF und ICON meist die verlässliche Basis, Arpège und GFS die Gegenprobe. Springen die Werte stark auseinander, ist die Wetterlage unsicher (Nebel, Wolkenlücken). Am genauesten wird es, wenn man die Prognose mit den echten Erträgen der eigenen Anlage vergleicht.", fontSize = 13.sp)
            }
        }
    )
    SectionCard(title = "Modellvergleich* (reine Modellwerte, kWh)") {
        fun tone(v: Double?, all: List<Double?>): Color {
            val vs = all.filterNotNull()
            if (v == null || vs.size < 2 || vs.max() - vs.min() < 0.05) return Ink
            return if (v == vs.max()) TileToday else if (v == vs.min()) TileTomorrow else Ink
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(" ", fontSize = 12.sp)
                Text(label0, fontSize = 13.sp, color = Muted)
                Text("🌞 Std.", fontSize = 12.sp, color = Muted)
                Text(label1, fontSize = 13.sp, color = Muted)
                Text("🌞 Std.", fontSize = 12.sp, color = Muted)
            }
            for (r in rows) {
                val frame = (if (r.id == selected) Modifier.border(1.5.dp, Gold, RoundedCornerShape(12.dp)) else Modifier).clip(RoundedCornerShape(12.dp)).clickable { onSelect(r.id) }
                Column(frame.padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.name, fontSize = 12.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                    Text(r.v0?.let { Calc.fmt1(it) } ?: "–", fontSize = 15.sp, color = tone(r.v0, rows.map { it.v0 }), fontWeight = FontWeight.Bold)
                    Text(r.s0?.let { Calc.fmt1(it) + " h" } ?: "–", fontSize = 12.sp, color = Muted)
                    Text(r.v1?.let { Calc.fmt1(it) } ?: "–", fontSize = 15.sp, color = tone(r.v1, rows.map { it.v1 }), fontWeight = FontWeight.Bold)
                    Text(r.s1?.let { Calc.fmt1(it) + " h" } ?: "–", fontSize = 12.sp, color = Muted)
                }
            }
        }
        Text(
            "Gleiche Anlage, die Einstrahlung jedes Modells auf die Prognose der App umgerechnet (ICON = Grundlage der Umrechnung). Antippen wählt das Modell für Kacheln, Balken und Summen. Große Abstände heißen: die Wetterlage ist unsicher.",
            fontSize = 11.sp, color = Muted, lineHeight = 15.sp
        )
        Text("* Was bedeuten ICON, ECMWF, GFS, Arpège?", fontSize = 12.sp, color = Gold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, modifier = Modifier.padding(top = 6.dp).clickable { info = true })
    }
}

@Composable
fun TomorrowCard(total: Double, sunTotal: Double?, firstKwh: String?) {
    SectionCard(title = "") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompactStat("Prognose morgen", Calc.fmt1(total), "kWh")
            if (firstKwh != null) CompactStat("Erste kWh um", firstKwh, "Uhr", TodayOrange)
            if (sunTotal != null) CompactStat("Sonne", Calc.fmt1(sunTotal), "h", GoldSoft)
        }
        Text(
            "Ab 2 Stunden nach Sonnenuntergang zeigt die App die Prognose für morgen. „Erste kWh“: wann deine Anlage (inkl. Verschattung) die erste Kilowattstunde erzeugt haben wird.",
            fontSize = 11.sp, color = Muted, lineHeight = 15.sp
        )
    }
}

@Composable
fun TodayCard(total: Double, rest: Double, nowKw: Double? = null, sunTotal: Double? = null, sunRest: Double? = null, satNote: String? = null, diag: String? = null, stationLabel: String? = null, onStation: (() -> Unit)? = null) {
    val done = (total - rest).coerceAtLeast(0.0)
    val frac = if (total > 0.0) (done / total).toFloat().coerceIn(0f, 1f) else 0f
    SectionCard(title = "") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (nowKw != null) CompactStat("Jetzt", Calc.fmt2(nowKw), "kW", TileToday)
            CompactStat("Prognose", Calc.fmt1(total), "kWh")
            CompactStat("bisher", Calc.fmt1(done), "kWh", TodayOrange)
            CompactStat("noch", Calc.fmt1(rest), "kWh", GoldSoft)
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Panel2)) {
            if (frac > 0.001f) Box(Modifier.weight(frac).fillMaxHeight().background(TodayOrange))
            if (frac < 0.999f) Box(Modifier.weight(1f - frac).fillMaxHeight().background(GoldSoft.copy(alpha = 0.35f)))
        }
        Text(
            (if (sunTotal != null) "Sonne heute " + Calc.fmt1(sunTotal) + " h" + (if (sunRest != null) ", noch " + Calc.fmt1(sunRest) + " h" else "") + " · " else "") +
                (if (satNote != null) satNote else "„Bisher“ aus dem Wetter berechnet, nicht gemessen."),
            fontSize = 11.sp, color = Muted, lineHeight = 15.sp
        )
        if (diag != null || onStation != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (diag != null) Text(diag, fontSize = 10.sp, color = Muted.copy(alpha = 0.8f), lineHeight = 13.sp, modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            if (onStation != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Gold, RoundedCornerShape(12.dp))
                        .clickable { onStation() }.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("DWD-Station\nändern", fontSize = 11.sp, lineHeight = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** Auswahl der DWD-Station für die beobachtete Bewölkung: Liste der nächsten Stationen mit Entfernung. */
@Composable
fun ObsStationDialog(plant: Plant, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<Extras.ObsStation>?>(null) }
    var failed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(plant.id) {
        try { list = Extras.loadObsStations(plant) } catch (e: Exception) { failed = true }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = { Text("DWD-Station für die Bewölkung") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Die beobachtete Bewölkung dieser Station korrigiert am Morgen die Tagesprognose. Nicht jede Station meldet Bewölkung; dann steht in der Diagnose „keine Daten“.", fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                @Composable fun row(label: String, id: String) {
                    val on = plant.obsStation == id
                    Text(
                        (if (on) "✓ " else "") + label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(vertical = 8.dp)
                    )
                }
                row("Automatisch (nächste Station mit Bewölkung)", "")
                val l = list
                if (l == null) Text(if (failed) "Stationsliste gerade nicht erreichbar." else "Lade Stationen …", fontSize = 12.sp)
                else if (l.isEmpty()) Text("Keine Stationen im Umkreis gefunden.", fontSize = 12.sp)
                else l.forEach { row(it.name + " · " + Calc.fmt0(it.km) + " km", it.id) }
            }
        }
    )
}

@Composable
private fun CompactStat(label: String, value: String, unit: String, color: Color = Ink) {
    Column {
        Text(label, fontSize = 10.5.sp, color = Muted)
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(" $unit", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

// ---------- Jahresprognose ----------

@Composable
fun YearCard(kwp: Double, data: YearData, marks: List<Double>? = null) {
    val perKwp = data.perKwp
    var sel by remember { mutableStateOf<Int?>(null) }
    val months = perKwp.map { it * kwp }
    val total = months.sum()
    val names = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    val today = LocalDate.now()
    SectionCard(title = "Jahresprognose " + java.time.LocalDate.now().year + ": ca. " + Calc.fmt0(total) + " kWh") {
        val s = sel
        val ytdSum = marks?.take(today.monthValue)?.sum()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (s != null && s < months.size) {
                val days = YearMonth.of(today.year, s + 1).lengthOfMonth()
                val mk = marks?.getOrNull(s)?.takeIf { s <= today.monthValue - 1 }
                Text(names[s] + ": Prognose " + Calc.fmt0(months[s]) + " kWh · Ø " + Calc.fmt1(months[s] / days) + " kWh pro Tag", fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp)
                if (mk != null) Text(
                    "Erzielt " + today.year + (if (s == today.monthValue - 1) " bisher" else "") + ": " + Calc.fmt0(mk) + " kWh · " +
                        Calc.fmt0(if (months[s] > 0) mk / months[s] * 100.0 else 0.0) + " % der Prognose erreicht",
                    fontSize = 12.5.sp, color = Color(0xFFFF9A2E), lineHeight = 17.sp
                )
            } else {
                if (ytdSum != null && total > 0) {
                    val mi = today.monthValue - 1
                    val frac = ((today.dayOfMonth - 1) + java.time.LocalTime.now().hour / 24.0) / YearMonth.of(today.year, mi + 1).lengthOfMonth()
                    val expected = months.take(mi).sum() + months[mi] * frac
                    Text(
                        "Erzielt bislang " + today.year + ": " + Calc.fmt0(ytdSum) + " kWh (" + Calc.fmt0(ytdSum / total * 100.0) + " %)",
                        fontSize = 12.5.sp, color = Color(0xFFFF9A2E), lineHeight = 17.sp
                    )
                    if (expected > 0) {
                        val dev = (ytdSum / expected - 1.0) * 100.0
                        val proj = ytdSum + (total - expected)
                        val devY = (proj / total - 1.0) * 100.0
                        Text(
                            "Prognose bis heute: " + Calc.fmt0(expected) + " kWh · bislang " +
                                (if (dev >= 0) Calc.fmt1(dev) + " % vor" else Calc.fmt1(-dev) + " % hinter") + " der Erwartung",
                            fontSize = 12.5.sp, color = Color.White, lineHeight = 17.sp
                        )
                        Text(
                            "Dieses Jahr liegt mit voraussichtlich " + Calc.fmt0(proj) + " kWh " +
                                (if (devY >= 0) Calc.fmt1(devY) + " % vor" else Calc.fmt1(-devY) + " % hinter") + " der Prognose.",
                            fontSize = 12.5.sp, color = Color.White, lineHeight = 17.sp
                        )
                    }
                }
            }
            if (marks != null) Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("Weiß = Prognose") }
                    append(". ")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFF9A2E), fontWeight = FontWeight.Bold)) { append("Orange & Strich: Ergebnis bis heute") }
                },
                fontSize = 11.5.sp, color = Muted
            )
        }
        MonthChart(values = months, currentMonth = today.monthValue - 1, selected = sel, onSelect = { sel = it }, marks = marks)
        val yrs = Regex("Jahre (\\d{4}) bis (\\d{4})").find(data.label)
        Text(
            if (data.label.contains("Open-Meteo") && yrs != null)
                "Berechneter Durchschnitt incl. deiner Verschattung und Wechselrichter-Grenze, ohne Schnee, aus Daten der Jahre " +
                    yrs.groupValues[1] + " bis " + yrs.groupValues[2] + " (Open-Meteo)."
            else data.label + ", ohne Verschattung, Schnee und Wechselrichter-Grenze.",
            fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
        )
    }
}

// ---------- Börsenstrompreis ----------

private fun priceHourText(p: PriceHour, zone: ZoneId): String {
    val z = Instant.ofEpochSecond(p.start).atZone(zone)
    return String.format(Locale.GERMANY, "%02d–%02d Uhr", z.hour, (z.hour + 1) % 24)
}

@Composable
fun PriceCard(prices: List<PriceHour>, nowSec: Long) {
    var sel by remember { mutableStateOf<Int?>(null) }
    val zone = remember { ZoneId.of("Europe/Berlin") }
    val today = LocalDate.now(zone)
    fun dateOf(p: PriceHour): LocalDate = Instant.ofEpochSecond(p.start).atZone(zone).toLocalDate()
    val cur = prices.firstOrNull { nowSec >= it.start && nowSec < it.start + 3600 }
    val todays = prices.filter { dateOf(it) == today }
    val hasTomorrow = prices.any { dateOf(it) == today.plusDays(1) }
    val cheapest = todays.minByOrNull { it.ct }
    val dearest = todays.maxByOrNull { it.ct }

    SectionCard(title = "Börsenstrompreis (ct/kWh)", bg = CardPrice, borderColor = CardPriceLine) {
        val s = sel
        val text = if (s != null && s < prices.size) {
            val p = prices[s]
            val d = dateOf(p).toString()
            Calc.weekday(d) + " " + Calc.dm(d) + ", " + priceHourText(p, zone) + "\n" + Calc.fmt1(p.ct) + " ct/kWh"
        } else {
            (if (cur != null) "Jetzt: " + Calc.fmt1(cur.ct) + " ct/kWh" else "Gerade kein aktueller Preis") + "\n" +
                if (cheapest != null && dearest != null) {
                    "Heute am günstigsten " + priceHourText(cheapest, zone) + " (" + Calc.fmt1(cheapest.ct) + " ct), am teuersten " +
                        priceHourText(dearest, zone) + " (" + Calc.fmt1(dearest.ct) + " ct)"
                } else {
                    " "
                }
        }
        Text(text, fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp)
        PriceChart(prices = prices, nowSec = nowSec, selected = sel, onSelect = { sel = it })
        Text(
            (if (!hasTomorrow) "Die Preise für morgen veröffentlicht die Börse gegen 13 Uhr. " else "") +
                "Netto-Börsenpreis (Day-Ahead, Gebotszone DE-LU) ohne Steuern, Abgaben und Netzentgelte.",
            fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
        )
    }
}


/** Strommix Deutschland (Energy-Charts): Tag/Woche/Jahr, Schieber für einzelne Zeitpunkte, Import und Export. */
@Composable
fun MixCard(range: Int, series: MixSeries?, failed: Boolean, gas: Extras.GasStorage?, onRange: (Int) -> Unit) {
    var showGas by remember { mutableStateOf(false) }
    val colors = mapOf(
        "Kohle" to Color(0xFFA9896B), "Erdgas" to Color(0xFFD4714F), "Biomasse" to Color(0xFF2E8B57),
        "Wind" to Color(0xFF4B5A9E), "Wasserkraft" to Color(0xFF5FD0E6), "Pumpspeicher" to Color(0xFF7FA6D9), "Batterie" to Color(0xFFB07CD8), "Solar" to Color(0xFFE3B341), "Sonstige" to Color(0xFFA0A0A0),
        "Import" to Color(0xFFF94001), "Export" to Color(0xFF9DBF5B)
    )
    val zone = remember { ZoneId.of("Europe/Berlin") }
    // Tag: letzter Zeitpunkt (aktuell). Woche und Jahr: zuerst der Durchschnitt des ganzen Zeitraums, ein Zeitpunkt nur nach Antippen.
    var sel by remember(series, range) { mutableStateOf<Int?>(if (range == 0) series?.t?.let { it.size - 1 } else null) }
    // CO2 je kWh: aus dem Erzeugungsmix mit festen Durchschnittsfaktoren geschätzt (Energy-Charts liefert dafür keine Daten)
    val co2 = series?.let { sr ->
        val nn = sr.t.size
        val si = sel
        fun a(g: String): Double = sr.v[g]?.let { if (si != null) it[si.coerceIn(0, nn - 1)] else it.average() } ?: 0.0
        val p = MIX_GROUPS_UI.sumOf { a(it) }
        // Kohle getrennt nach Braun- (1105 g/kWh) und Steinkohle (970 g/kWh), sonst Mittelwert
        val brown = a("Braunkohle")
        val hard = a("Steinkohle")
        val coal = if (brown + hard > 0.0) brown * 1105.0 + hard * 970.0 else a("Kohle") * (CO2_G["Kohle"] ?: 0.0)
        if (p > 0.0) (MIX_GROUPS_UI.filter { it != "Kohle" }.sumOf { a(it) * (CO2_G[it] ?: 0.0) } + coal) / p else null
    }
    SectionCard(
        title = "Strommix Deutschland",
        trailing = co2?.let { c -> { Text("≈ " + Calc.fmt0(c) + " g CO₂/kWh", fontSize = 11.sp, color = Color(0xFF9A9A9A)) } },
        bg = Color.Black, borderColor = Color(0xFF3A3A3A), inkColor = Color(0xFFEDEDED), mutedColor = Color(0xFF9A9A9A), darken = 0f
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tag", "Woche", "Jahr").forEachIndexed { i, label ->
                val on = i == range
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFFE3B341) else Color(0xFF262626))
                        .clickable { onRange(i) }.padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(label, fontSize = 13.sp, color = if (on) Color.Black else Color(0xFFEDEDED), fontWeight = FontWeight.SemiBold) }
            }
        }
        if (series == null) {
            Text(if (failed) "Daten gerade nicht erreichbar." else "Lade Daten …", fontSize = 13.sp, color = LocalMuted.current)
            return@SectionCard
        }
        val n = series.t.size
        val s = sel
        fun avg(g: String): Double = if (s != null) series.v[g]!![s.coerceIn(0, n - 1)] else series.v[g]!!.average()
        val prod = MIX_GROUPS_UI.sumOf { avg(it) }.coerceAtLeast(1.0)
        fun fmt(ts: Long, withTime: Boolean): String {
            val z = Instant.ofEpochSecond(ts).atZone(zone)
            val d = String.format(Locale.GERMANY, "%02d.%02d.%d", z.dayOfMonth, z.monthValue, z.year)
            return if (withTime) d + String.format(Locale.GERMANY, " %02d:%02d Uhr", z.hour, z.minute) else d
        }
        val first = fmt(series.t[0], false)
        val lastT = series.t[n - 1]
        val head = if (s != null) {
            (if (series.estFrom in 0..s) "Jetzt (vorläufig) " else if (s >= n - 1 && range != 2) "Aktuell " else "") + fmt(series.t[s.coerceIn(0, n - 1)], range != 2)
        } else when (range) {
            0 -> "Heute bis " + String.format(Locale.GERMANY, "%02d:%02d Uhr", Instant.ofEpochSecond(lastT + 900).atZone(zone).hour, Instant.ofEpochSecond(lastT + 900).atZone(zone).minute)
            else -> "Durchschnitt $first bis ${fmt(lastT, false)}"
        }
        Text(head + " · Erzeugung " + Calc.fmt1(prod / 1000.0) + " GW" + (series.v["Last"]?.let { la -> (if (s != null) la[s.coerceIn(0, n - 1)] else la.average()) }?.takeIf { it > 1000.0 }?.let { " · Last (öffentlich, ohne Eigenverbrauch) " + Calc.fmt1(it / 1000.0) + " GW" } ?: "") + " (Prozent = Anteil an der Erzeugung)", fontSize = 12.sp, color = LocalMuted.current)
        val top = MIX_GROUPS_UI.maxOf { avg(it) / prod * 100.0 }.coerceAtLeast(1.0)
        @Composable fun bar(name: String, label: String, gwOnly: Boolean = false) {
            val pct = avg(name) / prod * 100.0
            val w = ((pct / top) * 0.78).toFloat().coerceIn(0.01f, 0.78f)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Row(Modifier.weight(0.34f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, fontSize = 13.sp, color = LocalInk.current)
                    if (name == "Erdgas" && gas != null) {
                        Spacer(Modifier.width(8.dp))
                        val diff = gas.avg?.let { gas.full - it }
                        val bgc = when { diff == null -> Color(0xFF6B6B6B); diff >= 0.0 -> Color(0xFF1E8E4E); diff >= -15.0 -> Color(0xFFF59E1B); else -> Color(0xFFD32F2F) }
                        val fgc = if (diff != null && diff < 0.0 && diff >= -15.0) Color.Black else Color.White
                        val px = androidx.compose.ui.platform.LocalDensity.current.density
                        val ball = Brush.radialGradient(
                            listOf(androidx.compose.ui.graphics.lerp(bgc, Color.White, 0.45f), bgc, androidx.compose.ui.graphics.lerp(bgc, Color.Black, 0.55f)),
                            center = Offset(10f * px, 8f * px), radius = 26f * px
                        )
                        val frac = (gas.full / 100.0).toFloat().coerceIn(0f, 1f)
                        val emptyCol = androidx.compose.ui.graphics.lerp(bgc, Color.Black, 0.62f)
                        Box(
                            Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .border(1.5.dp, Gold, androidx.compose.foundation.shape.CircleShape).clickable { showGas = !showGas },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                val w = size.width
                                val h = size.height
                                drawRect(emptyCol)
                                val level = h * (1f - frac)
                                val amp = h * 0.045f
                                val wave = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, level)
                                    var x = 0f
                                    while (x <= w) {
                                        lineTo(x, level + amp * Math.sin(x / w * 2.0 * Math.PI * 1.5).toFloat())
                                        x += 1f
                                    }
                                    lineTo(w, h); lineTo(0f, h); close()
                                }
                                drawPath(wave, ball)
                                val line = androidx.compose.ui.graphics.Path().apply {
                                    var x = 0f
                                    moveTo(0f, level)
                                    while (x <= w) {
                                        lineTo(x, level + amp * Math.sin(x / w * 2.0 * Math.PI * 1.5).toFloat())
                                        x += 1f
                                    }
                                }
                                drawPath(line, Color(0xCCFFFFFF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                            }
                            Text(
                                Calc.fmt0(gas.full) + "%", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 0f), 4f))
                            )
                        }
                    }
                }
                Row(Modifier.weight(0.66f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(w).height(22.dp).background(colors[name] ?: Color.Gray))
                    Text(if (gwOnly) Calc.fmt2(avg(name) / 1000.0) + " GW" else Calc.fmt1(pct) + " % · " + Calc.fmt1(avg(name) / 1000.0) + " GW", fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                    Box(Modifier.weight((1f - w).coerceAtLeast(0.01f)))
                }
            }
            if (name == "Erdgas" && gas != null && showGas) {
                Text(
                    "Gasspeicher Deutschland: " + Calc.fmt1(gas.full) + " % gefüllt (Stand " + gas.date.substring(8, 10) + "." + gas.date.substring(5, 7) + ".)" +
                        (gas.avg?.let { ", Durchschnitt der letzten 5 Jahre zu dieser Zeit " + Calc.fmt1(it) + " %" } ?: "") + ". Quelle: GIE AGSI+",
                    fontSize = 11.sp, color = LocalMuted.current, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
        }
        val scaleGw = (top / 100.0 * prod / 1000.0).coerceAtLeast(0.5)
        @Composable fun storage(label: String, name: String, load: String, color: Color) {
            val l = (series.v[load]?.let { if (s != null) it[s.coerceIn(0, n - 1)] else it.average() } ?: 0.0) / 1000.0
            val gv = avg(name) / 1000.0
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(0.34f))
                Row(Modifier.weight(0.66f).height(16.dp)) {
                    Box(Modifier.weight(0.5f).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                        if (l > 0.005) Box(Modifier.fillMaxWidth((l / scaleGw * 0.78).toFloat().coerceIn(0.01f, 1f)).height(14.dp).background(color))
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF6A6A6A)))
                    Box(Modifier.weight(0.5f).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                        if (gv > 0.005) Box(Modifier.fillMaxWidth((gv / scaleGw * 0.78).toFloat().coerceIn(0.01f, 1f)).height(14.dp).background(color))
                    }
                }
            }
            Text(
                "laden " + Calc.fmt2(l) + " GW · abgeben " + Calc.fmt2(gv) + " GW (" + Calc.fmt1(avg(name) / prod * 100.0) + " %)",
                fontSize = 11.5.sp, color = LocalMuted.current
            )
        }
        MIX_GROUPS_UI.forEach {
            if (it == "Pumpspeicher") storage(it, it, "PumpLaden", colors[it] ?: Color.Gray)
            else if (it == "Batterie") storage("Batteriespeicher", it, "BattLaden", colors[it] ?: Color.Gray)
            else bar(it, it)
        }
        if (series.hasTrade) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3A3A)))
            bar("Import", "Import", true)
            bar("Export", "Export", true)
            Text(
                "Quelle Import/Export: " + (series.tradeSrc.ifEmpty { "nur Saldo aus public_power" }) + (if (range == 1) " · Stundenmittel" else if (range == 2) " · Mittel je 2 Tage" else ""),
                fontSize = 11.sp, color = if (series.tradeSrc.startsWith("Handel")) Color(0xFFF59E1B) else LocalMuted.current
            )
            val net = (avg("Import") - avg("Export")) / 1000.0
            Text(
                (if (net >= 0) "Netto-Import (Import-Export) " else "Netto-Export (Export-Import) ") + Calc.fmt2(Math.abs(net)) + " GW",
                fontSize = 12.sp, color = LocalMuted.current
            )
            val zus = avg("ImportBilanz") / 1000.0
            if (zus > 0.005) Text(
                "Im Import enthalten: " + Calc.fmt2(zus) + " GW rechnerisch ergänzt (Bilanz: Erzeugung, Speicher netto, + Import - Export = Last). " +
                    "Gemessen: " + Calc.fmt2((avg("Import") - avg("ImportBilanz")) / 1000.0) + " GW.",
                fontSize = 11.5.sp, color = Color(0xFFE3B341), lineHeight = 15.sp
            )
        }
        if (n > 1) {
            androidx.compose.material3.Slider(
                value = (s ?: (n - 1)).toFloat(),
                onValueChange = { sel = it.toInt().coerceIn(0, n - 1) },
                valueRange = 0f..(n - 1).toFloat(),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFFE3B341), activeTrackColor = Color(0xFFE3B341), inactiveTrackColor = Color(0xFF3A3A3A))
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (s == null) "Schieber: einzelnen Zeitpunkt wählen" else "Durchschnitt der Ansicht anzeigen",
                    fontSize = 12.sp, color = if (s == null) LocalMuted.current else Color(0xFFE3B341),
                    modifier = if (s == null) Modifier else Modifier.clickable { sel = null }
                )
            }
        }
        if (series.estFrom >= 0) Text("Vorläufig: ab " + String.format(Locale.GERMANY, "%02d:%02d Uhr", Instant.ofEpochSecond(series.t[series.estFrom]).atZone(zone).hour, Instant.ofEpochSecond(series.t[series.estFrom]).atZone(zone).minute) + " Solar und Wind aus der Energy-Charts-Prognose, übrige Werte vom letzten Messwert fortgeschrieben.", fontSize = 10.5.sp, color = Color(0xFFE3B341), lineHeight = 14.sp)
        Text("CO₂: Schätzung aus dem Erzeugungsmix mit festen Durchschnittswerten je Energieträger, ohne Import. Keine amtliche Messung.", fontSize = 10.5.sp, color = LocalMuted.current, lineHeight = 14.sp)
        Text("Nettostromerzeugung der öffentlichen Stromversorgung ohne Eigenerzeugung der Industrie, Anteile an der Erzeugung. Import und Export: physikalische Flüsse über die Grenzen. Der Import ist eine saldierte Zahl: Fehlt in den Quelldaten Strom zur Deckung der Last, wird die Lücke als Import ergänzt (gelb ausgewiesen). Quelle: Fraunhofer ISE, energy-charts.info (CC BY 4.0)", fontSize = 10.5.sp, color = LocalMuted.current, lineHeight = 14.sp)
    }
}

private val MIX_GROUPS_UI = Extras.MIX_GROUPS

/** Durchschnittliche Emission in g CO₂ je kWh (Lebenszyklus inkl. Vorkette, Deutschland; Kohle = Mittel aus Braun- und Steinkohle nach Strommengen 2025). */
private val CO2_G = mapOf(
    "Kohle" to 1065.0, "Erdgas" to 460.0, "Biomasse" to 290.0, "Wind" to 12.0, "Wasserkraft" to 12.0, "Pumpspeicher" to 86.0, "Batterie" to 55.0, "Solar" to 56.0, "Sonstige" to 500.0
)

/** Anteil von Solar und Erneuerbaren am aktuellen Strombedarf in Deutschland (Energy-Charts). */
@Composable
fun GridNowCard(m: MixSeries) {
    val solar = m.solarShare
    val ren = m.renShare
    if (solar == null || ren == null) return
    val zone = ZoneId.of("Europe/Berlin")
    val z = Instant.ofEpochSecond(m.shareT).atZone(zone)
    SectionCard(
        title = "Strombedarf jetzt: Anteil Solar und Erneuerbare",
        bg = Color(0xFFE27602), borderColor = Color(0xFFFFB27A), inkColor = Color.Black, mutedColor = Color.Black
    ) {
        Text(
            "Deutschland, Stand " + String.format(Locale.GERMANY, "%02d:%02d Uhr", z.hour, z.minute) + (if (m.estFrom in 0 until m.t.size && m.shareT >= m.t[m.estFrom]) " (vorläufig)" else ""),
            fontSize = 12.sp, color = LocalMuted.current
        )
        listOf(
            Triple("Solarstrom (Anteil am Verbrauch)", solar, Color(0xFFE3B341)),
            Triple("Erneuerbare gesamt (Anteil am Verbrauch)", ren, Color(0xFF4FB477))
        ).forEach { (label, pct, col) ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(label, fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f))
                    Text(Calc.fmt1(pct), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = col)
                    Text(" %", fontSize = 13.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 4.dp))
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x55000000))) {
                    Box(Modifier.fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0.01f, 1f)).height(10.dp).background(col))
                }
            }
        }
        Text(
            "Anteil an der aktuellen Last (Stromverbrauch) in Deutschland. Quelle: Fraunhofer ISE, energy-charts.info (CC BY 4.0)",
            fontSize = 10.5.sp, color = LocalMuted.current, lineHeight = 14.sp
        )
    }
}

/**
 * Eine Tageskachel: Leistungskurve (gemeinsame Skala für alle Tage) und Temperaturverlauf.
 * [k] = Index des Tages in [f]; Skalen kommen von außen, damit die Tage vergleichbar sind.
 */
@Composable
fun DayCard(f: Forecast, k: Int, topW: Double, stepW: Double, tMin: Double, tMax: Double, base: Color) {
    val d = f.days[k]
    val dark = base.luminance() > 0.45f
    val ink = if (dark) Color(0xFF2B1600) else Color.White
    val muted = if (dark) Color(0xFF4A2D0C) else Color(0xFFFFF1DE)
    val tempCol = if (dark) Color(0xFF0E2F4F) else Color(0xFF10263D)
    val shape = RoundedCornerShape(14.dp)
    val hs = (d.start until d.end).mapNotNull { f.hours.getOrNull(it) }
    val tLo = hs.minOfOrNull { it.temp }
    val tHi = hs.maxOfOrNull { it.temp }
    val rel = k - f.todayIndex
    Column(
        Modifier.fillMaxWidth().clip(shape).background(cardBrush(base, 0.35f)).border(1.dp, base.copy(alpha = 0.6f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                (if (rel in -2..1) dayName(rel, d.date).uppercase(Locale.GERMAN) + " · " else "") + Calc.weekday(d.date) + " " + Calc.dm(d.date),
                fontSize = 13.sp, letterSpacing = 0.6.sp, color = muted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Text(Calc.fmt1(d.kwh), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(" kWh", fontSize = 13.sp, color = muted, modifier = Modifier.padding(start = 2.dp, end = 10.dp, top = 8.dp))
            WeatherIcon(Extras.kindOf(d))
        }
        Text(
            "Spitze " + Calc.fmt1(d.peak / 1000.0) + " kW gegen " + d.peakHour + " Uhr" +
                (d.sunH?.let { " · Sonne " + Calc.fmt1(it) + " h" } ?: "") +
                (d.cloudAvg?.let { " · Wolken " + Calc.fmt0(it) + " %" } ?: "") +
                (if (d.rainMm >= 0.1) " · Regen " + Calc.fmt1(d.rainMm) + " mm" else "") +
                (if (tLo != null && tHi != null) " · " + Calc.fmt0(tLo) + " bis " + Calc.fmt0(tHi) + " °C" else ""),
            fontSize = 12.sp, color = muted, lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("▇ Leistung (kW, links)", fontSize = 11.sp, color = muted)
            Text("━ Temperatur (°C, rechts)", fontSize = 11.sp, color = tempCol, fontWeight = FontWeight.SemiBold)
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val tick = remember(density, muted) {
            android.graphics.Paint().apply {
                isAntiAlias = true; color = muted.toArgb(); textSize = with(density) { 10.5.sp.toPx() }
            }
        }
        val tickTemp = remember(density, tempCol) {
            android.graphics.Paint().apply {
                isAntiAlias = true; color = tempCol.toArgb(); textSize = with(density) { 10.5.sp.toPx() }; isFakeBoldText = true
            }
        }
        var selH by remember(k) { mutableStateOf<Int?>(null) }
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            val ml = 24.dp.toPx()
            val mr = 22.dp.toPx()
            val mt = 8.dp.toPx()
            val mb = 22.dp.toPx()
            val iw = w - ml - mr
            val ih = h - mt - mb
            val nc = drawContext.canvas.nativeCanvas
            val lo = 4
            val hi = 22
            fun xOf(o: Int): Float = ml + (o - lo).toFloat() / (hi - lo) * iw
            fun yP(v: Double): Float = mt + ih - (v / topW * ih).toFloat()
            val span = (tMax - tMin).coerceAtLeast(4.0)
            fun yT(v: Double): Float = mt + ih - ((v - tMin) / span * ih).toFloat()
            // Raster und linke Achse (Watt)
            var gv = 0.0
            tick.textAlign = android.graphics.Paint.Align.RIGHT
            while (gv <= topW + 0.001) {
                drawLine(
                    color = muted.copy(alpha = if (gv == 0.0) 0.6f else 0.25f), start = Offset(ml, yP(gv)), end = Offset(w - mr, yP(gv)),
                    strokeWidth = 1.dp.toPx()
                )
                nc.drawText(if (stepW >= 1000.0) Calc.fmt0(gv / 1000.0) else Calc.fmt1(gv / 1000.0), ml - 4.dp.toPx(), yP(gv) + 4.dp.toPx(), tick)
                gv += stepW
            }
            // Rechte Achse (Temperatur): Mitte und Ränder
            tickTemp.textAlign = android.graphics.Paint.Align.LEFT
            nc.drawText(Calc.fmt0(tMin + span), w - mr + 4.dp.toPx(), mt + 10.dp.toPx(), tickTemp)
            nc.drawText(Calc.fmt0(tMin), w - mr + 4.dp.toPx(), mt + ih, tickTemp)
            // Stundenmarken
            tick.textAlign = android.graphics.Paint.Align.CENTER
            for (hh in intArrayOf(6, 9, 12, 15, 18, 21)) nc.drawText(hh.toString(), xOf(hh), h - 5.dp.toPx(), tick)
            // Leistung
            val area = Path()
            val line = Path()
            var first = true
            for ((idx, x) in hs.withIndex()) {
                val o = idx
                if (o < lo || o > hi) continue
                val px = xOf(o)
                val py = yP(x.power)
                if (first) { line.moveTo(px, py); area.moveTo(px, yP(0.0)); area.lineTo(px, py); first = false }
                else { line.lineTo(px, py); area.lineTo(px, py) }
            }
            if (!first) {
                area.lineTo(xOf(minOf(hi, hs.size - 1)), yP(0.0))
                area.close()
                drawPath(area, color = Color.White.copy(alpha = if (dark) 0.45f else 0.30f))
                drawPath(line, color = if (dark) Color(0xFF2B1600) else Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            // Temperatur
            val tl = Path()
            var tf = true
            for ((idx, x) in hs.withIndex()) {
                if (idx < lo || idx > hi) continue
                val px = xOf(idx)
                val py = yT(x.temp)
                if (tf) { tl.moveTo(px, py); tf = false } else tl.lineTo(px, py)
            }
            if (!tf) drawPath(tl, color = tempCol, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
            selH?.let { o ->
                if (o in lo..hi && o < hs.size) {
                    val px = xOf(o)
                    drawLine(ink, Offset(px, mt), Offset(px, mt + ih), strokeWidth = 1.5.dp.toPx())
                    drawCircle(ink, 4.dp.toPx(), Offset(px, yP(hs[o].power)))
                }
            }
        }
        val sLo = 4
        val sHi = minOf(22, hs.size - 1)
        if (sHi > sLo) {
            val pk = hs.indexOfFirst { it.hour == d.peakHour }.coerceIn(sLo, sHi)
            androidx.compose.material3.Slider(
                value = (selH ?: pk).toFloat(),
                onValueChange = { selH = Math.round(it).coerceIn(sLo, sHi) },
                valueRange = sLo.toFloat()..sHi.toFloat(),
                steps = sHi - sLo - 1,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = ink, activeTrackColor = ink, inactiveTrackColor = muted.copy(alpha = 0.35f),
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                )
            )
            val sh = selH?.let { hs.getOrNull(it) }
            Text(
                if (sh != null) (sh.hour - 1).toString() + "–" + sh.hour + " Uhr · " + Calc.fmt2(sh.power / 1000.0) + " kW · " + Calc.fmt0(sh.temp) + " °C · Wolken " + Calc.fmt0(sh.cloud) + " %" +
                    (if (sh.precip >= 0.1) " · Regen " + Calc.fmt1(sh.precip) + " mm" else "")
                else "Schieber: einzelne Stunde anzeigen",
                fontSize = 12.sp, color = muted, lineHeight = 16.sp
            )
        }
    }
}

private const val PROFILE_ROWS = 28 // 0 bis 14 km in 500-m-Schritten

private fun profileCloudAt(d: ProfileData, i: Int, hm: Double): Double {
    if (!d.hasLevels) {
        val v = if (hm < 2000.0) d.low[i] else if (hm < 6000.0) d.mid[i] else d.high[i]
        return if (v.isNaN()) 0.0 else v
    }
    val g = d.gh[i]
    val c = d.cc[i]
    val idx = g.indices.filter { !g[it].isNaN() && !c[it].isNaN() }
    if (idx.isEmpty()) return 0.0
    if (hm <= g[idx.first()]) return c[idx.first()]
    if (hm >= g[idx.last()]) return c[idx.last()]
    for (k in 0 until idx.size - 1) {
        val a = idx[k]
        val b = idx[k + 1]
        if (hm <= g[b]) {
            val span = g[b] - g[a]
            val f = if (span <= 0.0) 0.0 else (hm - g[a]) / span
            return c[a] + (c[b] - c[a]) * f
        }
    }
    return c[idx.last()]
}

/** Niederschlag (mm/h, geglättet) und Wolkenprofil 0 bis 14 km in Wetterkarten-Optik: Tagesköpfe, Tag/Nacht-Bänder, seitlich verschiebbar. */
@Composable
fun ProfileCard(d: ProfileData?, failed: Boolean, lat: Double, lon: Double, temps: Map<String, Double> = emptyMap()) {
    SectionCard(
        title = "Niederschlag und Wolken",
        bg = Color(0xFFDC6601), borderColor = Color(0xFFF7C46A), inkColor = Color.White, mutedColor = Color(0xFFFFE9D6)
    ) {
        if (d == null || d.time.isEmpty()) {
            Text(if (failed) "Daten gerade nicht erreichbar." else "Lade Daten …", fontSize = 13.sp, color = LocalMuted.current)
            return@SectionCard
        }
        val n = d.time.size
        var sel by remember(d) { mutableStateOf<Int?>(null) }
        val grid = remember(d) { Array(n) { i -> DoubleArray(PROFILE_ROWS) { r -> profileCloudAt(d, i, (r + 0.5) * 500.0) } } }
        val nowIdx = remember(d) {
            val z = try { ZoneId.of(d.timezone) } catch (e: Exception) { ZoneId.systemDefault() }
            val key = java.time.LocalDateTime.now(z).toString().substring(0, 13)
            d.time.indexOfFirst { it.startsWith(key) }
        }
        val todayStr = remember(d) { if (nowIdx >= 0) d.time[nowIdx].substring(0, 10) else "" }
        // Tag/Nacht: je Datum Sonnenaufgang und -untergang in Spalteneinheiten
        val bands = remember(d, lat, lon) {
            d.time.map { it.substring(0, 10) }.distinct().mapNotNull { dt ->
                val i0 = d.time.indexOfFirst { it.startsWith(dt) }
                fun mins(s: String) = s.substring(0, 2).toInt() * 60f + s.substring(3, 5).toInt()
                Calc.sunTimes(lat, lon, d.timezone, java.time.LocalDate.parse(dt))?.let { (i0 + mins(it.first) / 60f) to (i0 + mins(it.second) / 60f) }
            }
        }
        val rainRaw = d.rain.filter { !it.isNaN() }.maxOrNull() ?: 0.0
        val rainTop = when {
            rainRaw <= 1.0 -> 1.0
            rainRaw <= 2.0 -> 2.0
            rainRaw <= 5.0 -> 5.0
            else -> Math.ceil(rainRaw / 5.0) * 5.0
        }
        val rainStep = if (rainTop <= 5.0) 1.0 else 5.0
        val rainTotal = d.rain.filter { !it.isNaN() }.sum()
        val panel = Color(0xFFC98414)
        val dayBand = Color(0xFFF7C46A)
        val ink = 0xFF2A1800.toInt()
        val lo = Color(0xFF3E77B6)
        val hi = Color(0xFF000000)
        val tempArr = remember(d, temps) { DoubleArray(n) { i -> temps[d.time[i].substring(0, 13)] ?: Double.NaN } }
        val tvAll = tempArr.filter { !it.isNaN() }
        val tLoAll = if (tvAll.isEmpty()) 0.0 else Math.floor(tvAll.min()) - 1
        val tHiAll = if (tvAll.isEmpty()) 1.0 else Math.ceil(tvAll.max()) + 1
        val heatImg = remember(d) {
            val bmp = android.graphics.Bitmap.createBitmap(n, PROFILE_ROWS, android.graphics.Bitmap.Config.ARGB_8888)
            for (i in 0 until n) for (r in 0 until PROFILE_ROWS) {
                val v = (grid[i][r] / 100.0).toFloat().coerceIn(0f, 1f)
                bmp.setPixel(i, PROFILE_ROWS - 1 - r, androidx.compose.ui.graphics.lerp(lo, hi, Math.pow(v.toDouble(), 0.85).toFloat()).toArgb())
            }
            bmp.asImageBitmap()
        }
        val colW = 11.dp
        val hdrH = 44.dp
        val rainH = 140.dp
        val labH = 22.dp
        val gap = 10.dp
        val heatH = 240.dp
        val totalH = hdrH + rainH + labH + gap + heatH + labH
        val scroll = androidx.compose.foundation.rememberScrollState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.LaunchedEffect(d, nowIdx) {
            val start = (maxOf(nowIdx, 0) - 2).coerceAtLeast(0)
            scroll.scrollTo((start * with(density) { colW.toPx() }).toInt())
        }

        Text(
            "Regen in " + (n / 24) + " Tagen insgesamt " + Calc.fmt1(rainTotal) + " mm",
            fontSize = 12.sp, color = LocalMuted.current
        )
        Row(Modifier.fillMaxWidth()) {
            // feste Achsen links, mit gedrehtem Titel
            Canvas(Modifier.size(width = 52.dp, height = totalH)) {
                val white = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF2A1800.toInt(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.RIGHT }
                val title = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF2A1800.toInt(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER }
                val nc = drawContext.canvas.nativeCanvas
                val r0 = hdrH.toPx()
                val rh = rainH.toPx()
                val h0 = r0 + rh + labH.toPx() + gap.toPx()
                val hh = heatH.toPx()
                var v = 0.0
                while (v <= rainTop + 0.001) {
                    val y = r0 + rh - (v / rainTop * rh).toFloat()
                    nc.drawText(Calc.fmt0(v), size.width - 4.dp.toPx(), y + 4.dp.toPx(), white)
                    v += rainStep
                }
                for (km in 0..14 step 2) {
                    nc.drawText("$km", size.width - 4.dp.toPx(), h0 + hh - km * 2 * (hh / PROFILE_ROWS) + 4.dp.toPx(), white)
                }
                nc.save()
                nc.rotate(-90f, 12.dp.toPx(), r0 + rh / 2)
                nc.drawText("Niederschlag (mm/h)", 12.dp.toPx(), r0 + rh / 2 + 4.dp.toPx(), title)
                nc.restore()
                nc.save()
                nc.rotate(-90f, 12.dp.toPx(), h0 + hh / 2)
                nc.drawText("Höhe (km)", 12.dp.toPx(), h0 + hh / 2 + 4.dp.toPx(), title)
                nc.restore()
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(
                    Modifier.size(width = colW * n, height = totalH)
                        .pointerInput(d) {
                            detectTapGestures { o -> sel = (o.x / colW.toPx()).toInt().coerceIn(0, n - 1) }
                        }
                ) {
                    val cw = colW.toPx()
                    val w = cw * n
                    val r0 = hdrH.toPx()
                    val rh = rainH.toPx()
                    val lh = labH.toPx()
                    val h0 = r0 + rh + lh + gap.toPx()
                    val hh = heatH.toPx()
                    val cellH = hh / PROFILE_ROWS
                    val nc = drawContext.canvas.nativeCanvas
                    fun paint(c: Int, sp: Float, bold: Boolean = false) = android.graphics.Paint().apply {
                        isAntiAlias = true; color = c; textSize = sp.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = bold
                    }
                    val tick = paint(ink, 12f)
                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 6f))

                    // Tagesköpfe
                    val dates = d.time.map { it.substring(0, 10) }.distinct()
                    for (dt in dates) {
                        val a = d.time.indexOfFirst { it.startsWith(dt) }
                        val b = d.time.indexOfLast { it.startsWith(dt) } + 1
                        val cx = (a + b) / 2f * cw
                        val col = if (dt == todayStr) 0xFF7A0010.toInt() else ink
                        val dn = java.time.LocalDate.parse(dt).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMAN)
                        nc.drawText(dn, cx, 18.dp.toPx(), paint(col, 15f))
                        nc.drawText(Calc.dm(dt).trimEnd('.'), cx, 36.dp.toPx(), paint(col, 15f))
                    }

                    // Niederschlag-Feld mit Tag/Nacht-Bändern
                    drawRect(panel, Offset(0f, r0), Size(w, rh))
                    for ((s0, s1) in bands) drawRect(dayBand, Offset(s0 * cw, r0), Size((s1 - s0) * cw, rh))
                    var gv = 0.0
                    while (gv <= rainTop + 0.001) {
                        val y = r0 + rh - (gv / rainTop * rh).toFloat()
                        drawLine(Color(0x88FFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = if (gv > 0.0) dash else null)
                        gv += rainStep
                    }
                    val base = r0 + rh
                    val pts = (0 until n).map { i ->
                        val r = d.rain[i].let { if (it.isNaN()) 0.0 else it }
                        Offset((i + 0.5f) * cw, base - (r / rainTop * rh).toFloat().coerceIn(0f, rh))
                    }
                    val line = Path()
                    line.moveTo(0f, pts[0].y)
                    line.lineTo(pts[0].x, pts[0].y)
                    for (i in 1 until n) {
                        val mx = (pts[i - 1].x + pts[i].x) / 2f
                        line.cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                    }
                    line.lineTo(w, pts[n - 1].y)
                    val area = Path()
                    area.addPath(line)
                    area.lineTo(w, base)
                    area.lineTo(0f, base)
                    area.close()
                    drawPath(area, Color(0xE69AD8FF))
                    drawPath(line, Color(0xFF3FA9F5), style = Stroke(width = 1.2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))

                    // Temperaturkurve (geglättet) über dem Regenfeld
                    val tv = tempArr.filter { !it.isNaN() }
                    if (tv.size > 3) {
                        val sm = DoubleArray(n) { i ->
                            var sum = 0.0; var c = 0
                            for (j in maxOf(0, i - 2)..minOf(n - 1, i + 2)) if (!tempArr[j].isNaN()) { sum += tempArr[j]; c++ }
                            if (c > 0) sum / c else Double.NaN
                        }
                        val tLo = tLoAll
                        val tHi = tHiAll
                        fun ty(t: Double) = r0 + rh * 0.92f - ((t - tLo) / (tHi - tLo) * rh * 0.84f).toFloat()
                        val tp = (0 until n).filter { !sm[it].isNaN() }.map { Offset((it + 0.5f) * cw, ty(sm[it])) }
                        val tl = Path()
                        tl.moveTo(tp[0].x, tp[0].y)
                        for (i in 1 until tp.size) {
                            val mx = (tp[i - 1].x + tp[i].x) / 2f
                            tl.cubicTo(mx, tp[i - 1].y, mx, tp[i].y, tp[i].x, tp[i].y)
                        }
                        drawPath(tl, Color(0xFFC8102E), style = Stroke(width = 2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        val tPaint = paint(0xFF8A0A20.toInt(), 11f, true)
                        for (dt in dates) {
                            val ids = (0 until n).filter { d.time[it].startsWith(dt) && !sm[it].isNaN() }
                            if (ids.isEmpty()) continue
                            val iMax = ids.maxByOrNull { sm[it] }!!
                            val iMin = ids.minByOrNull { sm[it] }!!
                            nc.drawText(Calc.fmt0(sm[iMax]) + "°", (iMax + 0.5f) * cw, ty(sm[iMax]) - 5.dp.toPx(), tPaint)
                            if (iMin != iMax) nc.drawText(Calc.fmt0(sm[iMin]) + "°", (iMin + 0.5f) * cw, ty(sm[iMin]) + 14.dp.toPx(), tPaint)
                        }
                    }

                    // Wolken-Wärmebild (weich interpoliert)
                    drawImage(heatImg, dstOffset = androidx.compose.ui.unit.IntOffset(0, h0.toInt()), dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), hh.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                    for (km in 2..14 step 2) {
                        val y = h0 + hh - km * 2 * cellH
                        drawLine(Color(0x33FFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                    }

                    // Achsen: Stundenmarken unter beiden Feldern, Tagesgrenzen
                    for ((axisY, top) in listOf(base to r0, (h0 + hh) to h0)) {
                        drawLine(Color(0xFF2A1800), Offset(0f, axisY), Offset(w, axisY), strokeWidth = 1.dp.toPx())
                        for (i in 0 until n) {
                            val hr = d.time[i].substring(11, 13).toInt()
                            if (hr % 3 == 0) {
                                val x = i * cw
                                drawLine(Color(0xFF2A1800), Offset(x, axisY), Offset(x, axisY + 7.dp.toPx()), strokeWidth = 1.dp.toPx())
                                val lab = when (hr) { 6 -> "06"; 12 -> "12"; 18 -> "18"; 0 -> Calc.weekday(d.time[i].substring(0, 10)); else -> null }
                                if (lab != null) nc.drawText(lab, x, axisY + 19.dp.toPx(), tick)
                            }
                        }
                        for (i in 1 until n) {
                            if (d.time[i].substring(11, 13) == "00") drawLine(Color(0x88000000), Offset(i * cw, top), Offset(i * cw, axisY), strokeWidth = 1.dp.toPx())
                        }
                    }
                    // Jetzt und Auswahl über beide Felder
                    if (nowIdx >= 0) {
                        val x = (nowIdx + 0.5f) * cw
                        val de = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                        drawLine(Color(0xFFE5304A), Offset(x, r0), Offset(x, base), strokeWidth = 2.dp.toPx(), pathEffect = de)
                        drawLine(Color(0xFFE5304A), Offset(x, h0), Offset(x, h0 + hh), strokeWidth = 2.dp.toPx(), pathEffect = de)
                    }
                    sel?.let { s ->
                        val x = (s + 0.5f) * cw
                        drawLine(Color(0xFF1B6BFF), Offset(x, r0), Offset(x, base), strokeWidth = 2.dp.toPx())
                        drawLine(Color(0xFF1B6BFF), Offset(x, h0), Offset(x, h0 + hh), strokeWidth = 2.dp.toPx())
                    }
                }
            }
            if (tvAll.size > 3) Canvas(Modifier.size(width = 34.dp, height = totalH)) {
                val tp = android.graphics.Paint().apply { isAntiAlias = true; color = 0xFF8A0A20.toInt(); textSize = 11.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT; isFakeBoldText = true }
                val nc = drawContext.canvas.nativeCanvas
                val r0 = hdrH.toPx()
                val rh = rainH.toPx()
                val span = tHiAll - tLoAll
                val st = if (span <= 8) 2.0 else if (span <= 20) 5.0 else 10.0
                var t = Math.ceil(tLoAll / st) * st
                while (t <= tHiAll) {
                    val y = r0 + rh * 0.92f - ((t - tLoAll) / span * rh * 0.84f).toFloat()
                    nc.drawText(Calc.fmt0(t) + "°", 4.dp.toPx(), y + 4.dp.toPx(), tp)
                    t += st
                }
                nc.save()
                nc.rotate(90f, 28.dp.toPx(), r0 + rh / 2)
                tp.textAlign = android.graphics.Paint.Align.CENTER
                nc.drawText("Temperatur (°C)", 28.dp.toPx(), r0 + rh / 2 + 4.dp.toPx(), tp)
                nc.restore()
            }
        }
        val s = sel
        if (s != null) {
            val t = d.time[s]
            val r = d.rain[s]
            Text(
                Calc.weekday(t.substring(0, 10)) + " " + Calc.dm(t.substring(0, 10)) + " " + t.substring(11, 13) + " Uhr · " + (temps[t.substring(0, 13)]?.let { Calc.fmt0(it) + " °C · " } ?: "") + "Regen " +
                    (if (r.isNaN()) "–" else Calc.fmt1(r) + " mm/h") + " · Wolken tief " + (if (d.low[s].isNaN()) "–" else Calc.fmt0(d.low[s]) + " %") +
                    ", mittel " + (if (d.mid[s].isNaN()) "–" else Calc.fmt0(d.mid[s]) + " %") +
                    ", hoch " + (if (d.high[s].isNaN()) "–" else Calc.fmt0(d.high[s]) + " %"),
                fontSize = 12.sp, color = LocalInk.current
            )
        } else {
            Text("Nach rechts wischen für die kommenden Tage, antippen für Einzelwerte. Rot gestrichelt: jetzt. Hellere Fläche: Tag. Hellblau: Regen, rote Linie: Temperatur (°C, Tageshoch/-tief beschriftet).", fontSize = 12.sp, color = LocalMuted.current)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wolken klar", fontSize = 11.sp, color = LocalMuted.current)
            Box(Modifier.size(width = 120.dp, height = 10.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(lo, hi))))
            Text("bedeckt", fontSize = 11.sp, color = LocalMuted.current)
        }
        Text(
            if (d.hasLevels) "Wolkenanteil je Höhe aus den Druckflächen-Daten des Wettermodells, Höhen gerechnet. Quelle: Open-Meteo.com (CC BY 4.0)"
            else "Höhendaten gerade nicht verfügbar: vereinfachte Darstellung mit tiefen, mittleren und hohen Wolken. Quelle: Open-Meteo.com (CC BY 4.0)",
            fontSize = 10.5.sp, color = LocalMuted.current, lineHeight = 14.sp
        )
    }
}
