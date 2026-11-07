package de.autoknigge.solarprognose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
val TempColorDark = Color(0xFF7A1E00)
val CloudColorDark = Color(0xFF28465C)
// Kartenfarben (Vorgabe von Marc, jeweils Startfarbe oben links, nach unten rechts dunkler)
val CardPower = Color(0xFF84B067)
val CardPowerLine = Color(0xFFA6C98B)
val CardClouds = Color(0xFFFFBF00)
val CardCloudsLine = Color(0xFFFFD866)
val CardTable = Color(0xFFE0CD95)
val CardTableLine = Color(0xFFF0E3B8)
val CardPrice = Color(0xFF00674F)
val CardPriceLine = Color(0xFF2FA283)
val TileToday = Color(0xFFE35336)
val TileTomorrow = Color(0xFFD47E30)
val WarmMuted = Color(0xFFFFE8D6)
val CloudColorLight = Color(0xFFE6F0F7)
val DarkInk = Color(0xFF14231B)
val DarkMuted = Color(0xFF34453B)

val LocalInk = compositionLocalOf { Ink }
val LocalMuted = compositionLocalOf { Muted }

/** Kartenhintergrund: von oben links nach unten rechts dunkler werdend. */
fun cardBrush(start: Color, darken: Float = 0.45f): Brush =
    Brush.linearGradient(listOf(start, androidx.compose.ui.graphics.lerp(start, Color.Black, darken)))

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
