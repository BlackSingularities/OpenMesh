package com.blacksingularities.openmesh.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.Notifier.plural
import com.blacksingularities.openmesh.mesh.channelOf
import com.blacksingularities.openmesh.mesh.dmConvo
import com.blacksingularities.openmesh.mesh.isDm
import com.blacksingularities.openmesh.mesh.peerOf
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.shortWhen
import com.blacksingularities.openmesh.ui.theme.Avatar
import com.blacksingularities.openmesh.ui.theme.Field
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.SectionTitle
import com.blacksingularities.openmesh.ui.theme.Tag

private val Rounded = RoundedCornerShape(20.dp)
private const val MAX_HITS = 120

/** Jeden wynik: dopasowana wiadomość wraz z nazwą rozmowy, w której się znajduje. */
private data class Hit(val message: Message, val convo: String, val title: String)

/**
 * Szukanie w całej historii. Wszystko dzieje się lokalnie na urządzeniu – przeszukiwane są
 * zapisane wiadomości, kanały i znane osoby. Wynik otwiera rozmowę, w której coś znaleziono.
 */
@Composable
fun SearchScreen(vm: MeshViewModel) {
    val messages by vm.core.messages.collectAsStateWithLifecycle()
    val channels by vm.core.channels.collectAsStateWithLifecycle()
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    val q = vm.query.trim()

    val names = remember(channels, peers, messages) {
        val map = HashMap<String, String>()
        channels.forEach { map[it.key] = "#" + it.name }
        messages.forEach { m ->
            if (m.convo !in map) {
                map[m.convo] = if (isDm(m.convo)) {
                    val id = peerOf(m.convo)
                    peers[id]?.name ?: (if (m.mine) null else m.fromName) ?: id.take(8)
                } else {
                    "#" + channelOf(m.convo)
                }
            }
        }
        map
    }

    val hits = remember(q, messages, names) {
        if (q.length < 2) {
            emptyList()
        } else {
            messages.asReversed()
                .asSequence()
                .filter { it.text.contains(q, ignoreCase = true) || it.fromName.contains(q, ignoreCase = true) }
                .map { Hit(it, it.convo, names[it.convo] ?: it.convo) }
                .take(MAX_HITS)
                .toList()
        }
    }
    val peerHits = remember(q, peers) {
        if (q.length < 2) emptyList() else peers.values.filter { it.name.contains(q, ignoreCase = true) }.sortedByDescending { it.lastSeen }
    }
    val channelHits = remember(q, channels) {
        if (q.length < 2) emptyList() else channels.filter { it.name.contains(q, ignoreCase = true) }
    }
    val empty = hits.isEmpty() && peerHits.isEmpty() && channelHits.isEmpty()

    Column(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        TopBar(
            title = "Szukaj",
            subtitle = when {
                q.length < 2 -> messages.size.toString() + " " + plural(messages.size, "wiadomość", "wiadomości", "wiadomości") + " w pamięci"
                empty -> "brak wyników"
                else -> hits.size.toString() + " " + plural(hits.size, "wynik", "wyniki", "wyników")
            },
            onBack = { vm.back() },
        )
        Field(
            value = vm.query,
            onChange = { vm.query = it },
            placeholder = "Treść wiadomości, nick, kanał…",
            leading = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp)) {
            if (channelHits.isNotEmpty()) {
                item { SectionTitle("Kanały") }
                items(channelHits, key = { "ch:" + it.key }) { ch ->
                    ResultRow(
                        title = "#" + ch.name,
                        subtitle = if (ch.secured) "chroniony hasłem" else "kanał jawny",
                        colorId = ch.key,
                        icon = true,
                        secured = ch.secured,
                        onClick = { vm.open(ch.key) },
                    )
                }
            }
            if (peerHits.isNotEmpty()) {
                item { SectionTitle("Osoby") }
                items(peerHits, key = { "p:" + it.id }) { p ->
                    ResultRow(
                        title = p.name,
                        subtitle = "widziany " + shortWhen(p.lastSeen, now),
                        colorId = p.id,
                        onClick = { vm.open(dmConvo(p.id)) },
                    )
                }
            }
            if (hits.isNotEmpty()) {
                item { SectionTitle("Wiadomości") }
                items(hits, key = { "m:" + it.message.id }) { h -> HitRow(h, q, now) { vm.open(h.convo) } }
            }
            if (q.length >= 2 && empty) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Search, null, tint = Palette.TextFaint, modifier = Modifier.size(32.dp))
                        Text(
                            "Nic nie znaleziono dla „" + q + "”",
                            color = Palette.TextDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            if (q.length < 2) {
                item {
                    Text(
                        "Wpisz co najmniej dwa znaki. Szukanie obejmuje całą historię zapisaną na tym telefonie – także rozmowy z archiwum.",
                        color = Palette.TextDim,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    colorId: String,
    icon: Boolean = false,
    secured: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(Rounded).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, colorId, size = 44.dp, icon = if (icon) (if (secured) Icons.Rounded.Lock else Icons.Rounded.Tag) else null)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HitRow(h: Hit, query: String, now: Long, onClick: () -> Unit) {
    val m = h.message
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(Rounded)
            .background(Palette.Card)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                h.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(shortWhen(m.ts, now), color = Palette.TextFaint, fontSize = 11.5.sp)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (m.mine) "Ty" else m.fromName,
                color = if (m.mine) Color.White else Palette.nick(m.from),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (m.sos) Tag("SOS", color = Color.White, background = Palette.Sos)
            if (m.hasLocation) Tag("pozycja")
        }
        Text(
            snippet(m.text, query),
            color = Palette.TextDim,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Fragment treści wokół dopasowania – żeby nie pokazywać całej długiej wiadomości. */
private fun snippet(text: String, query: String, radius: Int = 48): String {
    val i = text.indexOf(query, ignoreCase = true)
    if (i < 0 || text.length <= 2 * radius) return text
    val start = (i - radius).coerceAtLeast(0)
    val end = (i + query.length + radius).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}
