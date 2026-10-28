package com.blacksingularities.openmesh.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.blacksingularities.openmesh.data.Attachments
import com.blacksingularities.openmesh.mesh.FileTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.MsgStatus
import com.blacksingularities.openmesh.mesh.Notifier.plural
import com.blacksingularities.openmesh.mesh.channelOf
import com.blacksingularities.openmesh.mesh.isDm
import com.blacksingularities.openmesh.mesh.peerOf
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.Sheet
import com.blacksingularities.openmesh.ui.ago
import com.blacksingularities.openmesh.ui.clock
import com.blacksingularities.openmesh.ui.dayHeader
import com.blacksingularities.openmesh.ui.hopsLabel
import com.blacksingularities.openmesh.ui.isOnline
import com.blacksingularities.openmesh.ui.isSameDay
import com.blacksingularities.openmesh.ui.theme.Avatar
import com.blacksingularities.openmesh.ui.theme.Field
import com.blacksingularities.openmesh.ui.theme.IconCircle
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.Pill
import com.blacksingularities.openmesh.ui.theme.panel
import com.blacksingularities.openmesh.util.Geo
import com.blacksingularities.openmesh.util.GeoPoint

val QUICK_REPLIES = listOf(
    "Jestem OK 👍", "Gdzie jesteś?", "Czekam tutaj", "Idę do was", "Potrzebuję pomocy",
    "Zbiórka przy wejściu", "Wracam", "Bateria się kończy 🔋", "Słabo słyszę – piszę", "Dotarłem",
)

private sealed interface Row0 {
    data class Day(val label: String, val key: String) : Row0
    data class Msg(val m: Message, val first: Boolean, val last: Boolean) : Row0
}

