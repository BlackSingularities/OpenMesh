package com.blacksingularities.openmesh.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.mesh.Peer
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.Sheet
import com.blacksingularities.openmesh.ui.ago
import com.blacksingularities.openmesh.ui.theme.Card
import com.blacksingularities.openmesh.ui.theme.IconCircle
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.SectionTitle
import com.blacksingularities.openmesh.ui.theme.initials
import com.blacksingularities.openmesh.util.Geo
import com.blacksingularities.openmesh.util.GeoPoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Osoba naniesiona na mapę: odległość w metrach i kierunek w stopniach od mojej pozycji. */
private data class Plot(val peer: Peer, val distance: Float, val bearing: Float)

/**
 * Mapa okolicy rysowana z samych współrzędnych – bez kafelków, bez internetu.
 * W środku jestem ja, wokół osoby, które przysłały swoją pozycję; pierścienie podają skalę.
 */
@Composable
fun MapScreen(vm: MeshViewModel) {
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    val me = vm.myLocation

    val plots = remember(peers, me) {
        val origin = me ?: return@remember emptyList<Plot>()
        peers.values.mapNotNull { p ->
            val lat = p.lat
            val lon = p.lon
            if (lat == null || lon == null || p.blocked) return@mapNotNull null
            val target = GeoPoint(lat, lon)
            Plot(p, Geo.distance(origin, target), Geo.bearing(origin, target))
        }.sortedBy { it.distance }
    }
    // Skala: najdalsza osoba mieści się w ostatnim pierścieniu, minimum 100 m.
    val range = remember(plots) { max(100f, (plots.maxOfOrNull { it.distance } ?: 0f) * 1.15f) }

    Column(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.statusBars)) {
        TopBar(
            title = "Mapa okolicy",
            subtitle = when {
                me == null -> "potrzebna twoja pozycja GPS"
                plots.isEmpty() -> "nikt nie przysłał jeszcze pozycji"
                else -> "zasięg " + Geo.formatDistance(range)
            },
            onBack = { vm.back() },
        ) {
            IconCircle(Icons.Rounded.Refresh, "Odśwież pozycję", { vm.refreshLocation() }, background = Color.Transparent)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
            item {
                if (me == null) {
                    Card {
                        Icon(Icons.Rounded.MyLocation, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Text("Brak twojej pozycji", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            "Mapa rysuje się względem twojego miejsca. Wyjdź pod otwarte niebo, włącz GPS i pobierz pozycję – wszystko działa bez internetu.",
                            color = Palette.TextDim,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                        )
                        PillButton(
                            if (vm.locating) "Ustalanie pozycji…" else "Pobierz pozycję",
                            { vm.refreshLocation() },
                            icon = Icons.Rounded.MyLocation,
                            enabled = !vm.locating,
                        )
                    }
                } else {
                    MapCanvas(plots, range) { vm.sheet = Sheet.PeerInfo(it.peer.id) }
                }
            }
            if (me != null) {
                item {
                    Text(
                        Geo.formatCoords(me.lat, me.lon) + (me.accuracy?.let { "  ·  dokładność " + Geo.formatDistance(it) } ?: ""),
                        color = Palette.TextFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
            }
            if (plots.isNotEmpty()) {
                item { SectionTitle("Od najbliższej osoby") }
                items(plots, key = { it.peer.id }) { plot ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(38.dp).background(Palette.nick(plot.peer.id).copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initials(plot.peer.name), color = Palette.nick(plot.peer.id), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plot.peer.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Geo.formatDistance(plot.distance) + " na " + Geo.direction(plot.bearing) +
                                    " · pozycja z " + ago(plot.peer.locTs ?: plot.peer.lastSeen, now),
                                color = Palette.TextDim,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        PillButton("Nawiguj", { vm.openMap(plot.peer.lat!!, plot.peer.lon!!, plot.peer.name) }, primary = false)
                    }
                }
            }
            item {
                Text(
                    "Pozycje pochodzą z wiadomości i ogłoszeń obecności, więc mogą być sprzed kilku minut. „Nawiguj” przekazuje punkt do aplikacji map (np. OsmAnd albo Organic Maps – działają offline).",
                    color = Palette.TextFaint,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MapCanvas(plots: List<Plot>, range: Float, onPick: (Plot) -> Unit) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val labels = remember(range) { listOf(range / 3f, 2f * range / 3f, range) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Palette.Card, com.blacksingularities.openmesh.ui.theme.CardShape)
            .onSizeChanged { box = it }
            .pointerInput(plots, range, box) {
                detectTapGestures { tap ->
                    if (box == IntSize.Zero) return@detectTapGestures
                    val center = Offset(box.width / 2f, box.height / 2f)
                    val radius = minOf(box.width, box.height) / 2f - 24f
                    val hit = plots.minByOrNull { p ->
                        val at = place(center, radius, p, range)
                        hypot(at.x - tap.x, at.y - tap.y)
                    } ?: return@detectTapGestures
                    val at = place(center, radius, hit, range)
                    if (hypot(at.x - tap.x, at.y - tap.y) < 60f) onPick(hit)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = minOf(this.size.width, this.size.height) / 2f - 24f

            // Pierścienie skali i osie świata.
            labels.forEachIndexed { i, _ ->
                drawCircle(
                    color = Color.White.copy(alpha = if (i == labels.lastIndex) 0.16f else 0.08f),
                    radius = radius * (i + 1) / labels.size,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }
            drawLine(Color.White.copy(alpha = 0.07f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
            drawLine(Color.White.copy(alpha = 0.07f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))

            // Ja w środku.
            drawCircle(Color.White.copy(alpha = 0.16f), radius = 16f, center = center)
            drawCircle(Color.White, radius = 6f, center = center)

            plots.forEach { p ->
                val at = place(center, radius, p, range)
                val color = Palette.nick(p.peer.id)
                drawCircle(color.copy(alpha = 0.22f), radius = 20f, center = at)
                drawCircle(color, radius = 7f, center = at)
            }
        }
        // Opisy: kierunki świata i skala pierścieni.
        Text("N", color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        Text("S", color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        Text("W", color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        Text("E", color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { Text(Geo.formatDistance(it), color = Palette.TextFaint, fontSize = 10.5.sp) }
        }
        if (plots.isNotEmpty()) {
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End) {
                plots.take(6).forEach { p ->
                    Text(
                        p.peer.name,
                        color = Palette.nick(p.peer.id),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Punkt na tarczy: północ u góry, odległość liniowo do krawędzi. */
private fun place(center: Offset, radius: Float, p: Plot, range: Float): Offset {
    val r = (p.distance / range).coerceIn(0f, 1f) * radius
    val rad = Math.toRadians(p.bearing.toDouble())
    return Offset(center.x + (r * sin(rad)).toFloat(), center.y - (r * cos(rad)).toFloat())
}
