package com.blacksingularities.openmesh.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.blacksingularities.openmesh.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Znak OpenMesh: pięć węzłów połączonych krawędziami wokół środka (jak ikona aplikacji). */
@Composable
fun MeshMark(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val s = min(size.width, size.height) / 50f
        fun p(x: Float, y: Float) = Offset((x - 29f) * s + (size.width - 50f * s) / 2, (y - 27f) * s + (size.height - 50f * s) / 2)
        val top = p(54f, 33f)
        val l = p(36f, 47f)
        val r = p(72f, 47f)
        val bl = p(42f, 72f)
        val br = p(66f, 72f)
        val c = p(54f, 56f)
        val edges = listOf(top to l, top to r, l to r, l to bl, r to br, bl to br, top to c, l to c, r to c, bl to c, br to c)
        edges.forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 3f * s, cap = StrokeCap.Round) }
        drawCircle(color, 6f * s, top)
        listOf(l, r, bl, br).forEach { drawCircle(color, 5f * s, it) }
        drawCircle(color, 4f * s, c)
    }
}

/**
 * Radar: pulsujące okręgi i kropki sąsiadów rozmieszczone na orbitach według liczby skoków.
 * [direct] – liczba połączeń bezpośrednich, [indirect] – węzły dalej w sieci.
 */
@Composable
fun Radar(direct: Int, indirect: Int, active: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = min(size.width, size.height) / 2 * 0.96f
        // Stałe orbity.
        listOf(0.36f, 0.68f, 1f).forEach {
            drawCircle(Color.White.copy(alpha = 0.07f), maxR * it, center, style = Stroke(1.5f))
        }
        if (active) {
            listOf(0f, 0.5f).forEach { offset ->
                val f = (pulse + offset) % 1f
                drawCircle(Color.White.copy(alpha = 0.22f * (1f - f)), maxR * f, center, style = Stroke(2f))
            }
        }
        fun dots(n: Int, radius: Float, dot: Float, alpha: Float, phase: Float) {
            for (i in 0 until n) {
                val a = (spin + phase + i * 360f / n) * PI.toFloat() / 180f
                val pos = Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
                drawLine(Color.White.copy(alpha = 0.10f), center, pos, strokeWidth = 1.5f)
                drawCircle(Color.White.copy(alpha = alpha), dot, pos)
            }
        }
        dots(direct.coerceAtMost(12), maxR * 0.36f, 7f, 1f, 0f)
        dots(indirect.coerceAtMost(20), maxR * 0.68f, 5f, 0.55f, 17f)
        drawCircle(if (active) Color.White else Palette.TextFaint, 11f, center)
    }
}
