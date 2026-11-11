package de.autoknigge.solarprognose

import androidx.compose.foundation.Canvas
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
