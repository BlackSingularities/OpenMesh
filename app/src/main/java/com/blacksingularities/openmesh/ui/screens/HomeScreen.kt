package com.blacksingularities.openmesh.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.LinkState
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.Notifier.plural
import com.blacksingularities.openmesh.mesh.PUBLIC_CHANNEL
import com.blacksingularities.openmesh.mesh.Peer
import com.blacksingularities.openmesh.mesh.isDm
import com.blacksingularities.openmesh.mesh.peerOf
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.Sheet
import com.blacksingularities.openmesh.ui.Tab
import com.blacksingularities.openmesh.ui.ago
import com.blacksingularities.openmesh.ui.hopsLabel
import com.blacksingularities.openmesh.ui.isOnline
import com.blacksingularities.openmesh.ui.shortWhen
import com.blacksingularities.openmesh.ui.theme.Avatar
import com.blacksingularities.openmesh.ui.theme.CountBadge
import com.blacksingularities.openmesh.ui.theme.IconCircle
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.Pill
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.SectionTitle
import com.blacksingularities.openmesh.ui.theme.Tag
import com.blacksingularities.openmesh.ui.theme.panel
import com.blacksingularities.openmesh.util.Geo
import com.blacksingularities.openmesh.util.GeoPoint
import com.blacksingularities.openmesh.util.Permissions
import kotlinx.coroutines.delay

