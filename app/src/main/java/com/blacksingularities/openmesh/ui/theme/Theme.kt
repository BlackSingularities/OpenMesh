package com.blacksingularities.openmesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    // Czarno-biały interfejs jak w OpenPDF: akcent = biel, czerwień tylko dla SOS i usuwania.
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF0D0D0D)
    val Card = Color(0xFF161616)
    val CardHigh = Color(0xFF1C1C1C)
    val CardHigher = Color(0xFF262626)
    val Line = Color(0x1FFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Sos = Color(0xFFFF3B47)
    val SosDeep = Color(0xFF3A0B0F)
    val Online = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFB020)

    /** Stonowane kolory nicków – rozróżnialne na czarnym tle, bez krzykliwości. */
    private val nickColors = listOf(
        Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663), Color(0xFF81C995),
        Color(0xFFFF8BCB), Color(0xFFC58AF9), Color(0xFF78D9EC), Color(0xFFFCAD70),
    )

    fun nick(id: String): Color = nickColors[(id.hashCode() and 0x7fffffff) % nickColors.size]
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Palette.Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.CardHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.CardHigh,
    surfaceContainerHighest = Palette.CardHigher,
    outline = Color(0x33FFFFFF),
    outlineVariant = Palette.Line,
    error = Palette.Sos,
)

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp)

@Composable
fun OpenMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
