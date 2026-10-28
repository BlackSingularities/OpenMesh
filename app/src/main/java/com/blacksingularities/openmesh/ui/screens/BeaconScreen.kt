package com.blacksingularities.openmesh.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blacksingularities.openmesh.mesh.BeaconPattern
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.theme.Card
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.RowDivider
import com.blacksingularities.openmesh.ui.theme.SectionTitle
import com.blacksingularities.openmesh.ui.theme.SettingRow
import com.blacksingularities.openmesh.ui.theme.SettingSwitch
import com.blacksingularities.openmesh.ui.theme.SettingsGroup
import com.blacksingularities.openmesh.util.Beacon

/**
 * Latarka alarmowa: ostatnia warstwa sygnalizacji, gdy nikogo nie ma już w zasięgu sieci.
 * Miga diodą (i opcjonalnie całym ekranem) wzorem rozpoznawanym przez ratowników.
 */
@Composable
fun BeaconScreen(vm: MeshViewModel) {
    val settings by vm.core.settings.collectAsStateWithLifecycle()
    val running by Beacon.running.collectAsStateWithLifecycle()
    val lit by Beacon.lit.collectAsStateWithLifecycle()
    val hasTorch = vm.beaconAvailable
    val view = LocalView.current

    // Przy włączonej latarce ekran nie gaśnie – inaczej sygnał urwałby się po kilkunastu sekundach.
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    val flashing = running && settings.beaconScreen && lit
    val background by animateColorAsState(if (flashing) Color.White else Palette.Ink, tween(60), label = "flash")

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopBar(
            title = "Latarka alarmowa",
            subtitle = if (running) "sygnał nadawany" else "wyłączona",
            onBack = { vm.back() },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(184.dp)
                        .clip(CircleShape)
                        .background(if (running) Palette.Sos else Palette.CardHigh)
                        .clickable { vm.toggleBeacon() },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (running) Icons.Rounded.FlashOff else Icons.Rounded.FlashOn,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(46.dp),
                        )
                        Text(
                            if (running) "ZATRZYMAJ" else "NADAWAJ",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            Card {
                Text(Beacon.label(settings.beaconPattern), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    Beacon.description(settings.beaconPattern),
                    color = Palette.TextDim,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionTitle("Wzór sygnału")
            SettingsGroup {
                BeaconPattern.entries.forEachIndexed { i, pattern ->
                    if (i > 0) RowDivider()
                    val full = Beacon.description(pattern)
                    SettingRow(
                        title = Beacon.label(pattern),
                        subtitle = if (full.length > 72) full.take(72).trimEnd() + "…" else full,
                        onClick = { vm.setBeaconPattern(pattern) },
                    ) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (settings.beaconPattern == pattern) Color.White else Palette.CardHigher),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (settings.beaconPattern == pattern) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Ink))
                            }
                        }
                    }
                }
            }

            SectionTitle("Jak sygnalizować")
            SettingsGroup {
                SettingSwitch(
                    "Miganie ekranem",
                    settings.beaconScreen,
                    { on -> vm.core.updateSettings { it.copy(beaconScreen = on) } },
                    subtitle = "Cały ekran błyska razem z diodą – widoczne z daleka także bez latarki.",
                )
                RowDivider()
                SettingRow(
                    title = "Dioda w telefonie",
                    subtitle = if (hasTorch) "Dostępna – migocze razem z sygnałem." else "Ten telefon nie ma diody; zostaje miganie ekranem.",
                ) {
                    Text(if (hasTorch) "jest" else "brak", color = if (hasTorch) Palette.Online else Palette.TextFaint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
                Text(
                    "Latarka zużywa prąd – przy niskim stanie baterii wybierz sygnał alpejski albo SOS, które świecą tylko przez chwilę. Najpierw wyślij wezwanie SOS przez sieć: dojdzie dalej niż światło.",
                    color = Palette.TextFaint,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