@Composable
fun ChatScreen(vm: MeshViewModel, convo: String) {
    val all by vm.core.messages.collectAsStateWithLifecycle()
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val channels by vm.core.channels.collectAsStateWithLifecycle()
    val links by vm.core.transport.links.collectAsStateWithLifecycle()
    val drafts by vm.core.drafts.collectAsStateWithLifecycle()
    val now = rememberNow(30_000)
    val dm = isDm(convo)
    val peer = if (dm) peers[peerOf(convo)] else null
    val channel = if (!dm) channels.firstOrNull { it.key == convo } else null
    val linkedPeer = peer != null && links.values.any { it.peerId == peer.id }

    val messages = remember(all, convo) { all.filter { it.convo == convo }.sortedBy { it.ts } }
    // Elementy listy od najnowszych (odwrócony układ): dymki z grupowaniem i nagłówki dni.
    val rows = remember(messages) {
        val out = ArrayList<Row0>(messages.size + 8)
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            val prev = messages.getOrNull(i - 1)
            val next = messages.getOrNull(i + 1)
            fun groups(a: Message?, b: Message?) = a != null && b != null && a.from == b.from && !a.sos && !b.sos &&
                kotlin.math.abs(a.ts - b.ts) < 5 * 60_000 && isSameDay(a.ts, b.ts)
            out += Row0.Msg(m, first = !groups(prev, m), last = !groups(m, next))
            if (prev == null || !isSameDay(prev.ts, m.ts)) out += Row0.Day(dayHeader(m.ts, now), "day:" + m.id)
        }
        out
    }

    LaunchedEffect(messages.size) { vm.core.markRead(convo) }

    var text by remember(convo) { mutableStateOf(vm.sharedText ?: drafts[convo] ?: "") }
    LaunchedEffect(convo) { vm.sharedText = null }
    var showQuick by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    // Wybieranie zdjec przez systemowy wybor mediow nie wymaga zadnych uprawnien.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        showAttach = false
        if (uri != null) vm.sendAttachment(convo, uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        showAttach = false
        if (uri != null) vm.sendAttachment(convo, uri)
    }
    val list = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) { if (list.firstVisibleItemIndex < 3) list.animateScrollToItem(0) }

    val title = when {
        dm -> peer?.name ?: messages.firstOrNull { !it.mine }?.fromName ?: "Rozmowa prywatna"
        else -> "#" + (channel?.name ?: channelOf(convo))
    }
    val subtitle = when {
        dm && peer == null -> "Nieznany klucz – poczekaj na kontakt"
        dm && linkedPeer -> "bezpośrednio w zasięgu · szyfrowane E2E"
        dm && peer!!.isOnline(now) -> hopsLabel(peer.hops) + " · szyfrowane E2E"
        dm -> "widziany " + ago(peer!!.lastSeen, now) + " · szyfrowane E2E"
        channel?.secured == true -> "kanał chroniony hasłem · szyfrowany"
        convo == Channel.convoOf(com.blacksingularities.openmesh.mesh.PUBLIC_CHANNEL) -> "kanał publiczny · wszyscy w zasięgu"
        else -> "kanał jawny"
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        // Pasek tytułu
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć", { vm.back() }, background = Color.Transparent)
            Row(
                Modifier.weight(1f).clip(Pill).clickable { vm.sheet = if (dm && peer != null) Sheet.PeerInfo(peer.id) else Sheet.ConvoInfo(convo) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dm) {
                    Avatar(title, peerOf(convo), size = 40.dp, online = linkedPeer || peer?.isOnline(now) == true)
                } else {
                    Avatar(title, convo, size = 40.dp, icon = if (channel?.secured == true) Icons.Rounded.Lock else Icons.Rounded.Tag)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconCircle(Icons.Rounded.MoreVert, "Szczegóły", { vm.sheet = Sheet.ConvoInfo(convo) }, background = Color.Transparent)
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MeshMark(Modifier.size(56.dp), color = Palette.TextFaint)
                    Text(
                        if (dm) "Wiadomości są szyfrowane – odczyta je tylko " + title + "." else "Wiadomości w " + title + " widzą wszyscy, którzy dołączyli do kanału.",
                        color = Palette.TextDim,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
            LazyColumn(
                state = list,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(rows, key = { r -> if (r is Row0.Msg) r.m.id else (r as Row0.Day).key }) { r ->
                    when (r) {
                        is Row0.Day -> DayChip(r.label)
                        is Row0.Msg -> Bubble(vm, r.m, showName = !dm && r.first && !r.m.mine, first = r.first, last = r.last, now = now)
                    }
                }
            }
        }

        // Szybkie odpowiedzi
        AnimatedVisibility(showQuick, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_REPLIES.forEach { q ->
                    Text(
                        q,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.clip(Pill).background(Palette.CardHigh).clickable {
                            vm.quick(convo, q)
                            showQuick = false
                        }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }

        AnimatedVisibility(showAttach, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    "Zdjęcie",
                    { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.weight(1f),
                    icon = Icons.Rounded.Image,
                    primary = false,
                )
                PillButton(
                    "Plik",
                    { pickFile.launch(arrayOf("*/*")) },
                    Modifier.weight(1f),
                    icon = Icons.Rounded.Description,
                    primary = false,
                )
            }
        }

        // Pole wiadomości
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconCircle(
                Icons.Rounded.AttachFile,
                "Załącz zdjęcie lub plik",
                {
                    showAttach = !showAttach
                    showQuick = false
                },
                active = showAttach,
                size = 46.dp,
            )
            Spacer(Modifier.width(6.dp))
            IconCircle(
                Icons.Rounded.Bolt,
                "Szybkie odpowiedzi",
                {
                    showQuick = !showQuick
                    showAttach = false
                },
                active = showQuick,
                size = 46.dp,
            )
            Spacer(Modifier.width(6.dp))
            if (vm.locating) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            } else {
                IconCircle(Icons.Rounded.MyLocation, "Wyślij pozycję", { vm.shareLocation(convo, text.trim()).also { text = "" } }, size = 46.dp)
            }
            Spacer(Modifier.width(6.dp))
            Field(
                text,
                {
                    text = it.take(2000)
                    vm.core.setDraft(convo, text)
                },
                if (dm) "Wiadomość szyfrowana" else "Wiadomość do " + title,
                Modifier.weight(1f),
                singleLine = false,
                maxLines = 5,
                imeAction = ImeAction.Default,
                shape = RoundedCornerShape(23.dp),
            )
            Spacer(Modifier.width(6.dp))
            IconCircle(
                Icons.AutoMirrored.Rounded.Send,
                "Wyślij",
                {
                    vm.send(convo, text)
                    text = ""
                },
                active = text.isNotBlank(),
                enabled = text.isNotBlank(),
                size = 46.dp,
            )
        }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(Pill).background(Palette.Card).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(vm: MeshViewModel, m: Message, showName: Boolean, first: Boolean, last: Boolean, now: Long) {
    val haptic = LocalHapticFeedback.current
    val mine = m.mine
    val big = 20.dp
    val small = 6.dp
    val shape = if (mine) {
        RoundedCornerShape(topStart = big, topEnd = if (first) big else small, bottomEnd = if (last) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (last) big else small)
    }
    val bg = when {
        m.sos -> Palette.Sos
        mine -> Color.White
        else -> Palette.CardHigh
    }
    val fg = if (mine && !m.sos) Palette.Ink else Color.White
    val dim = if (mine && !m.sos) Palette.Ink.copy(alpha = 0.55f) else Palette.TextDim

    Column(
        Modifier.fillMaxWidth().padding(top = if (first) 8.dp else 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (showName) {
            Text(m.fromName, color = Palette.nick(m.from), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, bottom = 3.dp))
        }
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .clip(shape)
                .background(bg)
                .combinedClickable(
                    onClick = {
                        when {
                            m.hasLocation -> vm.openMap(m.lat!!, m.lon!!, m.fromName)
                            m.hasAttachment && m.attDone -> vm.openAttachment(m)
                            else -> vm.sheet = Sheet.MessageActions(m)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.sheet = Sheet.MessageActions(m)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            if (m.sos) {
                Text("🆘 SOS" + if (!mine) " · " + m.fromName else "", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.5.sp)
            }
            if (m.hasLocation) {
                LocationCard(vm, m, fg, dim)
            }
            if (m.hasAttachment) {
                AttachmentCard(vm, m, fg, dim)
            }
            if (m.text.isNotBlank()) {
                Text(
                    m.text,
                    color = fg,
                    fontSize = if (m.quick) 16.sp else 15.5.sp,
                    fontWeight = if (m.quick || m.sos) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = if (m.sos || m.hasLocation) 6.dp else 0.dp),
                )
            }
            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!mine && m.hops > 0) {
                    Text(hopsLabel(m.hops) + " · ", color = dim, fontSize = 11.sp)
                }
                if (m.battery != null && m.sos) {
                    Text("🔋" + m.battery + "% · ", color = dim, fontSize = 11.sp)
                }
                Text(clock(m.ts), color = dim, fontSize = 11.sp)
                if (mine) {
                    Spacer(Modifier.width(4.dp))
                    val (icon, label) = when (m.status) {
                        MsgStatus.QUEUED -> Icons.Rounded.Schedule to "W kolejce – wyjdzie po połączeniu"
                        MsgStatus.SENT -> Icons.Rounded.Done to "Wysłano do sieci"
                        MsgStatus.DELIVERED -> Icons.Rounded.DoneAll to "Doręczono"
                    }
                    Icon(icon, label, tint = dim, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (mine && last && m.status == MsgStatus.QUEUED) {
            Text("Czeka na kogoś w zasięgu", color = Palette.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp, top = 2.dp))
        }
    }
}

/**
 * Załącznik w dymku: zdjęcie jako podgląd, plik jako wiersz z nazwą i rozmiarem.
 * Dopóki części jadą przez sieć, w miejscu pliku widnieje postęp.
 */
@Composable
private fun AttachmentCard(vm: MeshViewModel, m: Message, fg: Color, dim: Color) {
    val ctx = LocalContext.current
    val id = m.attId ?: return
    val progress = if (m.attTotal > 0) m.attParts.toFloat() / m.attTotal else 0f
    val waiting = !m.attDone
    val sending = m.mine && m.attTotal > 0 && m.attParts < m.attTotal

    if (m.isImage && m.attDone) {
        val thumb by produceState<Bitmap?>(null, id) {
            value = withContext(Dispatchers.IO) { Attachments.thumbnail(ctx, id) }
        }
        Column(Modifier.padding(top = if (m.sos || m.hasLocation) 6.dp else 2.dp)) {
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    m.attName ?: "Zdjęcie",
                    modifier = Modifier
                        .widthIn(max = 282.dp)
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier.size(180.dp).clip(RoundedCornerShape(14.dp)).background(Palette.CardHigher),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Image, null, tint = Palette.TextFaint, modifier = Modifier.size(28.dp))
                }
            }
            if (sending) {
                Text(
                    "Wysyłam… " + m.attParts + " z " + m.attTotal,
                    color = dim,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        return
    }

    Column(Modifier.padding(top = if (m.sos || m.hasLocation) 6.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (fg == Palette.Ink) Palette.Ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (waiting) Icons.Rounded.Downloading else if (m.isImage) Icons.Rounded.Image else Icons.Rounded.InsertDriveFile,
                    null,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 210.dp)) {
                Text(
                    m.attName ?: "Plik",
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        m.mine && sending -> "Wysyłam… " + m.attParts + " z " + m.attTotal + " części"
                        m.mine -> FileTransfer.formatSize(m.attSize) + " · wysłane"
                        waiting -> "Odbieram… " + m.attParts + " z " + m.attTotal + " części"
                        else -> FileTransfer.formatSize(m.attSize)
                    },
                    color = dim,
                    fontSize = 12.sp,
                )
            }
        }
        if (waiting || sending) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .widthIn(min = 160.dp, max = 262.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(Pill)
                    .background(if (fg == Palette.Ink) Palette.Ink.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.18f)),
            ) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(Pill).background(fg))
            }
        }
        if (waiting && !m.mine) {
            Text(
                "Dokończ odbiór",
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(Pill)
                    .background(if (fg == Palette.Ink) Palette.Ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f))
                    .clickable { vm.requestMissingParts(m) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LocationCard(vm: MeshViewModel, m: Message, fg: Color, dim: Color) {
    val lat = m.lat ?: return
    val lon = m.lon ?: return
    val me = vm.myLocation
    val rel = if (me != null && !m.mine) {
        val other = GeoPoint(lat, lon)
        Geo.formatDistance(Geo.distance(me, other)) + " na " + Geo.direction(Geo.bearing(me, other))
    } else {
        null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (m.sos) 6.dp else 2.dp)) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (fg == Palette.Ink) Palette.Ink.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Map, null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(if (m.mine) "Moja pozycja" else "Pozycja " + m.fromName, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(Geo.formatCoords(lat, lon) + (m.acc?.let { " ±${it.toInt()} m" } ?: ""), color = dim, fontSize = 12.sp)
            if (rel != null) Text(rel, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Suppress("unused")
private fun peopleLabel(n: Int) = "$n " + plural(n, "osoba", "osoby", "osób")
