package de.autoknigge.solarprognose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
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

/** Tagesprognose, bisher erzeugte Menge (aus dem Wetter berechnet) und erwarteter Rest in einem Feld. */
@Composable
fun TodayCard(total: Double, rest: Double, nowKw: Double? = null, sun: Pair<String, String>? = null, uv: Double? = null, sunTotal: Double? = null, sunRest: Double? = null) {
    val done = (total - rest).coerceAtLeast(0.0)
    val frac = if (total > 0.0) (done / total).toFloat().coerceIn(0f, 1f) else 0f
    SectionCard(title = "Heute im Verlauf", trailing = sun?.let { s -> {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (uv != null) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.padding(end = 10.dp)) {
                    Text("UV", fontSize = 10.sp, color = Muted, lineHeight = 11.sp)
                    Text(Calc.fmt1(uv), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 14.sp)
                }
            }
            Text(s.first + " ⬆️  " + s.second + " ⬇️", fontSize = 13.sp, color = Ink)
        }
    } }) {
        if (nowKw != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Jetzt (Leistung)", Calc.fmt2(nowKw), "kW", TileToday)
                Text("≈ " + Calc.fmt0(nowKw * 1000.0) + " W", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 14.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Prognose heute", Calc.fmt1(total), "kWh")
            Stat("bisher erzeugt", Calc.fmt1(done), "kWh", TodayOrange)
            Stat("noch erwartet", Calc.fmt1(rest), "kWh", GoldSoft)
        }
        if (sunTotal != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Sonnenschein heute", Calc.fmt1(sunTotal), "h")
                if (sunRest != null) Stat("noch erwartet", Calc.fmt1(sunRest), "h", GoldSoft)
            }
        }
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Panel2)) {
            if (frac > 0.001f) Box(Modifier.weight(frac).fillMaxHeight().background(TodayOrange))
            if (frac < 0.999f) Box(Modifier.weight(1f - frac).fillMaxHeight().background(GoldSoft.copy(alpha = 0.35f)))
        }
        Text(
            "„Bisher“ ist aus dem Wetter berechnet, nicht an deiner Anlage gemessen.",
            fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
        )
    }
}

// ---------- Jahresprognose ----------

@Composable
fun YearCard(kwp: Double, data: YearData) {
    val perKwp = data.perKwp
    var sel by remember { mutableStateOf<Int?>(null) }
    val months = perKwp.map { it * kwp }
    val total = months.sum()
    val names = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    val today = LocalDate.now()
    SectionCard(title = "Jahresprognose pro Monat (kWh)") {
        val s = sel
        Text(
            if (s != null && s < months.size) {
                val days = YearMonth.of(today.year, s + 1).lengthOfMonth()
                names[s] + ": " + Calc.fmt0(months[s]) + " kWh\nØ " + Calc.fmt1(months[s] / days) + " kWh pro Tag"
            } else {
                "Etwa " + Calc.fmt0(total) + " kWh pro Jahr\nTippe auf einen Balken für Details."
            },
            fontSize = 12.5.sp, color = Muted, lineHeight = 17.sp
        )
        MonthChart(values = months, currentMonth = today.monthValue - 1, selected = sel, onSelect = { sel = it })
        Text(
            data.label + ", kein Wert für ein einzelnes Jahr. Ohne Verschattung, Schnee und Wechselrichter-Grenze.",
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
                "Netto-Börsenpreis (Day-Ahead, Gebotszone DE-LU) ohne Steuern, Abgaben und Netzentgelte. Dein Stromtarif weicht davon ab.",
            fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp
        )
    }
}


