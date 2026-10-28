package com.blacksingularities.openmesh.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.blacksingularities.openmesh.data.Attachments
import com.blacksingularities.openmesh.data.Export
import com.blacksingularities.openmesh.mesh
import com.blacksingularities.openmesh.mesh.BeaconPattern
import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.MeshEvent
import com.blacksingularities.openmesh.mesh.MeshService
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.Notifier
import com.blacksingularities.openmesh.mesh.PUBLIC_CHANNEL
import com.blacksingularities.openmesh.mesh.dmConvo
import com.blacksingularities.openmesh.update.UpdateManager
import com.blacksingularities.openmesh.update.UpdateState
import com.blacksingularities.openmesh.update.isNewer
import com.blacksingularities.openmesh.util.Beacon
import com.blacksingularities.openmesh.util.Geo
import com.blacksingularities.openmesh.util.GeoPoint
import com.blacksingularities.openmesh.util.Permissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab { CHATS, NEARBY, SETTINGS }

sealed interface Screen {
    data object Onboarding : Screen
    data object Main : Screen
    data class Chat(val convo: String) : Screen

    /** Szukanie w całej historii: wiadomości, kanały i osoby. */
    data object Search : Screen

    /** Mapa okolicy rysowana z pozycji GPS – bez internetu i bez kafelków map. */
    data object Map : Screen

    /** Latarka alarmowa migająca wzorem SOS. */
    data object Beacon : Screen
}

sealed interface Sheet {
    data object NewChat : Sheet
    data object Sos : Sheet
    data class PeerInfo(val peerId: String) : Sheet
    data class ConvoInfo(val convo: String) : Sheet
    data class MessageActions(val message: Message) : Sheet
    data object Identity : Sheet
    data object About : Sheet
}

class MeshViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val core = app.mesh

    var screen by mutableStateOf<Screen>(if (core.settings.value.onboarded) Screen.Main else Screen.Onboarding)
        private set
    var tab by mutableStateOf(Tab.CHATS)
    var sheet by mutableStateOf<Sheet?>(null)
    var notice by mutableStateOf<String?>(null)
        private set
    var myLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var locating by mutableStateOf(false)
        private set
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var updateBannerHidden by mutableStateOf(false)

    /** Tekst przekazany z innej aplikacji („Udostępnij → OpenMesh”) – trafia do pola wiadomości. */
    var sharedText by mutableStateOf<String?>(null)

    /** Tekst szukania – wspólny dla ekranu wyszukiwania. */
    var query by mutableStateOf("")

    /** Czy lista rozmów pokazuje też archiwum. */
    var showArchived by mutableStateOf(false)

    /** Zmiana stanu uprawnień (po powrocie z ustawień systemu) – odświeża ostrzeżenia. */
    var permissionsTick by mutableStateOf(0)

    private var noticeJob: Job? = null
    private var updateJob: Job? = null

    init {
        viewModelScope.launch {
            core.events.collect { e -> if (e is MeshEvent.Notice) toast(e.text) }
        }
    }

    fun toast(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(3200)
            notice = null
        }
    }

    // region Nawigacja

    fun open(convo: String) {
        screen = Screen.Chat(convo)
        sheet = null
        core.activeConvo = convo
        core.markRead(convo)
        Notifier.cancelConversation(ctx, convo)
    }

    fun openPeer(peerId: String) = open(dmConvo(peerId))

    fun openSearch() {
        query = ""
        sheet = null
        screen = Screen.Search
    }

    fun openMapScreen() {
        sheet = null
        screen = Screen.Map
        refreshLocation()
    }

    fun openBeacon() {
        sheet = null
        screen = Screen.Beacon
    }

    /** Zwraca false, gdy nie ma dokąd wrócić (wtedy system zamyka aplikację). */
    fun back(): Boolean {
        if (sheet != null) {
            sheet = null
            return true
        }
        return when (screen) {
            is Screen.Chat -> {
                screen = Screen.Main
                core.activeConvo = null
                true
            }
            Screen.Search, Screen.Map, Screen.Beacon -> {
                screen = Screen.Main
                true
            }
            Screen.Main -> if (tab != Tab.CHATS) {
                tab = Tab.CHATS
                true
            } else {
                false
            }
            Screen.Onboarding -> false
        }
    }

    fun onResume() {
        permissionsTick++
        (screen as? Screen.Chat)?.let {
            core.activeConvo = it.convo
            core.markRead(it.convo)
        }
        val s = core.settings.value
        if (s.onboarded && s.meshEnabled && Permissions.meshGranted(ctx)) MeshService.start(ctx)
        checkUpdateIfDue()
    }

    // endregion

    // region Sieć

    fun completeOnboarding(nick: String) {
        core.updateSettings { it.copy(nick = nick.trim().take(24), onboarded = true, meshEnabled = true) }
        screen = Screen.Main
        MeshService.start(ctx)
    }

    fun setMeshEnabled(on: Boolean) {
        core.updateSettings { it.copy(meshEnabled = on) }
        if (on) {
            if (!Permissions.meshGranted(ctx)) {
                toast("Nadaj uprawnienia Bluetooth i urządzeń w pobliżu")
                return
            }
            MeshService.start(ctx)
        } else {
            MeshService.stop(ctx)
        }
    }

    fun send(convo: String, text: String) {
        if (text.isBlank()) return
        core.send(convo, text)
    }

    fun quick(convo: String, text: String) = core.send(convo, text, quick = true)

    fun shareLocation(convo: String, note: String = "") {
        if (!Geo.hasPermission(ctx)) {
            toast("Brak uprawnienia do lokalizacji")
            return
        }
        viewModelScope.launch {
            locating = true
            val p = Geo.current(ctx)
            locating = false
            if (p == null) {
                toast("Nie udało się ustalić pozycji – wyjdź pod otwarte niebo i włącz GPS")
                return@launch
            }
            myLocation = p
            core.send(convo, note, location = p)
        }
    }

    fun sos(text: String, withLocation: Boolean) {
        sheet = null
        viewModelScope.launch {
            val p = if (withLocation && Geo.hasPermission(ctx)) {
                locating = true
                Geo.current(ctx, timeoutMs = 12_000).also { locating = false }
            } else {
                null
            }
            if (p != null) myLocation = p
            core.sendSos(text, p)
            toast(if (p != null) "Wysłano SOS z pozycją" else "Wysłano SOS (bez pozycji)")
            open(Channel.convoOf(PUBLIC_CHANNEL))
        }
    }

    fun refreshLocation() {
        if (!Geo.hasPermission(ctx) || locating) return
        viewModelScope.launch {
            locating = true
            Geo.current(ctx)?.let { myLocation = it }
            locating = false
        }
    }

    fun joinChannel(name: String, password: String?) {
        val convo = core.joinChannel(name, password)
        if (convo == null) {
            toast("Podaj nazwę kanału")
            return
        }
        open(convo)
    }

    fun openMap(lat: Double, lon: Double, label: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(Geo.geoUri(lat, lon, label))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure { toast("Brak aplikacji map – zainstaluj np. OsmAnd lub Organic Maps (działają offline)") }
    }

    fun copy(text: String, label: String = "OpenMesh") {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        toast("Skopiowano")
    }

    fun shareOutside(text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(i, "Udostępnij").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // endregion

    // region Załączniki

    /** Wysyła wybrane zdjęcie albo plik do rozmowy. */
    fun sendAttachment(convo: String, uri: Uri) {
        core.sendAttachment(convo, uri)
    }

    /** Otwiera załącznik w aplikacji, która go obsługuje. */
    fun openAttachment(m: Message) {
        val id = m.attId ?: return
        if (!m.attDone) {
            toast("Plik jeszcze się ściąga")
            return
        }
        val intent = Attachments.openIntent(ctx, id, m.attName ?: "zalacznik", m.attMime.orEmpty())
        if (intent == null) {
            toast("Pliku nie ma już na tym telefonie")
            return
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { toast("Brak aplikacji, która otworzy ten plik") }
    }

    /** Przekazuje załącznik dalej – do zapisania w galerii albo wysłania inną drogą. */
    fun shareAttachment(m: Message) {
        val id = m.attId ?: return
        val intent = Attachments.shareIntent(ctx, id, m.attName ?: "zalacznik", m.attMime.orEmpty())
        if (intent == null) {
            toast("Pliku nie ma już na tym telefonie")
            return
        }
        runCatching {
            ctx.startActivity(Intent.createChooser(intent, "Zapisz lub wyślij").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("Nie udało się udostępnić pliku") }
    }

    fun requestMissingParts(m: Message) = core.requestMissing(m.id)

    fun resendAttachment(m: Message) {
        core.resendAttachment(m.id)
        toast("Nadaję plik ponownie")
    }

    /** Miejsce zajęte przez pobrane załączniki. */
    fun attachmentBytes(): Long = Attachments.totalBytes(ctx)

    fun clearAttachments() {
        Attachments.clear(ctx)
        toast("Usunięto pobrane pliki")
    }

    // endregion

    // region Rozmowy: przypięcia, archiwum, eksport

    fun togglePinned(convo: String) {
        val pinned = convo in core.settings.value.pinned
        core.setPinned(convo, !pinned)
        toast(if (pinned) "Odpięto rozmowę" else "Przypięto na górze listy")
    }

    fun toggleArchived(convo: String) {
        val archived = convo in core.settings.value.archived
        core.setArchived(convo, !archived)
        toast(if (archived) "Przywrócono z archiwum" else "Przeniesiono do archiwum")
    }

    fun exportConvo(convo: String, title: String) {
        val msgs = core.messages.value.filter { it.convo == convo }
        if (msgs.isEmpty()) {
            toast("Ta rozmowa jest pusta")
            return
        }
        shareFile(Export.transcript(convo, title, msgs, myName()), title)
    }

    fun exportEverything() {
        val msgs = core.messages.value
        if (msgs.isEmpty()) {
            toast("Nie ma jeszcze żadnych wiadomości")
            return
        }
        shareFile(
            Export.fullReport(msgs, core.channels.value, core.peers.value, core.myId, myName()),
            "cala-historia",
        )
    }

    private fun myName() = core.settings.value.nick.ifBlank { "Ja" }

    private fun shareFile(text: String, name: String) {
        runCatching {
            val uri = Export.write(ctx, name, text)
            val chooser = Intent.createChooser(Export.shareIntent(uri, Export.fileName(name)), "Zapisz lub wyślij zapis")
            ctx.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast("Nie udało się przygotować pliku") }
    }

    // endregion

    // region Latarka alarmowa

    val beaconAvailable get() = Beacon.hasTorch(ctx)

    fun toggleBeacon() {
        val s = core.settings.value
        if (!Beacon.running.value && !beaconAvailable && !s.beaconScreen) {
            toast("Ten telefon nie ma diody – włącz miganie ekranem")
            return
        }
        Beacon.toggle(ctx, s.beaconPattern)
    }

    fun setBeaconPattern(pattern: BeaconPattern) {
        core.updateSettings { it.copy(beaconPattern = pattern) }
        if (Beacon.running.value) Beacon.start(ctx, pattern)
    }

    fun stopBeacon() = Beacon.stop(ctx)

    // endregion

    // region Dane

    fun wipeAll() {
        Beacon.stop(ctx)
        MeshService.stop(ctx)
        core.wipeAll()
        sheet = null
        screen = Screen.Onboarding
        tab = Tab.CHATS
    }

    // endregion

    // region Aktualizacje

    fun checkUpdateIfDue() {
        val s = core.settings.value
        if (!s.autoUpdate) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < 6 * 60 * 60_000L) return
        checkUpdate(manual = false)
    }

    fun checkUpdate(manual: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        if (!UpdateManager.online(ctx)) {
            if (manual) update = UpdateState.Failed("Brak internetu – aktualizacje sprawdzisz po połączeniu z siecią")
            return
        }
        updateJob = viewModelScope.launch {
            update = UpdateState.Checking
            update = runCatching {
                val info = UpdateManager.fetchLatest()
                core.updateSettings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                if (isNewer(info.version, UpdateManager.currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            }.getOrElse { if (manual) UpdateState.Failed(it.message ?: "Błąd sprawdzania") else UpdateState.Idle }
            val u = update
            // Automatyczna instalacja: pobranie od razu po wykryciu nowej wersji.
            if (u is UpdateState.Available && core.settings.value.autoInstall && UpdateManager.canInstall(ctx)) updateNow()
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> {
                if (!UpdateManager.canInstall(ctx)) {
                    toast("Zezwól OpenMesh na instalowanie aplikacji")
                    UpdateManager.openInstallPermissionSettings(ctx)
                    return
                }
                updateJob = viewModelScope.launch {
                    update = UpdateState.Downloading(u.info, 0f)
                    val file = runCatching {
                        UpdateManager.download(ctx, u.info) { p -> update = UpdateState.Downloading(u.info, p) }
                    }.getOrElse {
                        update = UpdateState.Failed(it.message ?: "Błąd pobierania")
                        return@launch
                    }
                    update = UpdateState.Ready(u.info, file)
                    install()
                }
            }
            is UpdateState.Ready -> install()
            else -> Unit
        }
    }

    private fun install() {
        val u = update as? UpdateState.Ready ?: return
        if (!UpdateManager.canInstall(ctx)) {
            UpdateManager.openInstallPermissionSettings(ctx)
            return
        }
        viewModelScope.launch {
            update = UpdateState.Installing(u.info)
            runCatching { UpdateManager.install(ctx, u.file) }.onFailure {
                update = UpdateState.Failed(it.message ?: "Błąd instalacji")
                return@launch
            }
            // Gdy system pokaże okno potwierdzenia, a użytkownik je zamknie – wracamy do „gotowa do instalacji”.
            delay(20_000)
            if (update is UpdateState.Installing) update = u
        }
    }

    // endregion
}
