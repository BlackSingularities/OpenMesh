package com.blacksingularities.openmesh.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openmesh.ui.MeshViewModel
import com.blacksingularities.openmesh.ui.theme.Field
import com.blacksingularities.openmesh.ui.theme.Palette
import com.blacksingularities.openmesh.ui.theme.PillButton
import com.blacksingularities.openmesh.ui.theme.panel
import com.blacksingularities.openmesh.util.Permissions

@Composable
fun OnboardingScreen(vm: MeshViewModel) {
    var step by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        AnimatedContent(step, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "onb") { s ->
            if (s == 0) Welcome { step = 1 } else Setup(vm)
        }
    }
}

@Composable
private fun Welcome(next: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(48.dp))
            MeshMark(Modifier.size(84.dp))
            Spacer(Modifier.height(24.dp))
            Text("OpenMesh", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(
                "Czat bez internetu. Telefony łączą się bezpośrednio przez Bluetooth i Wi‑Fi Direct, a wiadomości przeskakują od urządzenia do urządzenia.",
                color = Palette.TextDim,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(28.dp))
            Feature(Icons.Rounded.WifiOff, "Działa bez zasięgu", "W górach, na festiwalu, w samolocie i podczas awarii sieci.")
            Feature(Icons.Rounded.Hub, "Sieć mesh", "Każdy telefon przekazuje wiadomości dalej. Gdy nikogo nie ma w pobliżu, wiadomość czeka i wyjdzie przy pierwszym kontakcie.")
            Feature(Icons.Rounded.Lock, "Szyfrowanie", "Rozmowy prywatne szyfrowane end-to-end, kanały z hasłem, każda wiadomość podpisana.")
            Feature(Icons.Rounded.Sos, "SOS z pozycją GPS", "Wezwanie pomocy z maksymalnym zasięgiem – GPS działa bez internetu.")
        }
        PillButton("Dalej", next, Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
private fun Setup(vm: MeshViewModel) {
    val ctx = LocalContext.current
    var nick by remember { mutableStateOf(vm.core.settings.value.nick) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Permissions.meshGranted(ctx)) {
            vm.completeOnboarding(nick)
        } else {
            denied = true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(40.dp))
            Text("Jak Cię widać w sieci?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Nick widzą wszyscy w pobliżu. Możesz go zmienić później.", color = Palette.TextDim, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            Field(
                nick,
                { nick = it.take(24) },
                "Np. Ania z Doliny Pięciu Stawów",
                Modifier.fillMaxWidth(),
                leading = Icons.Rounded.Person,
                imeAction = ImeAction.Done,
                capitalization = KeyboardCapitalization.Words,
            )
            Spacer(Modifier.height(30.dp))
            Text("Uprawnienia", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Feature(Icons.Rounded.Bluetooth, "Urządzenia w pobliżu", "Bluetooth i Wi‑Fi do wykrywania telefonów i łączenia się z nimi. Wymagane.")
            Feature(Icons.Rounded.MyLocation, "Lokalizacja", "Udostępnianie pozycji i SOS. Pozycja wychodzi tylko, gdy ją wyślesz.")
            Feature(Icons.Rounded.Notifications, "Powiadomienia", "Nowe wiadomości i alarmy SOS, także gdy aplikacja jest w tle.")
            if (denied) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).panel(color = Palette.SosDeep).padding(16.dp)) {
                    Text("Bez uprawnienia „Urządzenia w pobliżu” sieć nie zadziała.", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nadaj je w ustawieniach systemu i wróć do aplikacji.",
                        color = Palette.TextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    PillButton("Otwórz ustawienia", { Permissions.openAppSettings(ctx) }, primary = false)
                }
            }
            Text(
                "Tip: w trybie samolotowym możesz włączyć z powrotem Bluetooth i Wi‑Fi – OpenMesh będzie działać.",
                color = Palette.TextFaint,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (denied && Permissions.meshGranted(ctx)) {
                PillButton("Zaczynamy", { vm.completeOnboarding(nick) }, Modifier.weight(1f))
            } else {
                PillButton("Nadaj uprawnienia i zacznij", { launcher.launch(Permissions.all().toTypedArray()) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Palette.CardHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Palette.TextDim, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