/** Strommix Deutschland (Energy-Charts): Tag/Woche/Jahr, Schieber für einzelne Zeitpunkte, Import und Export. */
@Composable
fun MixCard(range: Int, series: MixSeries?, failed: Boolean, onRange: (Int) -> Unit) {
    val colors = mapOf(
        "Kohle" to Color(0xFFD4714F), "Erdgas" to Color(0xFFA9896B), "Biomasse" to Color(0xFF9DBF5B),
        "Wind" to Color(0xFF2E8B57), "Wasserkraft" to Color(0xFF4B5A9E), "Solar" to Color(0xFFE3B341), "Sonstige" to Color(0xFFA0A0A0),
        "Import" to Color(0xFF5FD0E6), "Export" to Color(0xFFE8607A)
    )
    val zone = remember { ZoneId.of("Europe/Berlin") }
    var sel by remember(series) { mutableStateOf<Int?>(null) }
    SectionCard(
        title = "Strommix Deutschland",
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
            fmt(series.t[s.coerceIn(0, n - 1)], range != 2)
        } else when (range) {
            0 -> "Heute bis " + String.format(Locale.GERMANY, "%02d:%02d Uhr", Instant.ofEpochSecond(lastT + 900).atZone(zone).hour, Instant.ofEpochSecond(lastT + 900).atZone(zone).minute)
            else -> "Durchschnitt $first bis ${fmt(lastT, false)}"
        }
        Text(head + " · Erzeugung " + Calc.fmt1(prod / 1000.0) + " GW", fontSize = 12.sp, color = LocalMuted.current)
        val top = MIX_GROUPS_UI.maxOf { avg(it) / prod * 100.0 }.coerceAtLeast(1.0)
        @Composable fun bar(name: String, label: String) {
            val pct = avg(name) / prod * 100.0
            val w = ((pct / top) * 0.78).toFloat().coerceIn(0.01f, 0.78f)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(0.34f))
                Row(Modifier.weight(0.66f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.weight(w).height(22.dp).background(colors[name] ?: Color.Gray))
                    Text(Calc.fmt1(pct) + " %", fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                    Box(Modifier.weight((1f - w).coerceAtLeast(0.01f)))
                }
            }
        }
        MIX_GROUPS_UI.forEach { bar(it, it) }
        if (series.hasTrade) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A3A3A)))
            bar("Import", "Import")
            bar("Export", "Export")
            val net = (avg("Import") - avg("Export")) / 1000.0
            Text(
                (if (net >= 0) "Netto-Import " else "Netto-Export ") + Calc.fmt1(Math.abs(net)) + " GW",
                fontSize = 12.sp, color = LocalMuted.current
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
                    if (s == null) "Schieber: einzelnen Zeitpunkt wählen" else "Zurück zur Gesamtansicht",
                    fontSize = 12.sp, color = if (s == null) LocalMuted.current else Color(0xFFE3B341),
                    modifier = if (s == null) Modifier else Modifier.clickable { sel = null }
                )
            }
        }
        Text("Nettostromerzeugung der öffentlichen Stromversorgung, Anteile an der Erzeugung. Quelle: Fraunhofer ISE, energy-charts.info (CC BY 4.0)", fontSize = 10.5.sp, color = LocalMuted.current, lineHeight = 14.sp)
    }
}

