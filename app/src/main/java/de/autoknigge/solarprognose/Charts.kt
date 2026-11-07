package de.autoknigge.solarprognose

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val GridColor = Color(0xFF2B5848).copy(alpha = 0.55f)
private val AxisColor = Color(0xFF2B5848)

/** Tagesname relativ zu heute. */
fun dayName(rel: Int, date: String): String = when (rel) {
    -2 -> "Vorgestern"
    -1 -> "Gestern"
    0 -> "Heute"
    1 -> "Morgen"
    else -> Calc.weekday(date)
}

private class ChartPaints(sizeTick: Float, sizeDay: Float, ink: Color = Ink, muted: Color = Muted) {
    private fun paint(color: Color, size: Float, align: Paint.Align, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = size
        textAlign = align
        isFakeBoldText = bold
    }

    val tickRight = paint(muted, sizeTick, Paint.Align.RIGHT)
    val tickCenter = paint(muted, sizeTick, Paint.Align.CENTER)
    val tickLeft = paint(muted, sizeTick, Paint.Align.LEFT)
    val day = paint(ink, sizeDay, Paint.Align.CENTER)
    val dayToday = paint(ink, sizeDay, Paint.Align.CENTER, bold = true)
    val now = paint(muted, sizeTick, Paint.Align.CENTER)
    val barLabel = paint(ink, sizeDay, Paint.Align.CENTER, bold = true)
}

