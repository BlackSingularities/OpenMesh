package com.blacksingularities.openmesh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.BuildConfig
import com.blacksingularities.openmesh.mesh.Crypto
import com.blacksingularities.openmesh.mesh.Topology
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.Sheet
import com.blacksingularities.openmesh.ui.theme.Avatar
import com.blacksingularities.openmesh.ui.theme.Field
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.RowDivider
import com.blacksingularities.openmesh.ui.theme.SectionTitle
import com.blacksingularities.openmesh.ui.theme.Segmented
import com.blacksingularities.openmesh.ui.theme.SettingRow
import com.blacksingularities.openmesh.ui.theme.SettingSlider
import com.blacksingularities.openmesh.ui.theme.SettingSwitch
import com.blacksingularities.openmesh.ui.theme.SettingsGroup
import com.blacksingularities.openmesh.ui.theme.panel
import com.blacksingularities.openmesh.update.UpdateState
import com.blacksingularities.openmesh.util.Permissions

@Composable
fun SettingsTab(vm: MeshViewModel) {
    val ctx = LocalContext.current
    val s by vm.core.settings.collectAsStateWithLifecycle()
    val stats by vm.core.stats.collectAsStateWithLifecycle()
    var nick by remember { mutableStateOf(s.nick) }
    var status by remember { mutableStateOf(s.status) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val tick = vm.permissionsTick

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)) {
        // Profil
        item {
            Column(Modifier.fillMaxWidth().panel().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(nick.ifBlank { "?" }, vm.core.myId, size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nick.ifBlank { "Bez nicku" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("ID " + vm.core.myId.take(8).uppercase(), color = Palette.TextDim, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Field(nick, { nick = it.take(24) }, "Nick", Modifier.fillMaxWidth(), onAction = { vm.core.updateSettings { it.copy(nick = nick.trim()) } })
                Spacer(Modifier.height(8.dp))
                Field(status, { status = it.take(60) }, "Status, np. „Schronisko Murowaniec, do 18:00”", Modifier.fillMaxWidth(), onAction = { vm.core.updateSettings { it.copy(status = status.trim()) } })
                if (nick.trim() != s.nick || status.trim() != s.status) {
                    PillButton(
                        "Zapisz profil",
                        {
                            vm.core.updateSettings { it.copy(nick = nick.trim(), status = status.trim()) }
                            vm.toast("Zapisano – inni zobaczą zmiany przy następnym kontakcie")
                        },
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }

        item { SectionTitle("Sieć") }
        item {
            SettingsGroup {
                SettingSwitch(
                    "Sieć mesh",
                    s.meshEnabled,
                    { vm.setMeshEnabled(it) },
                    subtitle = if (s.meshEnabled) "Wykrywanie, łączenie i przekazywanie wiadomości" else "Radio wyłączone – nic nie odbierasz",
                    icon = Icons.Rounded.PowerSettingsNew,
                )
                RowDivider()
                SettingSwitch(
                    "Przekazuj wiadomości innych",
                    s.relay,
                    { v -> vm.core.updateSettings { it.copy(relay = v) } },
                    subtitle = "Twój telefon jest węzłem sieci – dzięki temu wiadomości docierają dalej. Zużywa nieco więcej baterii.",
                    icon = Icons.Rounded.Route,
                )
                RowDivider()
                SettingSlider(
                    "Zasięg wiadomości",
                    s.ttl,
                    1..10,
                    { v -> vm.core.updateSettings { it.copy(ttl = v) } },
                    label = { "$it skoków" },
                    subtitle = "Ile razy wiadomość może być przekazana dalej. SOS zawsze ma maksimum.",
                    icon = Icons.Rounded.Hub,
                )
                RowDivider()
                SettingSlider(
                    "Przechowywanie do przekazania",
                    s.retentionHours,
                    1..72,
                    { v -> vm.core.updateSettings { it.copy(retentionHours = v) } },
                    label = { "$it h" },
                    subtitle = "Jak długo wiadomości czekają na nowych sąsiadów (store-and-forward).",
                    icon = Icons.Rounded.Schedule,
                )
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Układ sieci", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (s.topology == Topology.CLUSTER) "Klaster: każdy łączy się z każdym – najlepszy dla grup w ruchu." else "Gwiazda: jeden telefon jako centrum, szybsze Wi‑Fi Direct – np. obóz, schron.",
                        color = Palette.TextDim,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    Segmented(
                        listOf(Topology.CLUSTER to "Klaster", Topology.STAR to "Gwiazda"),
                        s.topology,
                        { v -> vm.core.updateSettings { it.copy(topology = v) } },
                    )
                }
                RowDivider()
                SettingSwitch(
                    "Uruchamiaj po starcie telefonu",
                    s.autoStart,
                    { v -> vm.core.updateSettings { it.copy(autoStart = v) } },
                    icon = Icons.Rounded.RestartAlt,
                )
                RowDivider()
                val ignoring = Permissions.ignoringBatteryOptimizations(ctx)
                SettingRow(
                    "Działanie w tle",
                    subtitle = if (ignoring) "Bez ograniczeń baterii – sieć działa przy wygaszonym ekranie" else "System może usypiać sieć. Dotknij i wybierz „Bez ograniczeń” dla OpenMesh.",
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = if (ignoring) Palette.Online else Palette.Warn,
                    onClick = { Permissions.openBatterySettings(ctx) },
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp)) }
                RowDivider()
                SettingRow(
                    "Uprawnienia",
                    subtitle = if (Permissions.meshGranted(ctx)) "Bluetooth i urządzenia w pobliżu – nadane" else "Brakuje uprawnień – sieć nie działa",
                    icon = Icons.Rounded.Bluetooth,
                    iconTint = if (Permissions.meshGranted(ctx)) Color.White else Palette.Sos,
                    onClick = { Permissions.openAppSettings(ctx) },
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp)) }
            }
        }

        item { SectionTitle("Powiadomienia") }
        item {
            SettingsGroup {
                SettingSwitch("Powiadomienia o wiadomościach", s.notify, { v -> vm.core.updateSettings { it.copy(notify = v) } }, icon = Icons.Rounded.Notifications)
                RowDivider()
                SettingSwitch("Wibracja", s.vibrate, { v -> vm.core.updateSettings { it.copy(vibrate = v) } }, icon = Icons.Rounded.Vibration)
                RowDivider()
                SettingSwitch(
                    "Alarm SOS",
                    s.sosAlarm,
                    { v -> vm.core.updateSettings { it.copy(sosAlarm = v) } },
                    subtitle = "Głośny dźwięk i długa wibracja, gdy ktoś w sieci wzywa pomocy",
                    icon = Icons.Rounded.Sos,
                )
                RowDivider()
                SettingSwitch(
                    "Udostępniaj poziom baterii",
                    s.shareBattery,
                    { v -> vm.core.updateSettings { it.copy(shareBattery = v) } },
                    subtitle = "Pomaga grupie ocenić, kto może pełnić rolę węzła",
                    icon = Icons.Rounded.BatteryStd,
                )
            }
        }

        item { SectionTitle("Prywatność i dane") }
        item {
            SettingsGroup {
                SettingRow(
                    "Moja tożsamość",
                    subtitle = "Odcisk klucza " + Crypto.fingerprint(vm.core.myPub).take(14) + "…",
                    icon = Icons.Rounded.Fingerprint,
                    onClick = { vm.sheet = Sheet.Identity },
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Palette.TextFaint, modifier = Modifier.size(18.dp)) }
                RowDivider()
                SettingRow(
                    if (confirmClear) "Dotknij ponownie, aby usunąć" else "Wyczyść historię rozmów",
                    subtitle = "Usuwa wiadomości z tego telefonu",
                    icon = Icons.Rounded.DeleteSweep,
                    titleColor = if (confirmClear) Palette.Sos else Color.White,
                    onClick = {
                        if (confirmClear) {
                            vm.core.clearHistory()
                            vm.toast("Historia usunięta")
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    if (confirmWipe) "Dotknij ponownie – usunie WSZYSTKO" else "Usuń wszystkie dane",
                    subtitle = "Tryb paniki: klucze, historia, kanały i ustawienia znikają natychmiast",
                    icon = Icons.Rounded.DeleteForever,
                    iconTint = Palette.Sos,
                    titleColor = Palette.Sos,
                    onClick = { if (confirmWipe) vm.wipeAll() else confirmWipe = true },
                )
            }
        }

        item { SectionTitle("Aktualizacje") }
        item {
            SettingsGroup {
                SettingSwitch(
                    "Sprawdzaj automatycznie",
                    s.autoUpdate,
                    { v -> vm.core.updateSettings { it.copy(autoUpdate = v) } },
                    subtitle = "Gdy telefon ma internet – co kilka godzin, z GitHub Releases",
                    icon = Icons.Rounded.SystemUpdate,
                )
                RowDivider()
                SettingSwitch(
                    "Instaluj automatycznie",
                    s.autoInstall,
                    { v -> vm.core.updateSettings { it.copy(autoInstall = v) } },
                    subtitle = "Pobiera i instaluje nową wersję od razu (Android 12+: bez pytania)",
                    icon = Icons.Rounded.Download,
                    enabled = s.autoUpdate,
                )
                RowDivider()
                val u = vm.update
                SettingRow(
                    "Wersja " + BuildConfig.VERSION_NAME,
                    subtitle = when (u) {
                        UpdateState.Checking -> "Sprawdzanie…"
                        UpdateState.UpToDate -> "Masz najnowszą wersję"
                        is UpdateState.Available -> "Dostępna wersja " + u.info.version
                        is UpdateState.Downloading -> "Pobieranie " + (u.progress * 100).toInt() + "%"
                        is UpdateState.Ready -> "Gotowa do instalacji: " + u.info.version
                        is UpdateState.Installing -> "Instalowanie…"
                        is UpdateState.Failed -> u.message
                        UpdateState.Idle -> "Dotknij, aby sprawdzić aktualizacje"
                    },
                    icon = Icons.Rounded.Info,
                    onClick = {
                        if (u is UpdateState.Available || u is UpdateState.Ready) {
                            vm.updateBannerHidden = false
                            vm.updateNow()
                        } else {
                            vm.checkUpdate(manual = true)
                        }
                    },
                ) {
                    if (u == UpdateState.Checking) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }

        item { SectionTitle("Statystyki") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Wysłane", stats.sent, Modifier.weight(1f))
                StatTile("Odebrane", stats.received, Modifier.weight(1f))
                StatTile("Przekazane", stats.relayed, Modifier.weight(1f))
            }
        }
        item {
            Text(
                "OpenMesh " + BuildConfig.VERSION_NAME + " · otwarte oprogramowanie (MIT)\nPołączeń: " + stats.connections + " · odrzucone pakiety: " + stats.rejected,
                color = Palette.TextFaint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 8.dp),
            )
        }
        item {
            PillButton("O aplikacji i bezpieczeństwie", { vm.sheet = Sheet.About }, Modifier.fillMaxWidth().padding(top = 12.dp), primary = false, icon = Icons.Rounded.Info)
        }
    }
}

@Composable
private fun StatTile(label: String, value: Long, modifier: Modifier) {
    Column(modifier.panel().padding(vertical = 14.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.TextDim, fontSize = 12.sp)
    }
}