private val MIX_GROUPS_UI = Extras.MIX_GROUPS

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
        bg = Color.Black, borderColor = Color(0xFF3A3A3A), inkColor = Color(0xFFEDEDED), mutedColor = Color(0xFF9A9A9A), darken = 0f
    ) {
        Text(
            "Deutschland, Stand " + String.format(Locale.GERMANY, "%02d:%02d Uhr", z.hour, z.minute),
            fontSize = 12.sp, color = LocalMuted.current
        )
        listOf(
            Triple("Solarstrom", solar, Color(0xFFE3B341)),
            Triple("Erneuerbare gesamt", ren, Color(0xFF4FB477))
        ).forEach { (label, pct, col) ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Text(label, fontSize = 13.sp, color = LocalInk.current, modifier = Modifier.weight(1f))
                    Text(Calc.fmt1(pct), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = col)
                    Text(" %", fontSize = 13.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 4.dp))
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF262626))) {
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
        Modifier.fillMaxWidth().clip(shape).background(cardBrush(base, 0.35f)).border(1.dp, base.copy(alpha = 0.6f), shape).padding(14.dp),
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
            "Spitze " + Calc.fmt0(d.peak) + " W gegen " + d.peakHour + " Uhr" +
                (d.sunH?.let { " · Sonne " + Calc.fmt1(it) + " h" } ?: "") +
                (d.cloudAvg?.let { " · Wolken " + Calc.fmt0(it) + " %" } ?: "") +
                (if (d.rainMm >= 0.1) " · Regen " + Calc.fmt1(d.rainMm) + " mm" else "") +
                (if (tLo != null && tHi != null) " · " + Calc.fmt0(tLo) + " bis " + Calc.fmt0(tHi) + " °C" else ""),
            fontSize = 12.sp, color = muted, lineHeight = 16.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("▇ Leistung (W, links)", fontSize = 11.sp, color = muted)
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
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            val ml = 40.dp.toPx()
            val mr = 30.dp.toPx()
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
                nc.drawText(Calc.fmt0(gv), ml - 4.dp.toPx(), yP(gv) + 4.dp.toPx(), tick)
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

/** Niederschlag (Balken) und Wolkenprofil von 0 bis 14 km (Wärmebild) auf gemeinsamer Zeitachse. */
@Composable
fun ProfileCard(d: ProfileData?, failed: Boolean) {
    SectionCard(
        title = "Niederschlag und Wolken",
        bg = Color.Black, borderColor = Color(0xFF3A3A3A), inkColor = Color(0xFFEDEDED), mutedColor = Color(0xFF9A9A9A), darken = 0f
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
        val rainMax = maxOf(2.0, d.rain.filter { !it.isNaN() }.maxOrNull() ?: 0.0)
        val rainTotal = d.rain.filter { !it.isNaN() }.sum()
        val rainColor = Color(0xFF5FB0E6)
        val lo = Color(0xFF101C2A)
        val hi = Color(0xFFF4F8FB)
        val gold = Color(0xFFE3B341)

        Text(
            "Regen je Stunde (blau, bis " + Calc.fmt0(rainMax) + " mm) · in 5 Tagen insgesamt " + Calc.fmt1(rainTotal) + " mm",
            fontSize = 12.sp, color = LocalMuted.current
        )
        Canvas(
            Modifier.fillMaxWidth().height(290.dp)
                .pointerInput(d) {
                    detectTapGestures { o ->
                        val lm = 34.dp.toPx()
                        val w = size.width - lm - 2.dp.toPx()
                        sel = ((o.x - lm) / (w / n)).toInt().coerceIn(0, n - 1)
                    }
                }
                .pointerInput(d) {
                    detectHorizontalDragGestures { ch, _ ->
                        val lm = 34.dp.toPx()
                        val w = size.width - lm - 2.dp.toPx()
                        sel = ((ch.position.x - lm) / (w / n)).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val lm = 34.dp.toPx()
            val w = size.width - lm - 2.dp.toPx()
            val colW = w / n
            val rainH = 56.dp.toPx()
            val heatTop = rainH + 10.dp.toPx()
            val heatH = 200.dp.toPx()
            val axisY = heatTop + heatH
            val cellH = heatH / PROFILE_ROWS
            val txt = android.graphics.Paint().apply {
                isAntiAlias = true
                color = 0xFF9A9A9A.toInt()
                textSize = 10.sp.toPx()
            }
            // Regen
            drawLine(Color(0xFF3A3A3A), Offset(lm, rainH), Offset(lm + w, rainH), strokeWidth = 1.dp.toPx())
            for (i in 0 until n) {
                val r = d.rain[i]
                if (r.isNaN() || r <= 0.0) continue
                val bh = (r / rainMax).toFloat().coerceIn(0f, 1f) * rainH
                drawRect(rainColor, Offset(lm + i * colW, rainH - bh), Size(maxOf(colW - 0.6f, 1f), bh))
            }
            // Wolken-Wärmebild
            for (i in 0 until n) {
                for (r in 0 until PROFILE_ROWS) {
                    val v = (grid[i][r] / 100.0).toFloat().coerceIn(0f, 1f)
                    val col = androidx.compose.ui.graphics.lerp(lo, hi, Math.pow(v.toDouble(), 0.85).toFloat())
                    drawRect(col, Offset(lm + i * colW, axisY - (r + 1) * cellH), Size(colW + 0.6f, cellH + 0.6f))
                }
            }
            // Höhenachse
            txt.textAlign = android.graphics.Paint.Align.RIGHT
            for (km in 0..14 step 2) {
                val y = axisY - km * 2 * cellH
                drawLine(Color(0x55FFFFFF), Offset(lm - 3.dp.toPx(), y), Offset(lm, y), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("$km km", lm - 5.dp.toPx(), y + 3.5.dp.toPx(), txt)
            }
            // Tagesgrenzen und Beschriftung
            txt.textAlign = android.graphics.Paint.Align.CENTER
            val starts = (0 until n).filter { d.time[it].substring(11, 13) == "00" }
            val bounds = (if (starts.firstOrNull() != 0) listOf(0) else emptyList()) + starts + n
            for (k in 0 until bounds.size - 1) {
                val a = bounds[k]
                val b = bounds[k + 1]
                if (a > 0) {
                    val x = lm + a * colW
                    drawLine(Color(0x66FFFFFF), Offset(x, 0f), Offset(x, axisY), strokeWidth = 1.dp.toPx())
                }
                if (b - a >= 6) {
                    val dt = d.time[a].substring(0, 10)
                    drawContext.canvas.nativeCanvas.drawText(Calc.weekday(dt) + " " + Calc.dm(dt), lm + (a + b) / 2f * colW, axisY + 15.dp.toPx(), txt)
                }
            }
            // Jetzt
            if (nowIdx >= 0) {
                val x = lm + (nowIdx + 0.5f) * colW
                drawLine(gold, Offset(x, 0f), Offset(x, axisY), strokeWidth = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
            // Auswahl
            sel?.let { s ->
                val x = lm + (s + 0.5f) * colW
                drawLine(Color.White, Offset(x, 0f), Offset(x, axisY), strokeWidth = 1.5.dp.toPx())
            }
        }
        val s = sel
        if (s != null) {
            val t = d.time[s]
            val r = d.rain[s]
            Text(
                Calc.weekday(t.substring(0, 10)) + " " + Calc.dm(t.substring(0, 10)) + " " + t.substring(11, 13) + " Uhr · Regen " +
                    (if (r.isNaN()) "–" else Calc.fmt1(r) + " mm") + " · Wolken tief " + (if (d.low[s].isNaN()) "–" else Calc.fmt0(d.low[s]) + " %") +
                    ", mittel " + (if (d.mid[s].isNaN()) "–" else Calc.fmt0(d.mid[s]) + " %") +
                    ", hoch " + (if (d.high[s].isNaN()) "–" else Calc.fmt0(d.high[s]) + " %"),
                fontSize = 12.sp, color = LocalInk.current
            )
        } else {
            Text("Antippen oder wischen für Einzelwerte. Gold gestrichelt: jetzt.", fontSize = 12.sp, color = LocalMuted.current)
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("klar", fontSize = 11.sp, color = LocalMuted.current)
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