/** Stundenkurve der erwarteten Leistung. [count] = 48 (zwei Tage) oder alle Stunden (sieben Tage). */
@Composable
fun HourlyChart(
    f: Forecast,
    count: Int,
    nowC: Double?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Ink,
    muted: Color = Muted,
    grid: Color = GridColor,
    axis: Color = AxisColor,
    lineColor: Color = Color(0xFFFFF3C4)
) {
    val density = LocalDensity.current
    val paints = remember(density, ink, muted) { ChartPaints(with(density) { 11.sp.toPx() }, with(density) { 12.sp.toPx() }, ink, muted) }
    val n = minOf(count, f.hours.size)
    val two = n <= 48

    fun indexAt(x: Float, width: Float, ml: Float, mr: Float): Int {
        val iw = width - ml - mr
        val c = (x - ml) / iw * n
        return Math.round(c + 0.5).toInt().coerceIn(1, n - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(n) {
                detectTapGestures { off ->
                    onSelect(indexAt(off.x, size.width.toFloat(), 44.dp.toPx(), 8.dp.toPx()))
                }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat(), 44.dp.toPx(), 8.dp.toPx()))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val ml = 44.dp.toPx()
        val mr = 8.dp.toPx()
        val mt = 12.dp.toPx()
        val mb = (if (two) 46 else 42).dp.toPx()
        val iw = w - ml - mr
        val ih = h - mt - mb
        val nativeCanvas = drawContext.canvas.nativeCanvas

        var maxP = 0.0
        for (i in 1 until n) maxP = maxOf(maxP, f.hours[i].power)
        val (step, top) = Calc.niceScale(maxP, doubleArrayOf(100.0, 200.0, 250.0, 500.0, 1000.0, 2000.0, 2500.0), 5)

        fun xOf(c: Double): Float = ml + (c / n * iw).toFloat()
        fun yOf(v: Double): Float = mt + ih - (v / top * ih).toFloat()

        // Raster und Beschriftung der Leistungsachse
        var v = 0.0
        while (v <= top + 0.001) {
            drawLine(
                color = if (v == 0.0) axis else grid,
                start = Offset(ml, yOf(v)),
                end = Offset(w - mr, yOf(v)),
                strokeWidth = 1.dp.toPx()
            )
            nativeCanvas.drawText(Calc.fmt0(v), ml - 6.dp.toPx(), yOf(v) + 4.dp.toPx(), paints.tickRight)
            v += step
        }

        // Tage: Trennlinien und Beschriftung
        val base = mt + ih
        f.days.filter { it.start < n }.forEachIndexed { k, d ->
            if (d.start > 0) {
                drawLine(
                    color = axis,
                    start = Offset(xOf(d.start.toDouble()), mt),
                    end = Offset(xOf(d.start.toDouble()), base),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            }
            val cx = xOf((d.start + minOf(d.end, n)) / 2.0)
            val dayPaint = if (k == f.todayIndex) paints.dayToday else paints.day
            if (two) {
                for (hh in intArrayOf(6, 12, 18)) {
                    if (d.start + hh < n) {
                        nativeCanvas.drawText(hh.toString(), xOf((d.start + hh).toDouble()), base + 15.dp.toPx(), paints.tickCenter)
                    }
                }
                val name = dayName(k - f.todayIndex, d.date)
                nativeCanvas.drawText(name + " · " + Calc.weekday(d.date) + " " + Calc.dm(d.date), cx, base + 36.dp.toPx(), dayPaint)
            } else {
                nativeCanvas.drawText(if (k == f.todayIndex) "Heute" else Calc.weekday(d.date), cx, base + 17.dp.toPx(), dayPaint)
                nativeCanvas.drawText(Calc.dm(d.date), cx, base + 31.dp.toPx(), paints.tickCenter)
            }
        }

        // Kurve und Fläche
        val line = Path()
        val area = Path()
        for (i in 1 until n) {
            val x = xOf(i - 0.5)
            val y = yOf(f.hours[i].power)
            if (i == 1) {
                line.moveTo(x, y)
                area.moveTo(x, yOf(0.0))
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(xOf(n - 1.5), yOf(0.0))
        area.close()
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(Gold.copy(alpha = 0.55f), Gold.copy(alpha = 0.04f)),
                startY = mt,
                endY = base
            )
        )
        drawPath(line, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Jetzt-Markierung
        if (nowC != null && nowC > 0 && nowC < n) {
            val x = xOf(nowC)
            drawLine(
                color = muted,
                start = Offset(x, mt),
                end = Offset(x, base),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
            )
            nativeCanvas.drawText("jetzt", x, mt - 2.dp.toPx(), paints.now)
        }

        // Auswahl
        if (selected != null && selected in 1 until n) {
            val x = xOf(selected - 0.5)
            val y = yOf(f.hours[selected].power)
            drawLine(color = ink.copy(alpha = 0.7f), start = Offset(x, mt), end = Offset(x, base), strokeWidth = 1.dp.toPx())
            drawCircle(color = Forest, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(color = GoldSoft, radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** Balken mit dem Tagesertrag in kWh. */
@Composable
fun DailyChart(
    f: Forecast,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Ink,
    muted: Color = Muted
) {
    val density = LocalDensity.current
    val paints = remember(density, ink, muted) { ChartPaints(with(density) { 11.sp.toPx() }, with(density) { 12.sp.toPx() }, ink, muted) }
    val days = f.days

    fun dayAt(x: Float, width: Float, m: Float): Int {
        val band = (width - 2 * m) / days.size
        return ((x - m) / band).toInt().coerceIn(0, days.size - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(210.dp)
            .pointerInput(days.size) {
                detectTapGestures { off -> onSelect(dayAt(off.x, size.width.toFloat(), 8.dp.toPx())) }
            }
            .pointerInput(days.size) {
                detectHorizontalDragGestures { change, _ -> onSelect(dayAt(change.position.x, size.width.toFloat(), 8.dp.toPx())) }
            }
    ) {
        val w = size.width
        val h = size.height
        val m = 8.dp.toPx()
        val mt = 26.dp.toPx()
        val mb = 42.dp.toPx()
        val ih = h - mt - mb
        val band = (w - 2 * m) / days.size
        val bw = minOf(46.dp.toPx(), band * 0.62f)
        val base = mt + ih
        val maxK = maxOf(1.0, days.maxOfOrNull { it.kwh } ?: 1.0)
        val nativeCanvas = drawContext.canvas.nativeCanvas

        drawLine(color = AxisColor, start = Offset(m, base), end = Offset(w - m, base), strokeWidth = 1.dp.toPx())

        days.forEachIndexed { k, d ->
            val cx = m + band * (k + 0.5f)
            val x = cx - bw / 2
            val y = base - (d.kwh / (maxK * 1.12) * ih).toFloat()
            val bh = base - y
            if (bh > 0.5f) {
                val r = minOf(4.dp.toPx(), bh / 2)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = y, right = x + bw, bottom = base,
                            topLeftCornerRadius = CornerRadius(r),
                            topRightCornerRadius = CornerRadius(r),
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                val ti = f.todayIndex
                val barColor = when {
                    k == ti -> TodayOrange
                    k < ti -> TileTomorrow
                    else -> {
                        val cnt = days.size - ti - 1
                        val t = if (cnt <= 1) 1f else (k - ti - 1).toFloat() / (cnt - 1)
                        androidx.compose.ui.graphics.lerp(GoldDark, GoldLight, t)
                    }
                }
                drawPath(path, color = if (selected == null || selected == k) barColor else barColor.copy(alpha = 0.55f))
            }
            nativeCanvas.drawText(Calc.fmt1(d.kwh), cx, minOf(y, base - 2.dp.toPx()) - 6.dp.toPx(), paints.barLabel)
            nativeCanvas.drawText(if (k == f.todayIndex) "Heute" else Calc.weekday(d.date), cx, base + 17.dp.toPx(), if (k == f.todayIndex) paints.dayToday else paints.day)
            nativeCanvas.drawText(Calc.dm(d.date), cx, base + 31.dp.toPx(), paints.tickCenter)
        }
    }
}


private val MONTHS = arrayOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

/** Senkrechtes Balkendiagramm der Monatserträge. Der aktuelle Monat ist orange, die anderen laufen von dunklem zu hellem Gold. */
@Composable
fun MonthChart(
    values: List<Double>,
    currentMonth: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paints = remember(density) { ChartPaints(with(density) { 10.sp.toPx() }, with(density) { 10.5.sp.toPx() }) }
    val n = values.size

    fun monthAt(x: Float, width: Float, m: Float): Int {
        val band = (width - 2 * m) / n
        return ((x - m) / band).toInt().coerceIn(0, n - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(n) {
                detectTapGestures { off -> onSelect(monthAt(off.x, size.width.toFloat(), 4.dp.toPx())) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ -> onSelect(monthAt(change.position.x, size.width.toFloat(), 4.dp.toPx())) }
            }
    ) {
        val w = size.width
        val h = size.height
        val m = 4.dp.toPx()
        val mt = 22.dp.toPx()
        val mb = 22.dp.toPx()
        val ih = h - mt - mb
        val band = (w - 2 * m) / n
        val bw = band * 0.72f
        val base = mt + ih
        val maxV = maxOf(1.0, values.maxOrNull() ?: 1.0)
        val nativeCanvas = drawContext.canvas.nativeCanvas

        drawLine(color = AxisColor, start = Offset(m, base), end = Offset(w - m, base), strokeWidth = 1.dp.toPx())

        values.forEachIndexed { k, v ->
            val cx = m + band * (k + 0.5f)
            val x = cx - bw / 2
            val y = base - (v / (maxV * 1.12) * ih).toFloat()
            val bh = base - y
            val c0 = if (k == currentMonth) TodayOrange else androidx.compose.ui.graphics.lerp(GoldDark, GoldLight, k / (n - 1f))
            val c = if (selected == null || selected == k) c0 else c0.copy(alpha = 0.55f)
            if (bh > 0.5f) {
                val r = minOf(3.dp.toPx(), bh / 2)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = y, right = x + bw, bottom = base,
                            topLeftCornerRadius = CornerRadius(r),
                            topRightCornerRadius = CornerRadius(r),
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, color = c)
            }
            nativeCanvas.drawText(String.format(Locale.GERMANY, "%.0f", v), cx, minOf(y, base - 2.dp.toPx()) - 5.dp.toPx(), paints.barLabel)
            nativeCanvas.drawText(MONTHS[k], cx, base + 15.dp.toPx(), if (k == currentMonth) paints.dayToday else paints.tickCenter)
        }
    }
}

/** Börsenpreis je Stunde (ct/kWh) für heute und morgen. Günstig grün, teuer orange, die aktuelle Stunde mit Rahmen. */
@Composable
fun PriceChart(
    prices: List<PriceHour>,
    nowSec: Long,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paints = remember(density) { ChartPaints(with(density) { 10.sp.toPx() }, with(density) { 11.sp.toPx() }) }
    val zone = remember { ZoneId.of("Europe/Berlin") }
    val n = prices.size

    fun indexAt(x: Float, width: Float, ml: Float, mr: Float): Int {
        val band = (width - ml - mr) / n
        return ((x - ml) / band).toInt().coerceIn(0, n - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(210.dp)
            .pointerInput(n) {
                detectTapGestures { off -> onSelect(indexAt(off.x, size.width.toFloat(), 34.dp.toPx(), 6.dp.toPx())) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat(), 34.dp.toPx(), 6.dp.toPx()))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val ml = 34.dp.toPx()
        val mr = 6.dp.toPx()
        val mt = 10.dp.toPx()
        val mb = 36.dp.toPx()
        val iw = w - ml - mr
        val ih = h - mt - mb
        val band = iw / n
        val bw = band * 0.8f
        val gap = (band - bw) / 2
        val nativeCanvas = drawContext.canvas.nativeCanvas

        val vals = prices.map { it.ct }
        val lo = vals.minOrNull() ?: 0.0
        val hi = vals.maxOrNull() ?: 1.0
        val maxV = maxOf(1.0, hi)
        val minV = minOf(0.0, lo)
        var step = 1.0
        for (s in doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0)) {
            step = s
            if ((maxV - minV) / s <= 5) break
        }
        val top = Math.ceil(maxV / step) * step
        val bot = Math.floor(minV / step) * step
        fun yOf(v: Double): Float = mt + ((top - v) / (top - bot) * ih).toFloat()
        val base = h - mb

        var g = bot
        while (g <= top + 0.001) {
            drawLine(
                color = if (Math.abs(g) < 1e-9) AxisColor else GridColor,
                start = Offset(ml, yOf(g)),
                end = Offset(w - mr, yOf(g)),
                strokeWidth = 1.dp.toPx()
            )
            nativeCanvas.drawText(Calc.fmt0(g), ml - 5.dp.toPx(), yOf(g) + 4.dp.toPx(), paints.tickRight)
            g += step
        }

        val span = maxOf(hi - lo, 0.001)
        val zeroY = yOf(0.0)
        prices.forEachIndexed { i, p ->
            val x = ml + band * i + gap
            val y = yOf(p.ct)
            val t = ((p.ct - lo) / span).toFloat()
            val c0 = androidx.compose.ui.graphics.lerp(Color(0xFF6FCF97), TodayOrange, t)
            val c = if (selected == null || selected == i) c0 else c0.copy(alpha = 0.55f)
            val topY = minOf(y, zeroY)
            val hh = maxOf(Math.abs(zeroY - y), 1f)
            drawRect(color = c, topLeft = Offset(x, topY), size = Size(bw, hh))
            if (nowSec >= p.start && nowSec < p.start + 3600) {
                drawRect(
                    color = Ink,
                    topLeft = Offset(x - 1f, topY - 1f),
                    size = Size(bw + 2f, hh + 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            val z = Instant.ofEpochSecond(p.start).atZone(zone)
            if (z.hour % 6 == 0) nativeCanvas.drawText(z.hour.toString(), x + bw / 2, base + 14.dp.toPx(), paints.tickCenter)
            if (z.hour == 0 && i > 0) {
                drawLine(
                    color = AxisColor,
                    start = Offset(x - gap, mt),
                    end = Offset(x - gap, base),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            }
        }

        val today = LocalDate.now(zone)
        fun dateAt(i: Int): LocalDate = Instant.ofEpochSecond(prices[i].start).atZone(zone).toLocalDate()
        var i = 0
        while (i < n) {
            val d = dateAt(i)
            var j = i
            while (j < n && dateAt(j) == d) j++
            val cx = ml + band * (i + j) / 2f
            val label = when (d) {
                today -> "Heute"
                today.plusDays(1) -> "Morgen"
                else -> Calc.weekday(d.toString()) + " " + Calc.dm(d.toString())
            }
            nativeCanvas.drawText(label, cx, base + 30.dp.toPx(), if (d == today) paints.dayToday else paints.day)
            i = j
        }
    }
}


/** Temperatur (Linie, linke Achse) und Bewölkung (Fläche, rechte Achse) pro Stunde. Gleiche Zeitachse wie [HourlyChart]. */
@Composable
fun WeatherChart(
    f: Forecast,
    count: Int,
    nowC: Double?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Ink,
    muted: Color = Muted,
    grid: Color = GridColor,
    axis: Color = AxisColor,
    tempColor: Color = TempColor,
    cloudColor: Color = CloudColor
) {
    val density = LocalDensity.current
    val paints = remember(density, ink, muted) { ChartPaints(with(density) { 11.sp.toPx() }, with(density) { 12.sp.toPx() }, ink, muted) }
    val n = minOf(count, f.hours.size)
    val two = n <= 48

    fun indexAt(x: Float, width: Float, ml: Float, mr: Float): Int {
        val iw = width - ml - mr
        val c = (x - ml) / iw * n
        return Math.round(c + 0.5).toInt().coerceIn(1, maxOf(1, n - 1))
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(230.dp)
            .pointerInput(n) {
                detectTapGestures { off -> onSelect(indexAt(off.x, size.width.toFloat(), 40.dp.toPx(), 40.dp.toPx())) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    onSelect(indexAt(change.position.x, size.width.toFloat(), 40.dp.toPx(), 40.dp.toPx()))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val ml = 40.dp.toPx()
        val mr = 40.dp.toPx()
        val mt = 12.dp.toPx()
        val mb = (if (two) 46 else 42).dp.toPx()
        val iw = w - ml - mr
        val ih = h - mt - mb
        val base = mt + ih
        val nativeCanvas = drawContext.canvas.nativeCanvas

        var tMin = Double.MAX_VALUE
        var tMax = -Double.MAX_VALUE
        for (i in 1 until n) {
            tMin = minOf(tMin, f.hours[i].temp)
            tMax = maxOf(tMax, f.hours[i].temp)
        }
        if (tMin > tMax) {
            tMin = 0.0
            tMax = 1.0
        }
        var step = 1.0
        for (s in doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0)) {
            step = s
            if ((tMax - tMin) / s <= 5) break
        }
        val lo = Math.floor(tMin / step) * step
        var hi = Math.ceil(tMax / step) * step
        if (hi <= lo) hi = lo + step

        fun xOf(c: Double): Float = ml + (c / n * iw).toFloat()
        fun yT(v: Double): Float = mt + ih - ((v - lo) / (hi - lo) * ih).toFloat()
        fun yC(v: Double): Float = mt + ih - (v.coerceIn(0.0, 100.0) / 100.0 * ih).toFloat()

        // Raster und linke Achse (Temperatur)
        var g = lo
        while (g <= hi + 0.001) {
            drawLine(
                color = if (Math.abs(g) < 1e-9) axis else grid,
                start = Offset(ml, yT(g)),
                end = Offset(w - mr, yT(g)),
                strokeWidth = 1.dp.toPx()
            )
            nativeCanvas.drawText(Calc.fmt0(g), ml - 6.dp.toPx(), yT(g) + 4.dp.toPx(), paints.tickRight)
            g += step
        }
        // rechte Achse (Bewölkung)
        for (p in intArrayOf(0, 50, 100)) {
            nativeCanvas.drawText("$p %", w - mr + 6.dp.toPx(), yC(p.toDouble()) + 4.dp.toPx(), paints.tickLeft)
        }

        // Tage: Trennlinien und Beschriftung
        f.days.filter { it.start < n }.forEachIndexed { k, d ->
            if (d.start > 0) {
                drawLine(
                    color = axis,
                    start = Offset(xOf(d.start.toDouble()), mt),
                    end = Offset(xOf(d.start.toDouble()), base),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            }
            val cx = xOf((d.start + minOf(d.end, n)) / 2.0)
            val dayPaint = if (k == f.todayIndex) paints.dayToday else paints.day
            if (two) {
                for (hh in intArrayOf(6, 12, 18)) {
                    if (d.start + hh < n) {
                        nativeCanvas.drawText(hh.toString(), xOf((d.start + hh).toDouble()), base + 15.dp.toPx(), paints.tickCenter)
                    }
                }
                val name = dayName(k - f.todayIndex, d.date)
                nativeCanvas.drawText(name + " · " + Calc.weekday(d.date) + " " + Calc.dm(d.date), cx, base + 36.dp.toPx(), dayPaint)
            } else {
                nativeCanvas.drawText(if (k == f.todayIndex) "Heute" else Calc.weekday(d.date), cx, base + 17.dp.toPx(), dayPaint)
                nativeCanvas.drawText(Calc.dm(d.date), cx, base + 31.dp.toPx(), paints.tickCenter)
            }
        }

        // Bewölkung als Fläche mit Linie
        val cLine = Path()
        val cArea = Path()
        for (i in 1 until n) {
            val x = xOf(i - 0.5)
            val y = yC(f.hours[i].cloud)
            if (i == 1) {
                cLine.moveTo(x, y)
                cArea.moveTo(x, base)
                cArea.lineTo(x, y)
            } else {
                cLine.lineTo(x, y)
                cArea.lineTo(x, y)
            }
        }
        cArea.lineTo(xOf(n - 1 - 0.5), base)
        cArea.close()
        drawPath(cArea, color = cloudColor.copy(alpha = 0.22f))
        drawPath(cLine, color = cloudColor, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Temperatur als Linie
        val tLine = Path()
        for (i in 1 until n) {
            val x = xOf(i - 0.5)
            val y = yT(f.hours[i].temp)
            if (i == 1) tLine.moveTo(x, y) else tLine.lineTo(x, y)
        }
        drawPath(tLine, color = tempColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Jetzt-Markierung
        if (nowC != null && nowC > 0 && nowC < n) {
            val x = xOf(nowC)
            drawLine(
                color = muted,
                start = Offset(x, mt),
                end = Offset(x, base),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
            )
            nativeCanvas.drawText("jetzt", x, mt - 2.dp.toPx(), paints.now)
        }

        // Auswahl
        if (selected != null && selected in 1 until n) {
            val x = xOf(selected - 0.5)
            drawLine(color = ink.copy(alpha = 0.7f), start = Offset(x, mt), end = Offset(x, base), strokeWidth = 1.dp.toPx())
            drawCircle(color = Forest, radius = 6.dp.toPx(), center = Offset(x, yT(f.hours[selected].temp)))
            drawCircle(color = tempColor, radius = 4.dp.toPx(), center = Offset(x, yT(f.hours[selected].temp)))
            drawCircle(color = Forest, radius = 6.dp.toPx(), center = Offset(x, yC(f.hours[selected].cloud)))
            drawCircle(color = cloudColor, radius = 4.dp.toPx(), center = Offset(x, yC(f.hours[selected].cloud)))
        }
    }
}