/** Zegar odświeżający względne czasy („2 min temu”) i stan „w zasięgu”. */
@Composable
fun rememberNow(periodMs: Long = 20_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun HomeScreen(vm: MeshViewModel) {
    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Header(vm)
            UpdateBanner(vm, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            MeshWarning(vm)
            Box(Modifier.weight(1f)) {
                AnimatedContent(vm.tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                    when (t) {
                        Tab.CHATS -> ChatsTab(vm)
                        Tab.NEARBY -> NearbyTab(vm)
                        Tab.SETTINGS -> SettingsTab(vm)
                    }
                }
            }
        }
        BottomBar(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(vm: MeshViewModel) {
    val links by vm.core.transport.links.collectAsStateWithLifecycle()
    val state by vm.core.transport.state.collectAsStateWithLifecycle()
    val settings by vm.core.settings.collectAsStateWithLifecycle()
    val title = when (vm.tab) {
        Tab.CHATS -> "Rozmowy"
        Tab.NEARBY -> "W pobliżu"
        Tab.SETTINGS -> "Ustawienia"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            MeshStatusLine(settings.meshEnabled, state, links.size) { vm.tab = Tab.NEARBY }
        }
        IconCircle(Icons.Rounded.Search, "Szukaj", { vm.openSearch() }, background = Palette.CardHigh)
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(Pill)
                .background(Palette.Sos)
                .clickable { vm.sheet = Sheet.Sos }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun MeshStatusLine(enabled: Boolean, state: LinkState, links: Int, onClick: () -> Unit) {
    val (color, text) = when {
        !enabled -> Palette.TextFaint to "Sieć wyłączona"
        state is LinkState.Error -> Palette.Warn to "Problem z radiem"
        state == LinkState.Starting -> Palette.Warn to "Uruchamianie…"
        links > 0 -> Palette.Online to "$links " + plural(links, "połączenie", "połączenia", "połączeń")
        state == LinkState.On -> Color.White to "Szukanie urządzeń…"
        else -> Palette.TextFaint to "Nieaktywna"
    }
    Row(
        Modifier.padding(top = 2.dp).clip(Pill).clickable(onClick = onClick).padding(vertical = 2.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ostrzeżenie, gdy sieć nie może działać – z przyciskiem naprawy. */
@Composable
private fun MeshWarning(vm: MeshViewModel) {
    val ctx = LocalContext.current
    val settings by vm.core.settings.collectAsStateWithLifecycle()
    val state by vm.core.transport.state.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val tick = vm.permissionsTick
    val granted = Permissions.meshGranted(ctx)
    val (text, action, onAction) = when {
        !granted -> Triple("Brak uprawnień do Bluetooth i urządzeń w pobliżu.", "Nadaj", { Permissions.openAppSettings(ctx) })
        !settings.meshEnabled -> Triple("Sieć jest wyłączona – nie odbierasz ani nie przekazujesz wiadomości.", "Włącz", { vm.setMeshEnabled(true) })
        state is LinkState.Error -> Triple((state as LinkState.Error).message, "Bluetooth", { Permissions.openBluetoothSettings(ctx) })
        else -> Triple(null, null, {})
    }
    AnimatedVisibility(text != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).panel(color = Palette.CardHigh).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Warning, null, tint = Palette.Warn, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text ?: "", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
            if (action != null) {
                Text(
                    action,
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp).clip(Pill).background(Color.White).clickable(onClick = onAction).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(vm: MeshViewModel, modifier: Modifier) {
    val unread by vm.core.unread.collectAsStateWithLifecycle()
    val total = unread.values.sum()
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .panel(Pill, Palette.CardHigh)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Rounded.ChatBubble, "Rozmowy", vm.tab == Tab.CHATS, total) { vm.tab = Tab.CHATS }
        NavItem(Icons.Rounded.Radar, "W pobliżu", vm.tab == Tab.NEARBY, 0) { vm.tab = Tab.NEARBY }
        NavItem(Icons.Rounded.Settings, "Ustawienia", vm.tab == Tab.SETTINGS, 0) { vm.tab = Tab.SETTINGS }
        AnimatedVisibility(vm.tab == Tab.CHATS, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            IconCircle(Icons.Rounded.Add, "Nowa rozmowa", { vm.sheet = Sheet.NewChat }, active = true, size = 48.dp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, badge: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .clip(Pill)
            .background(if (active) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (active) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(icon, label, tint = if (active) Palette.Ink else Palette.TextDim, modifier = Modifier.size(22.dp))
            if (badge > 0 && !active) {
                Box(Modifier.align(Alignment.TopEnd).padding(start = 14.dp).size(9.dp).clip(CircleShape).background(Color.White))
            }
        }
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (badge > 0) {
                Spacer(Modifier.width(6.dp))
                CountBadge(badge, color = Palette.Ink)
            }
        }
    }
}

// region Rozmowy

data class ConvoSummary(
    val convo: String,
    val title: String,
    val idForColor: String,
    val last: Message?,
    val unread: Int,
    val secured: Boolean,
    val muted: Boolean,
    val online: Boolean?,
    val sortKey: Long,
    val isChannel: Boolean,
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

@Composable
private fun ChatsTab(vm: MeshViewModel) {
    val messages by vm.core.messages.collectAsStateWithLifecycle()
    val channels by vm.core.channels.collectAsStateWithLifecycle()
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val unread by vm.core.unread.collectAsStateWithLifecycle()
    val seenChannels by vm.core.seenChannels.collectAsStateWithLifecycle()
    val links by vm.core.transport.links.collectAsStateWithLifecycle()
    val settings by vm.core.settings.collectAsStateWithLifecycle()
    val now = rememberNow()

    val all = remember(messages, channels, peers, unread, links, now) {
        val lastBy = HashMap<String, Message>()
        messages.forEach { m -> if ((lastBy[m.convo]?.ts ?: 0) <= m.ts) lastBy[m.convo] = m }
        val linked = links.values.mapTo(HashSet()) { it.peerId }
        val list = mutableListOf<ConvoSummary>()
        channels.forEach { ch ->
            val last = lastBy[ch.key]
            list += ConvoSummary(ch.key, "#" + ch.name, ch.key, last, unread[ch.key] ?: 0, ch.secured, ch.muted, null, last?.ts ?: ch.joinedAt, true)
        }
        lastBy.keys.filter { isDm(it) }.forEach { key ->
            val id = peerOf(key)
            val p = peers[id]
            val last = lastBy[key]
            list += ConvoSummary(
                key,
                p?.name ?: last?.takeIf { !it.mine }?.fromName ?: id.take(8),
                id,
                last,
                unread[key] ?: 0,
                true,
                false,
                id in linked || (p?.isOnline(now) == true),
                last?.ts ?: 0,
                false,
            )
        }
        // Kanał ogólny na górze, reszta wg ostatniej aktywności.
        val pub = Channel.convoOf(PUBLIC_CHANNEL)
        list.sortedWith(compareByDescending<ConvoSummary> { it.convo == pub }.thenByDescending { it.sortKey })
    }
    // Przypięte zawsze na górze, archiwum schowane pod osobnym wierszem.
    val convos = remember(all, settings.pinned, settings.archived) {
        val marked = all.map { it.copy(pinned = it.convo in settings.pinned, archived = it.convo in settings.archived) }
        marked.filter { it.pinned }.sortedByDescending { it.sortKey } + marked.filter { !it.pinned && !it.archived }
    }
    val archived = remember(all, settings.archived) {
        all.filter { it.convo in settings.archived }.map { it.copy(archived = true) }.sortedByDescending { it.sortKey }
    }
    val discoverable = remember(seenChannels, channels) {
        seenChannels.values.filter { s -> channels.none { it.key == Channel.convoOf(s.name) } }.sortedByDescending { it.count }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp)) {
        items(convos, key = { it.convo }) { c ->
            ConvoRow(c, vm.core.myId, now, onClick = { vm.open(c.convo) }, onLongClick = { vm.sheet = Sheet.ConvoInfo(c.convo) })
        }
        if (archived.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCard).clickable { vm.showArchived = !vm.showArchived }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Inventory2, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Archiwum · " + archived.size + " " + plural(archived.size, "rozmowa", "rozmowy", "rozmów"),
                        color = Palette.TextDim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (vm.showArchived) "Ukryj" else "Pokaż", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (vm.showArchived) {
                items(archived, key = { "arch:" + it.convo }) { c ->
                    ConvoRow(c, vm.core.myId, now, onClick = { vm.open(c.convo) }, onLongClick = { vm.sheet = Sheet.ConvoInfo(c.convo) })
                }
            }
        }
        if (discoverable.isNotEmpty()) {
            item { SectionTitle("Kanały w pobliżu", Modifier.padding(start = 8.dp)) }
            items(discoverable, key = { "seen:" + it.name }) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCard).clickable {
                        if (s.secured) vm.sheet = Sheet.NewChat else vm.joinChannel(s.name, null)
                    }.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar("#" + s.name, Channel.convoOf(s.name), size = 46.dp, icon = if (s.secured) Icons.Rounded.Lock else Icons.Rounded.Tag)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("#" + s.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (s.secured) "Chroniony hasłem · " else "") + "${s.count} " + plural(s.count, "wiadomość", "wiadomości", "wiadomości") + " w sieci",
                            color = Palette.TextDim,
                            fontSize = 13.sp,
                        )
                    }
                    Text("Dołącz", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clip(Pill).background(Color.White).padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
        if (convos.size <= 1 && messages.isEmpty()) {
            item { EmptyChats(vm) }
        }
    }
}

private val RoundedCard = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

@Composable
private fun ConvoRow(c: ConvoSummary, myId: String, now: Long, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.isChannel) {
            Avatar(c.title, c.idForColor, size = 50.dp, icon = if (c.secured) Icons.Rounded.Lock else Icons.Rounded.Tag)
        } else {
            Avatar(c.title, c.idForColor, size = 50.dp, online = c.online)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.pinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.PushPin, "Przypięta", tint = Palette.TextDim, modifier = Modifier.size(13.dp))
                }
                if (c.muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.NotificationsOff, "Wyciszony", tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                if (c.last != null) Text(shortWhen(c.last.ts, now), color = if (c.unread > 0) Color.White else Palette.TextFaint, fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val last = c.last
                val preview = when {
                    last == null -> if (c.isChannel) "Kanał gotowy – napisz pierwszą wiadomość" else ""
                    else -> {
                        val who = when {
                            last.from == myId -> "Ty: "
                            c.isChannel -> last.fromName + ": "
                            else -> ""
                        }
                        val body = when {
                            last.sos -> "🆘 " + last.text
                            last.hasLocation && last.text.isBlank() -> "📍 Pozycja"
                            last.hasLocation -> "📍 " + last.text
                            else -> last.text
                        }
                        who + body
                    }
                }
                Text(
                    preview,
                    color = if (c.last?.sos == true) Palette.Sos else if (c.unread > 0) Color.White else Palette.TextDim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (c.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    CountBadge(c.unread)
                }
            }
        }
    }
}

@Composable
private fun EmptyChats(vm: MeshViewModel) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).panel().padding(20.dp)) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text("Pierwsze kroki", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(
            "Kanał #ogólny widzą wszyscy w zasięgu sieci. Utwórz własny kanał (np. #schronisko albo #ekipa z hasłem) lub napisz prywatnie do osoby z zakładki „W pobliżu”.",
            color = Palette.TextDim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Nowy kanał", { vm.sheet = Sheet.NewChat }, icon = Icons.Rounded.Add)
            PillButton("W pobliżu", { vm.tab = Tab.NEARBY }, primary = false, icon = Icons.Rounded.Radar)
        }
    }
}

// endregion

// region W pobliżu

@Composable
private fun NearbyTab(vm: MeshViewModel) {
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val links by vm.core.transport.links.collectAsStateWithLifecycle()
    val state by vm.core.transport.state.collectAsStateWithLifecycle()
    val settings by vm.core.settings.collectAsStateWithLifecycle()
    val nearbyCount by vm.core.transport.nearby.collectAsStateWithLifecycle()
    val now = rememberNow(10_000)
    val linked = remember(links) { links.values.mapTo(HashSet()) { it.peerId } }
    val sorted = remember(peers, linked, now) {
        peers.values.sortedWith(
            compareByDescending<Peer> { it.id in linked }
                .thenByDescending { it.isOnline(now) }
                .thenBy { it.hops }
                .thenByDescending { it.lastSeen },
        )
    }
    val online = sorted.filter { it.id in linked || it.isOnline(now) }
    val offline = sorted - online.toSet()
    val direct = online.count { it.id in linked }

    LaunchedEffect(Unit) { vm.refreshLocation() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
        item {
            Column(Modifier.fillMaxWidth().panel().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(0.72f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Radar(direct, online.size - direct, settings.meshEnabled && state == LinkState.On, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(direct.toString(), "bezpośrednio")
                    Stat((online.size - direct).toString(), "przez innych")
                    Stat(nearbyCount.toString(), "wykryte radiem")
                }
            }
        }
        item { MyPositionCard(vm) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Mapa okolicy", { vm.openMapScreen() }, icon = Icons.Rounded.Map, primary = false, modifier = Modifier.weight(1f))
                PillButton("Latarka", { vm.openBeacon() }, icon = Icons.Rounded.FlashOn, primary = false, modifier = Modifier.weight(1f))
            }
        }
        item { SectionTitle("W zasięgu sieci · ${online.size}") }
        if (online.isEmpty()) {
            item {
                Text(
                    if (settings.meshEnabled) "Nikogo jeszcze nie widać. Urządzenia z OpenMesh łączą się same, gdy są w zasięgu (zwykle 10–100 m, zależnie od terenu)." else "Włącz sieć, aby zobaczyć osoby w pobliżu.",
                    color = Palette.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        items(online, key = { it.id }) { p -> PeerRow(vm, p, p.id in linked, now) }
        if (offline.isNotEmpty()) {
            item { SectionTitle("Wcześniej widziani · ${offline.size}") }
            items(offline, key = { it.id }) { p -> PeerRow(vm, p, false, now) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun MyPositionCard(vm: MeshViewModel) {
    val p = vm.myLocation
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).panel().clickable(enabled = p != null) {
            p?.let { vm.copy(Geo.formatCoords(it.lat, it.lon) + " (" + Geo.formatDms(it.lat, it.lon) + ")", "Pozycja") }
        }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.CardHigher), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MyLocation, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Moja pozycja", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    p != null -> Geo.formatCoords(p.lat, p.lon) + (p.accuracy?.let { " · ±${it.toInt()} m" } ?: "")
                    vm.locating -> "Ustalanie pozycji z GPS…"
                    else -> "Nieznana – odśwież, aby zobaczyć odległości do innych"
                },
                color = Palette.TextDim,
                fontSize = 13.sp,
            )
        }
        if (vm.locating) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            IconCircle(Icons.Rounded.Refresh, "Odśwież pozycję", { vm.refreshLocation() }, size = 38.dp)
        }
    }
}

@Composable
private fun PeerRow(vm: MeshViewModel, p: Peer, linked: Boolean, now: Long) {
    val online = linked || p.isOnline(now)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCard).clickable { vm.sheet = Sheet.PeerInfo(p.id) }.padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, p.id, size = 48.dp, online = online)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = if (p.blocked) Palette.TextFaint else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.blocked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Block, "Zablokowany", tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
                }
            }
            if (p.status.isNotBlank()) {
                Text(p.status, color = Palette.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(
                    if (linked) "bezpośrednio" else if (online) hopsLabel(p.hops) else ago(p.lastSeen, now),
                    icon = if (linked) Icons.Rounded.Hub else null,
                    color = if (linked) Palette.Ink else Palette.TextDim,
                    background = if (linked) Color.White else Palette.CardHigher,
                )
                distanceTo(vm.myLocation, p)?.let { Tag(it, icon = Icons.Rounded.Explore) }
                p.battery?.let { Tag("$it%", icon = Icons.Rounded.BatteryStd, color = if (it <= 15) Palette.Sos else Palette.TextDim) }
            }
        }
        IconCircle(Icons.Rounded.ChatBubble, "Napisz", { vm.openPeer(p.id) }, size = 40.dp)
    }
}

fun distanceTo(me: GeoPoint?, p: Peer): String? {
    if (me == null || p.lat == null || p.lon == null) return null
    val other = GeoPoint(p.lat, p.lon)
    return Geo.formatDistance(Geo.distance(me, other)) + " " + Geo.direction(Geo.bearing(me, other))
}

// endregion
