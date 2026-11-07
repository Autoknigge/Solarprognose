package de.autoknigge.solarprognose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Markenfarben der autoknigge-Apps: Tannengrün mit Gold.
val Forest = Color(0xFF0B2A21)
val Panel = Color(0xFF123A2E)
val Panel2 = Color(0xFF1B4C3C)
val Gold = Color(0xFFE3B341)
val GoldSoft = Color(0xFFF3D27A)
val TodayOrange = Color(0xFFF2820F)
val GoldDark = Color(0xFFC8921A)
val GoldLight = Color(0xFFFFE9A6)
val TempColor = Color(0xFFFFE27A)
val CloudColor = Color(0xFF9FB8C8)
// Eigene Kartenfarben: Leistung (helles Ocker), Bewölkung und Temperatur (dunkles Orange), Tabelle (bernstein)
val PanelOchre = Color(0xFF9C7A2B)
val LineOchre = Color(0xFFD9B650)
val PanelOrange = Color(0xFF8F4A0F)
val LineOrange = Color(0xFFE08A2E)
val PanelAmber = Color(0xFF3F2E16)
val LineAmber = Color(0xFF9A7432)

/** Kartenhintergrund: von oben links nach unten rechts dunkler werdend. */
fun cardBrush(start: Color): Brush =
    Brush.linearGradient(listOf(start, androidx.compose.ui.graphics.lerp(start, Color.Black, 0.45f)))
val Ink = Color(0xFFEAF2EE)
val Muted = Color(0xFFA4BDB2)
val Line = Color(0xFF2B5848)

@Composable
fun SolarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Forest,
            secondary = GoldSoft,
            onSecondary = Forest,
            background = Forest,
            onBackground = Ink,
            surface = Panel,
            onSurface = Ink,
            surfaceVariant = Panel2,
            onSurfaceVariant = Muted,
            secondaryContainer = Panel2,
            onSecondaryContainer = Ink,
            surfaceContainerLowest = Forest,
            surfaceContainerLow = Panel,
            surfaceContainer = Panel,
            surfaceContainerHigh = Panel2,
            surfaceContainerHighest = Panel2,
            outline = Line,
            outlineVariant = Line,
            error = Color(0xFFFFB4A8),
            onError = Forest
        ),
        content = content
    )
}
