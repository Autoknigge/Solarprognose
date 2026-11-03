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

private val GridColor = Color(0xFF2B5848).copy(alpha = 0.55f)
private val AxisColor = Color(0xFF2B5848)

private class ChartPaints(sizeTick: Float, sizeDay: Float) {
    private fun paint(color: Color, size: Float, align: Paint.Align, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = size
        textAlign = align
        isFakeBoldText = bold
    }

    val tickRight = paint(Muted, sizeTick, Paint.Align.RIGHT)
    val tickCenter = paint(Muted, sizeTick, Paint.Align.CENTER)
    val day = paint(Ink, sizeDay, Paint.Align.CENTER)
    val dayToday = paint(Ink, sizeDay, Paint.Align.CENTER, bold = true)
    val now = paint(Muted, sizeTick, Paint.Align.CENTER)
    val barLabel = paint(Ink, sizeDay, Paint.Align.CENTER, bold = true)
}

/** Stundenkurve der erwarteten Leistung. [count] = 48 (zwei Tage) oder alle Stunden (sieben Tage). */
@Composable
fun HourlyChart(
    f: Forecast,
    count: Int,
    nowC: Double?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paints = remember(density) { ChartPaints(with(density) { 11.sp.toPx() }, with(density) { 12.sp.toPx() }) }
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
                color = if (v == 0.0) AxisColor else GridColor,
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
                    color = AxisColor,
                    start = Offset(xOf(d.start.toDouble()), mt),
                    end = Offset(xOf(d.start.toDouble()), base),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            }
            val cx = xOf((d.start + minOf(d.end, n)) / 2.0)
            val dayPaint = if (k == 0) paints.dayToday else paints.day
            if (two) {
                for (hh in intArrayOf(6, 12, 18)) {
                    if (d.start + hh < n) {
                        nativeCanvas.drawText(hh.toString(), xOf((d.start + hh).toDouble()), base + 15.dp.toPx(), paints.tickCenter)
                    }
                }
                val name = when (k) {
                    0 -> "Heute"
                    1 -> "Morgen"
                    else -> Calc.weekday(d.date)
                }
                nativeCanvas.drawText(name + " · " + Calc.weekday(d.date) + " " + Calc.dm(d.date), cx, base + 36.dp.toPx(), dayPaint)
            } else {
                nativeCanvas.drawText(if (k == 0) "Heute" else Calc.weekday(d.date), cx, base + 17.dp.toPx(), dayPaint)
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
        drawPath(line, color = Gold, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Jetzt-Markierung
        if (nowC != null && nowC > 0 && nowC < n) {
            val x = xOf(nowC)
            drawLine(
                color = Muted,
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
            drawLine(color = Ink.copy(alpha = 0.7f), start = Offset(x, mt), end = Offset(x, base), strokeWidth = 1.dp.toPx())
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paints = remember(density) { ChartPaints(with(density) { 11.sp.toPx() }, with(density) { 12.sp.toPx() }) }
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
                drawPath(path, color = if (selected == null || selected == k) Gold else Gold.copy(alpha = 0.55f))
            }
            nativeCanvas.drawText(Calc.fmt1(d.kwh), cx, minOf(y, base - 2.dp.toPx()) - 6.dp.toPx(), paints.barLabel)
            nativeCanvas.drawText(if (k == 0) "Heute" else Calc.weekday(d.date), cx, base + 17.dp.toPx(), if (k == 0) paints.dayToday else paints.day)
            nativeCanvas.drawText(Calc.dm(d.date), cx, base + 31.dp.toPx(), paints.tickCenter)
        }
    }
}
