package de.autoknigge.solarprognose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneOffset

enum class BadgeKind { SUN, WIND, TANK }

/** 0 dunkelrot, 1 hellrot, 2 orange, 3 gold, 4 hellgrün, 5 dunkelgrün, sonst grau. */
fun badgeColor(state: Int): Color = when (state) {
    0 -> Color(0xFF9B1C1C)
    1 -> Color(0xFFEF4B43)
    2 -> Color(0xFFF57C00)
    3 -> Color(0xFFFFC21A)
    4 -> Color(0xFF8BD04F)
    5 -> Color(0xFF1B9A52)
    else -> Color(0xFF8A8A8A)
}

/** Stufe 0..5 aus Prozent und fünf Grenzen (aufsteigend). */
fun badgeStep(util: Double, cuts: List<Double>): Int = cuts.count { util >= it }

/**
 * Runde Anzeige mit Rand und Symbol in der Zustandsfarbe. Das Symbol (Sonne, Windrad-Mast, Speicher) füllt sich von unten
 * wie ein Barometer bis [frac] (0..1). Zeichnung in einem 100×100-Raster.
 */
@Composable
fun GaugeBadge(kind: BadgeKind, frac: Float, state: Int, label: String, caption: String? = null, onClick: () -> Unit) {
    val col = badgeColor(state)
    val f = frac.coerceIn(0f, 1f)
    val pump = ImageBitmap.imageResource(R.drawable.ic_pump)
    androidx.compose.foundation.layout.Column(Modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.minDimension / 100f
            fun o(x: Float, y: Float) = Offset(x * s, y * s)
            // Rand, weißer Innenring, dunkle Scheibe
            drawCircle(col, radius = 49f * s, center = o(50f, 50f))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 48.5f * s, center = o(50f, 50f), style = Stroke(1.4f * s))
            drawCircle(Color.White, radius = 38f * s, center = o(50f, 50f))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF454545), Color(0xFF050505)), center = o(40f, 34f), radius = 70f * s),
                radius = 36f * s, center = o(50f, 50f)
            )
            val dim = col.copy(alpha = 0.35f)
            withTransform({ translate(0f, -10f * s); scale(0.66f, 0.66f, pivot = o(50f, 50f)) }) {
            when (kind) {
                BadgeKind.SUN -> {
                    fun sun(c: Color) {
                        drawCircle(c, radius = 11f * s, center = o(50f, 50f))
                        for (k in 0 until 8) {
                            val a = Math.toRadians(k * 45.0)
                            val cx = Math.cos(a).toFloat()
                            val cy = Math.sin(a).toFloat()
                            drawLine(c, o(50f + cx * 17f, 50f + cy * 17f), o(50f + cx * 27f, 50f + cy * 27f), strokeWidth = 5f * s, cap = StrokeCap.Round)
                        }
                    }
                    sun(dim)
                    clipRect(0f, (77f - f * 54f) * s, size.width, size.height) { sun(col) }
                }
                BadgeKind.WIND -> {
                    val mast = Path().apply {
                        moveTo(47.5f * s, 36f * s); lineTo(52.5f * s, 36f * s); lineTo(55f * s, 80f * s); lineTo(45f * s, 80f * s); close()
                    }
                    drawPath(mast, dim)
                    clipRect(0f, (80f - f * 44f) * s, size.width, size.height) { drawPath(mast, col) }
                    drawRect(col, o(38f, 80f), Size(24f * s, 3.5f * s))
                    for (k in 0 until 3) {
                        val a = Math.toRadians(270.0 + k * 120.0)
                        drawLine(col, o(50f, 33f), o(50f + Math.cos(a).toFloat() * 25f, 33f + Math.sin(a).toFloat() * 25f), strokeWidth = 4.5f * s, cap = StrokeCap.Round)
                    }
                    drawCircle(Color.White, radius = 4f * s, center = o(50f, 33f))
                    drawCircle(col, radius = 2.4f * s, center = o(50f, 33f))
                }
                BadgeKind.TANK -> {
                    val dw = (39f * s).toInt()
                    val dh = (52f * s).toInt()
                    val off = androidx.compose.ui.unit.IntOffset((30.5f * s).toInt(), (24f * s).toInt())
                    val sz = androidx.compose.ui.unit.IntSize(dw, dh)
                    val tint = androidx.compose.ui.graphics.ColorFilter.tint(col)
                    drawImage(pump, dstOffset = off, dstSize = sz, alpha = 0.35f, colorFilter = tint)
                    clipRect(0f, (76f - f * 52f) * s, size.width, size.height) { drawImage(pump, dstOffset = off, dstSize = sz, colorFilter = tint) }
                }
            }
            }
        }
        Text(
            label, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 0f), 6f)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp)
        )
    }
    if (caption != null) Text(caption, fontSize = 8.sp, color = androidx.compose.ui.graphics.Color(0xFFB8B8B8), lineHeight = 9.sp, maxLines = 1)
    }
}

/** Bei klarem Himmel mögliche Solarleistung (GW) zu einem Zeitpunkt: hängt von Uhrzeit und Jahreszeit ab (Mitteldeutschland, grob). */
fun clearSkyGw(tSec: Long, capGw: Double): Double {
    val z = Instant.ofEpochSecond(tSec).atZone(ZoneOffset.UTC)
    val decl = Math.toRadians(23.44) * Math.sin(2.0 * Math.PI * (284 + z.dayOfYear) / 365.0)
    val hours = z.hour + z.minute / 60.0
    val ha = Math.toRadians(15.0 * (hours + 10.0 / 15.0 - 12.0))
    val lat = Math.toRadians(51.0)
    val sinEl = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(ha)
    return capGw * 0.65 * Math.max(0.0, sinEl)
}
