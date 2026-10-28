package com.blacksingularities.openmesh.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.BuildConfig
import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.Crypto
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.MsgStatus
import com.blacksingularities.openmesh.mesh.PUBLIC_CHANNEL
import com.blacksingularities.openmesh.mesh.isDm
import com.blacksingularities.openmesh.mesh.peerOf
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.Sheet
import com.blacksingularities.openmesh.ui.ago
import com.blacksingularities.openmesh.ui.hopsLabel
import com.blacksingularities.openmesh.ui.isOnline
import com.blacksingularities.openmesh.ui.theme.Avatar
import com.blacksingularities.openmesh.ui.theme.Field
import com.blacksingularities.openmesh.ui.theme.Mono
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.Pill
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.RowDivider
import com.blacksingularities.openmesh.ui.theme.Segmented
import com.blacksingularities.openmesh.ui.theme.SettingRow
import com.blacksingularities.openmesh.ui.theme.SettingSwitch
import com.blacksingularities.openmesh.ui.theme.SettingsGroup
import com.blacksingularities.openmesh.ui.theme.Tag
import com.blacksingularities.openmesh.ui.theme.panel
import com.blacksingularities.openmesh.util.Geo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Sheets(vm: MeshViewModel) {
    val sheet = vm.sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null },
        sheetState = state,
        containerColor = Palette.Panel,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(Pill).background(Palette.CardHigher))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
        ) {
            when (sheet) {
                Sheet.NewChat -> NewChatSheet(vm)
                Sheet.Sos -> SosSheet(vm)
                is Sheet.PeerInfo -> PeerSheet(vm, sheet.peerId)
                is Sheet.ConvoInfo -> ConvoSheet(vm, sheet.convo)
                is Sheet.MessageActions -> MessageSheet(vm, sheet.message)
                Sheet.Identity -> IdentitySheet(vm)
                Sheet.About -> AboutSheet()
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, subtitle: String? = null) {
    Text(text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(16.dp))
}

// region Nowa rozmowa

private val CHANNEL_IDEAS = listOf("góry", "schronisko", "festiwal", "scena", "awaria", "rodzina", "ekipa", "lot", "obóz", "pomoc")

@Composable
private fun ColumnScope.NewChatSheet(vm: MeshViewModel) {
    var mode by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var secured by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val seen by vm.core.seenChannels.collectAsStateWithLifecycle()

    SheetTitle("Nowa rozmowa")
    Segmented(listOf(0 to "Kanał", 1 to "Prywatna"), mode, { mode = it })
    Spacer(Modifier.height(16.dp))
    if (mode == 0) {
        Field(name, { name = it.take(32) }, "Nazwa kanału, np. schronisko", Modifier.fillMaxWidth(), leading = Icons.Rounded.Tag, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (seen.keys.toList() + CHANNEL_IDEAS).distinct().take(14).forEach { idea ->
                val isSeen = seen.containsKey(idea)
                Text(
                    "#$idea",
                    color = if (isSeen) Palette.Ink else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isSeen) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(Pill).background(if (isSeen) Color.White else Palette.CardHigh).clickable {
                        name = idea
                        if (seen[idea]?.secured == true) secured = true
                    }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            SettingSwitch(
                "Chroniony hasłem",
                secured,
                { secured = it },
                subtitle = "Treść szyfrowana – czytają ją tylko osoby znające hasło",
                icon = Icons.Rounded.Lock,
            )
        }
        if (secured) {
            Spacer(Modifier.height(10.dp))
            Field(password, { password = it.take(64) }, "Hasło kanału", Modifier.fillMaxWidth(), password = true, leading = Icons.Rounded.VpnKey, capitalization = KeyboardCapitalization.None)
            Text("Przekaż hasło ustnie – kto ma inne hasło, nie zobaczy wiadomości.", color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        }
        PillButton(
            "Dołącz do #" + Channel.normalize(name).ifEmpty { "…" },
            { vm.joinChannel(name, if (secured) password else null) },
            Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = Channel.normalize(name).isNotEmpty() && (!secured || password.length >= 4),
        )
    } else {
        val now = System.currentTimeMillis()
        val list = peers.values.filter { !it.blocked }.sortedByDescending { it.lastSeen }
        if (list.isEmpty()) {
            Text(
                "Nikogo jeszcze nie znasz. Osoby pojawią się tutaj, gdy ich telefon z OpenMesh znajdzie się w zasięgu sieci.",
                color = Palette.TextDim,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        list.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(Pill).clickable { vm.openPeer(p.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.name, p.id, size = 42.dp, online = p.isOnline(now))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (p.isOnline(now)) hopsLabel(p.hops) else ago(p.lastSeen, now), color = Palette.TextDim, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChatBubble, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// endregion

// region SOS

private val SOS_REASONS = listOf("Wypadek", "Zgubiłem się", "Potrzebuję lekarza", "Uraz – nie mogę iść", "Hipotermia", "Brak wody", "Zagrożenie", "Pożar")

@Composable
private fun ColumnScope.SosSheet(vm: MeshViewModel) {
    var text by remember { mutableStateOf("") }
    var withLocation by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    Text("SOS", color = Palette.Sos, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    Text(
        "Wezwanie trafi do wszystkich telefonów z OpenMesh w zasięgu sieci – także przez pośredników – z maksymalnym zasięgiem i alarmem dźwiękowym.",
        color = Palette.TextDim,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SOS_REASONS.forEach { r ->
            val on = text.contains(r)
            Text(
                r,
                color = if (on) Color.White else Palette.TextDim,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(Pill).background(if (on) Palette.Sos else Palette.CardHigh).clickable {
                    text = if (on) text.replace(r, "").replace(Regex(",\\s*,"), ",").trim(' ', ',') else listOf(text, r).filter { it.isNotBlank() }.joinToString(", ")
                }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Field(text, { text = it.take(300) }, "Opis: co się stało, ile osób, gdzie jesteście", Modifier.fillMaxWidth(), singleLine = false, maxLines = 4, imeAction = ImeAction.Default)
    Spacer(Modifier.height(10.dp))
    SettingsGroup {
        SettingSwitch("Dołącz pozycję GPS", withLocation, { withLocation = it }, subtitle = "GPS działa bez internetu; najlepiej pod otwartym niebem", icon = Icons.Rounded.MyLocation)
    }
    Spacer(Modifier.height(18.dp))
    // Przytrzymanie 1,5 s chroni przed przypadkowym wysłaniem.
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(Pill)
            .background(Palette.SosDeep)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val job = scope.launch {
                        progress.animateTo(1f, tween(1500, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.sos(text, withLocation)
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(progress.value).height(64.dp).background(Palette.Sos))
        Text(
            if (progress.value > 0f) "Trzymaj…" else "Przytrzymaj, aby wysłać SOS",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        "W Polsce numer alarmowy to 112, w górach GOPR/TOPR: 985 lub 601 100 300. Gdy masz choć minimalny zasięg komórkowy – dzwoń.",
        color = Palette.TextFaint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
}

// endregion

// region Osoba

@Composable
private fun ColumnScope.PeerSheet(vm: MeshViewModel, peerId: String) {
    val peers by vm.core.peers.collectAsStateWithLifecycle()
    val links by vm.core.transport.links.collectAsStateWithLifecycle()
    val p = peers[peerId]
    if (p == null) {
        SheetTitle("Nieznana osoba")
        return
    }
    val now = System.currentTimeMillis()
    val linked = links.values.any { it.peerId == p.id }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name, p.id, size = 64.dp, online = linked || p.isOnline(now))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (linked) "bezpośrednio w zasięgu" else if (p.isOnline(now)) hopsLabel(p.hops) else "widziany " + ago(p.lastSeen, now),
                color = Palette.TextDim,
                fontSize = 14.sp,
            )
        }
    }
    if (p.status.isNotBlank()) {
        Text("„" + p.status + "”", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        p.battery?.let { Tag("Bateria $it%", color = if (it <= 15) Palette.Sos else Palette.TextDim) }
        Tag("ID " + p.id.take(8).uppercase())
        if (p.blocked) Tag("Zablokowany", color = Palette.Sos)
    }
    Spacer(Modifier.height(16.dp))
    SettingsGroup {
        if (p.lat != null && p.lon != null) {
            SettingRow(
                "Ostatnia pozycja",
                subtitle = Geo.formatCoords(p.lat, p.lon) + (p.locTs?.let { " · " + ago(it, now) } ?: "") + (distanceTo(vm.myLocation, p)?.let { "\n$it od Ciebie" } ?: ""),
                icon = Icons.Rounded.Map,
                onClick = { vm.openMap(p.lat, p.lon, p.name) },
            )
            RowDivider()
        }
        SettingRow(
            "Odcisk klucza",
            subtitle = Crypto.fingerprint(p.pub),
            icon = Icons.Rounded.VpnKey,
            onClick = { vm.copy(Crypto.fingerprint(p.pub), "Odcisk klucza") },
        )
    }
    Text(
        "Porównaj odcisk na obu telefonach – jeśli się zgadza, rozmawiasz z właściwą osobą.",
        color = Palette.TextFaint,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
    PillButton("Napisz wiadomość", { vm.openPeer(p.id) }, Modifier.fillMaxWidth().padding(top = 16.dp), icon = Icons.Rounded.ChatBubble)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            if (p.blocked) "Odblokuj" else "Zablokuj",
            { vm.core.setBlocked(p.id, !p.blocked) },
            Modifier.weight(1f),
            primary = false,
            icon = Icons.Rounded.Block,
        )
        PillButton(
            "Zapomnij",
            {
                vm.core.forgetPeer(p.id)
                vm.sheet = null
            },
            Modifier.weight(1f),
            primary = false,
            icon = Icons.Rounded.PersonRemove,
        )
    }
}

// endregion

// region Rozmowa

@Composable
private fun ColumnScope.ConvoSheet(vm: MeshViewModel, convo: String) {
    val channels by vm.core.channels.collectAsStateWithLifecycle()
    val messages by vm.core.messages.collectAsStateWithLifecycle()
    val count = messages.count { it.convo == convo }
    var confirm by remember { mutableStateOf(false) }
    if (isDm(convo)) {
        val peer = vm.core.peers.collectAsStateWithLifecycle().value[peerOf(convo)]
        SheetTitle(peer?.name ?: "Rozmowa prywatna", "Szyfrowanie end-to-end (ECDH P-256 + AES-256-GCM). $count w historii.")
        if (peer != null) {
            PillButton("Profil i odcisk klucza", { vm.sheet = Sheet.PeerInfo(peer.id) }, Modifier.fillMaxWidth(), primary = false, icon = Icons.Rounded.VpnKey)
            Spacer(Modifier.height(10.dp))
        }
    } else {
        val ch = channels.firstOrNull { it.key == convo }
        SheetTitle(
            "#" + (ch?.name ?: ""),
            if (ch?.secured == true) "Kanał chroniony hasłem – treść szyfrowana AES-256-GCM. $count w historii." else "Kanał jawny – czytają go wszyscy, którzy do niego dołączą. $count w historii.",
        )
        if (ch != null) {
            SettingsGroup {
                SettingSwitch(
                    "Wycisz powiadomienia",
                    ch.muted,
                    { vm.core.setMuted(convo, it) },
                    icon = if (ch.muted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                )
                if (ch.secured) {
                    RowDivider()
                    SettingRow("Kopiuj zaproszenie", subtitle = "Nazwa i hasło kanału do przekazania", icon = Icons.Rounded.ContentCopy, onClick = {
                        vm.copy("Kanał OpenMesh #" + ch.name + ", hasło: " + ch.password)
                    })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    SettingsGroup {
        SettingRow(
            if (confirm) "Dotknij ponownie, aby usunąć" else "Wyczyść historię rozmowy",
            icon = Icons.Rounded.DeleteSweep,
            titleColor = if (confirm) Palette.Sos else Color.White,
            onClick = {
                if (confirm) {
                    vm.core.clearConversation(convo)
                    vm.sheet = null
                } else {
                    confirm = true
                }
            },
        )
        if (!isDm(convo) && convo != Channel.convoOf(PUBLIC_CHANNEL)) {
            RowDivider()
            SettingRow("Opuść kanał", icon = Icons.AutoMirrored.Rounded.ExitToApp, iconTint = Palette.Sos, titleColor = Palette.Sos, onClick = {
                vm.core.leaveChannel(convo)
                vm.sheet = null
                vm.back()
            })
        }
    }
}

// endregion

// region Wiadomość

@Composable
private fun ColumnScope.MessageSheet(vm: MeshViewModel, m: Message) {
    val fmt = remember { SimpleDateFormat("d MMMM yyyy, HH:mm:ss", Locale("pl", "PL")) }
    SheetTitle(if (m.mine) "Twoja wiadomość" else m.fromName)
    if (m.text.isNotBlank()) {
        Text(m.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth().panel().padding(14.dp))
        Spacer(Modifier.height(12.dp))
    }
    val details = buildList {
        add("Wysłano: " + fmt.format(Date(m.ts)))
        if (!m.mine) add("Odebrano: " + fmt.format(Date(m.receivedAt)) + " · " + hopsLabel(m.hops))
        if (m.mine) {
            add(
                "Stan: " + when (m.status) {
                    MsgStatus.QUEUED -> "w kolejce – wyjdzie przy pierwszym połączeniu"
                    MsgStatus.SENT -> "wysłano do sieci"
                    MsgStatus.DELIVERED -> "doręczono odbiorcy"
                },
            )
        }
        if (m.encrypted) add("Szyfrowana")
        if (m.lat != null && m.lon != null) add("Pozycja: " + Geo.formatDms(m.lat, m.lon))
    }
    details.forEach { Text(it, color = Palette.TextDim, fontSize = 13.sp, lineHeight = 19.sp) }
    Spacer(Modifier.height(14.dp))
    SettingsGroup {
        if (m.text.isNotBlank()) {
            ActionRow(Icons.Rounded.ContentCopy, "Kopiuj tekst") {
                vm.copy(m.text)
                vm.sheet = null
            }
            RowDivider()
        }
        if (m.lat != null && m.lon != null) {
            ActionRow(Icons.Rounded.Map, "Pokaż na mapie") { vm.openMap(m.lat, m.lon, m.fromName) }
            RowDivider()
            ActionRow(Icons.Rounded.ContentCopy, "Kopiuj współrzędne") { vm.copy(Geo.formatCoords(m.lat, m.lon) + " (" + Geo.formatDms(m.lat, m.lon) + ")") }
            RowDivider()
        }
        if (m.mine) {
            ActionRow(Icons.Rounded.Refresh, if (m.sos) "Nadaj SOS ponownie" else "Wyślij ponownie") {
                vm.core.resend(m)
                vm.sheet = null
            }
            RowDivider()
        }
        ActionRow(Icons.Rounded.Share, "Udostępnij poza OpenMesh") {
            val loc = if (m.lat != null && m.lon != null) "\n📍 " + Geo.formatCoords(m.lat, m.lon) else ""
            vm.shareOutside(m.fromName + ": " + m.text + loc)
        }
        RowDivider()
        ActionRow(Icons.Rounded.Delete, "Usuń u mnie", danger = true) {
            vm.core.deleteMessage(m.id)
            vm.sheet = null
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, danger: Boolean = false, onClick: () -> Unit) {
    SettingRow(title, icon = icon, iconTint = if (danger) Palette.Sos else Color.White, titleColor = if (danger) Palette.Sos else Color.White, onClick = onClick)
}

// endregion

// region Tożsamość i informacje

@Composable
private fun ColumnScope.IdentitySheet(vm: MeshViewModel) {
    var confirm by remember { mutableStateOf(false) }
    SheetTitle("Moja tożsamość", "Klucz P-256 wygenerowany na tym telefonie. Nie opuszcza urządzenia – w sieci widać tylko klucz publiczny.")
    Text("Odcisk klucza", color = Palette.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(
        Crypto.fingerprint(vm.core.myPub),
        style = Mono.copy(fontSize = 20.sp, lineHeight = 30.sp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).panel().clickable { vm.copy(Crypto.fingerprint(vm.core.myPub)) }.padding(16.dp),
    )
    Text("Identyfikator: " + vm.core.myId, color = Palette.TextDim, style = Mono, modifier = Modifier.padding(top = 10.dp))
    Spacer(Modifier.height(18.dp))
    PillButton(
        if (confirm) "Na pewno? Znajomi zobaczą Cię jako nową osobę" else "Utwórz nową tożsamość",
        {
            if (confirm) {
                vm.core.resetIdentity()
                vm.toast("Nowa tożsamość utworzona")
                vm.sheet = null
            } else {
                confirm = true
            }
        },
        Modifier.fillMaxWidth(),
        primary = false,
        danger = confirm,
    )
}

@Composable
private fun ColumnScope.AboutSheet() {
    SheetTitle("OpenMesh " + BuildConfig.VERSION_NAME, "Otwarty komunikator mesh bez internetu.")
    val points = listOf(
        "Jak to działa" to "Telefony wykrywają się przez Bluetooth / BLE i łączą przez Bluetooth lub Wi‑Fi (Direct, hotspot) – medium dobiera Nearby Connections. Każdy pakiet ma identyfikator i licznik skoków; węzeł przekazuje go dalej tylko raz.",
        "Store-and-forward" to "Ostatnie wiadomości są przechowywane i wysyłane każdemu nowemu sąsiadowi. Osoba przechodząca między grupami zanosi wiadomości dalej.",
        "Bezpieczeństwo" to "Każda wiadomość jest podpisana ECDSA P-256 – nie da się podszyć pod nadawcę. Rozmowy prywatne: ECDH + AES-256-GCM. Kanały z hasłem: PBKDF2 + AES-256-GCM. Kanały jawne i SOS są czytelne dla wszystkich.",
        "Prywatność" to "Brak serwerów, kont i numerów telefonu. Pozycja wychodzi tylko wtedy, gdy ją wyślesz. Nick i klucz publiczny widzą osoby w sieci.",
        "Zasięg" to "Zwykle 10–100 m między telefonami, w otwartym terenie więcej. Każdy dodatkowy telefon wydłuża sieć.",
        "Aktualizacje" to "Gdy telefon ma internet, OpenMesh sprawdza GitHub Releases i aktualizuje się sam.",
    )
    points.forEach { (t, d) ->
        Text(t, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(d, color = Palette.TextDim, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
    }
    Text("github.com/" + BuildConfig.UPDATE_REPO, color = Palette.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
}

// endregion
