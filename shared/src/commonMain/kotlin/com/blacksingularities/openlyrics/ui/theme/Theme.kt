package com.blacksingularities.openlyrics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Czarno-biały interfejs jak w OpenDocument i OpenShot: akcent = biel, czerwień dla usuwania. */
object Palette {
    val Accent = Color(0xFFFFFFFF)
    val Danger = Color(0xFFFF3B47)
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF000000)
    val PanelHigh = Color(0xFF1C1C1C)
    val PanelHigher = Color(0xFF262626)
    val Line = Color(0x1FFFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Ok = Color(0xFF7CE38B)

    /** Kolory aktywnego wiersza do wyboru w ustawieniach nakładki. */
    val LyricColors = listOf(
        0xFFFFFFFF, 0xFFFFE066, 0xFF7CE3FF, 0xFF9CFF8A,
        0xFFFF9EC7, 0xFFFFB061, 0xFFC5A3FF, 0xFFFF6B6B,
    )
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Line,
    error = Palette.Danger,
)

@Composable
fun OpenLyricsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
