package com.blacksingularities.openmesh.mesh

import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import com.blacksingularities.openmesh.data.Attachments
import com.blacksingularities.openmesh.data.JsonStore
import com.blacksingularities.openmesh.util.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.security.KeyPair

/** Zdarzenia dla interfejsu i powiadomień. */
sealed interface MeshEvent {
    data class Incoming(val message: Message, val title: String) : MeshEvent
    data class Sos(val message: Message) : MeshEvent
    data class Notice(val text: String) : MeshEvent
}

/**
 * Rdzeń sieci mesh. Każdy pakiet ma unikalny identyfikator i licznik TTL: węzeł, który go
 * pierwszy raz widzi, dostarcza go lokalnie (jeśli dotyczy) i przekazuje dalej wszystkim sąsiadom
 * poza nadawcą. Ostatnie pakiety są przechowywane i wysyłane każdemu nowemu sąsiadowi
 * (store-and-forward), więc wiadomość „przechodzi” z osobą między rozdzielonymi grupami.
 *
 * Cała logika wykonuje się w jednym wątku ([serial]) – bez blokad i wyścigów.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeshCore(private val app: Context) : NearbyTransport.Listener {
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)
    private val store = JsonStore(File(app.filesDir, "mesh"), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    private val json get() = store.json
    private val prefs = app.getSharedPreferences("openmesh.identity", Context.MODE_PRIVATE)

    private var keys: KeyPair = loadKeys()
    val myPub: String get() = Crypto.encodePub(keys.public)
    val myId: String get() = Crypto.peerId(myPub)

    val transport = NearbyTransport(app, this)

    private val _settings = MutableStateFlow(store.load(SETTINGS, Settings.serializer()) ?: Settings())
    val settings: StateFlow<Settings> = _settings
    private val _messages = MutableStateFlow(store.load(MESSAGES, ListSerializer(Message.serializer())) ?: emptyList())
    val messages: StateFlow<List<Message>> = _messages
    private val _peers = MutableStateFlow(store.load(PEERS, MapSerializer(String.serializer(), Peer.serializer())) ?: emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers
    private val _channels = MutableStateFlow(
        (store.load(CHANNELS, ListSerializer(Channel.serializer())) ?: emptyList())
            .let { list -> if (list.none { it.key == Channel.convoOf(PUBLIC_CHANNEL) }) listOf(Channel(PUBLIC_CHANNEL)) + list else list },
    )
    val channels: StateFlow<List<Channel>> = _channels
    private val _unread = MutableStateFlow(store.load(UNREAD, MapSerializer(String.serializer(), Int.serializer())) ?: emptyMap())
    val unread: StateFlow<Map<String, Int>> = _unread
    private val _stats = MutableStateFlow(store.load(STATS, Stats.serializer()) ?: Stats())
    val stats: StateFlow<Stats> = _stats
    private val _seenChannels = MutableStateFlow<Map<String, SeenChannel>>(emptyMap())
    val seenChannels: StateFlow<Map<String, SeenChannel>> = _seenChannels
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts

    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MeshEvent> = _events

    /** Pakiety do przekazania nowym sąsiadom. */
    private val cache = (store.load(CACHE, ListSerializer(Packet.serializer())) ?: emptyList()).toMutableList()
    private val seen = object : LinkedHashMap<String, Boolean>(4096, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 8000
    }.apply { cache.forEach { put(it.id, true) } }
    private val dmKeys = mutableMapOf<String, ByteArray>()
    private val channelKeys = mutableMapOf<String, ByteArray>()

    /** Składane właśnie załączniki: identyfikator pliku → odebrane części. */
    private val assemblies = mutableMapOf<String, FileTransfer.Assembly>()

    /** Rozmowa otwarta na ekranie (bez powiadomień dla niej) i czy aplikacja jest widoczna. */
    @Volatile var activeConvo: String? = null
    @Volatile var appVisible: Boolean = false

    private var ticker: Job? = null

    init {
        // Wiadomości wysłane bez sieci czekają w kolejce; po restarcie aplikacji nadal w niej są.
        scope.launch { prune() }
    }

    // region Cykl życia sieci

    fun startMesh() {
        val s = _settings.value
        transport.start(myId, displayName(s), s.topology)
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                var n = 0
                while (isActive) {
                    delay(15_000)
                    n++
                    if (n % 6 == 0 && transport.links.value.isNotEmpty()) hello()
                    if (n % 20 == 0) prune()
                }
            }
        }
    }

    fun stopMesh() {
        ticker?.cancel()
        ticker = null
        transport.stop()
    }

    private fun displayName(s: Settings = _settings.value) = s.nick.ifBlank { "Węzeł-" + myId.take(4).uppercase() }

    override fun onLinkUp(link: Link) {
        scope.launch {
            _stats.update { it.copy(connections = it.connections + 1) }
            saveStats()
            // Przedstawienie się i przekazanie zaległych pakietów nowemu sąsiadowi.
            val h = buildHello()
            markSeen(h.id)
            transport.send(link.endpoint, encode(h))
            val now = System.currentTimeMillis()
            val ret = retentionMs()
            cache.filter { now - it.ts < ret }.takeLast(SYNC_LIMIT).forEach { transport.send(link.endpoint, encode(it)) }
            // Moje wiadomości z kolejki wyszły właśnie do sieci.
            if (_messages.value.any { it.mine && it.status == MsgStatus.QUEUED }) {
                _messages.update { list -> list.map { if (it.mine && it.status == MsgStatus.QUEUED) it.copy(status = MsgStatus.SENT) else it } }
                saveMessages()
            }
        }
    }

    override fun onLinkDown(endpoint: String) = Unit

    override fun onBytes(endpoint: String, bytes: ByteArray) {
        scope.launch { handle(endpoint, bytes) }
    }

    // endregion

    // region Odbiór

    private fun markSeen(id: String): Boolean {
        if (seen.containsKey(id)) return false
        seen[id] = true
        return true
    }

    private fun handle(endpoint: String, bytes: ByteArray) {
        val p = runCatching { json.decodeFromString(Packet.serializer(), bytes.decodeToString()) }.getOrNull() ?: return
        if (!markSeen(p.id)) return
        if (p.from == myId) return
        if (Crypto.peerId(p.pub) != p.from || !Crypto.verify(p.pub, p.signedBytes(), p.sig)) {
            _stats.update { it.copy(rejected = it.rejected + 1) }
            return
        }
        val now = System.currentTimeMillis()
        if (now - p.ts > retentionMs() || p.ts - now > 6 * HOUR) return

        val blocked = _peers.value[p.from]?.blocked == true
        if (p.hops == 0) transport.rename(endpoint, p.from, p.name)
        updatePeer(p, now)
        if (!blocked) deliver(p, now)
        _stats.update { it.copy(received = it.received + 1) }

        // Przekazanie dalej (także pakiety od zablokowanych – blokada dotyczy tylko wyświetlania).
        val s = _settings.value
        if (s.relay && p.ttl > 0) {
            val fwd = p.copy(ttl = minOf(p.ttl, MAX_TTL) - 1, hops = p.hops + 1)
            if (fwd.type != PacketType.HELLO) remember(fwd)
            if (transport.broadcast(encode(fwd), except = endpoint) > 0) {
                _stats.update { it.copy(relayed = it.relayed + 1) }
            }
        }
        saveStats()
    }

    private fun updatePeer(p: Packet, now: Long) {
        val content = if (!p.enc && p.type != PacketType.ACK) decodeContent(p.body) else null
        _peers.update { map ->
            val old = map[p.from]
            val hops = if (old != null && now - old.lastSeen < 2 * MINUTE) minOf(old.hops, p.hops) else p.hops
            val hasLoc = content?.lat != null && content.lon != null
            val peer = (old ?: Peer(p.from, p.name, p.pub, now, hops)).copy(
                name = p.name,
                pub = p.pub,
                lastSeen = maxOf(old?.lastSeen ?: 0, now),
                hops = hops,
                status = if (p.type == PacketType.HELLO) content?.text.orEmpty() else old?.status.orEmpty(),
                lat = if (hasLoc) content?.lat else old?.lat,
                lon = if (hasLoc) content?.lon else old?.lon,
                locTs = if (hasLoc) p.ts else old?.locTs,
                battery = content?.battery ?: old?.battery,
            )
            map + (p.from to peer)
        }
        savePeers()
    }

    private fun deliver(p: Packet, now: Long) {
        when (p.type) {
            PacketType.HELLO -> Unit
            PacketType.CHAT -> {
                val name = Channel.normalize(p.channel ?: return)
                val ch = _channels.value.firstOrNull { it.key == Channel.convoOf(name) }
                if (ch == null) {
                    _seenChannels.update { m ->
                        val old = m[name]
                        m + (name to SeenChannel(name, p.enc, now, (old?.count ?: 0) + 1))
                    }
                    return
                }
                val content = if (p.enc) {
                    val pw = ch.password ?: return
                    Crypto.open(channelKey(name, pw), p.body)?.let(::decodeContent) ?: return
                } else {
                    if (ch.secured) return
                    decodeContent(p.body) ?: return
                }
                addIncoming(p, ch.key, content, now, title = "#" + name + " · " + p.name, muted = ch.muted)
            }
            PacketType.DM -> {
                if (p.to != myId) return
                val content = Crypto.open(dmKey(p.from, p.pub), p.body)?.let(::decodeContent) ?: return
                addIncoming(p, dmConvo(p.from), content, now, title = p.name)
                sendAck(p)
            }
            PacketType.SOS -> {
                val content = decodeContent(p.body) ?: Content("SOS")
                val m = toMessage(p, Channel.convoOf(PUBLIC_CHANNEL), content, now).copy(sos = true)
                if (addMessage(m)) {
                    bumpUnread(m.convo)
                    _events.tryEmit(MeshEvent.Sos(m))
                }
            }
            PacketType.ACK -> {
                if (p.to != myId) return
                val ref = p.ref ?: return
                _messages.update { list -> list.map { if (it.id == ref && it.mine) it.copy(status = MsgStatus.DELIVERED) else it } }
                saveMessages()
            }
            PacketType.FILE -> receiveChunk(p, now)
            PacketType.FILE_REQ -> resendParts(p)
        }
    }

    private fun toMessage(p: Packet, convo: String, c: Content, now: Long) = Message(
        id = p.id,
        convo = convo,
        from = p.from,
        fromName = p.name,
        ts = p.ts,
        text = c.text,
        lat = c.lat,
        lon = c.lon,
        acc = c.acc,
        hops = p.hops,
        quick = c.quick,
        encrypted = p.enc,
        battery = c.battery,
        receivedAt = now,
    )

    private fun addIncoming(p: Packet, convo: String, c: Content, now: Long, title: String, muted: Boolean = false) {
        val m = toMessage(p, convo, c, now)
        if (!addMessage(m)) return
        if (!(appVisible && activeConvo == convo)) bumpUnread(convo)
        if (!muted) _events.tryEmit(MeshEvent.Incoming(m, title))
    }

    private fun addMessage(m: Message): Boolean {
        if (_messages.value.any { it.id == m.id }) return false
        _messages.update { list -> (list + m).let { if (it.size > MESSAGE_LIMIT) it.drop(it.size - MESSAGE_LIMIT) else it } }
        saveMessages()
        return true
    }

    private fun bumpUnread(convo: String) {
        _unread.update { it + (convo to (it[convo] ?: 0) + 1) }
        store.save(UNREAD, MapSerializer(String.serializer(), Int.serializer())) { _unread.value }
    }

    // endregion

    // region Wysyłanie

    /** Wysyła wiadomość do rozmowy; brak sieci = wiadomość czeka w kolejce na pierwszego sąsiada. */
    fun send(convo: String, text: String, location: GeoPoint? = null, quick: Boolean = false) {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty() && location == null) return
        scope.launch {
            val content = Content(clean, location?.lat, location?.lon, location?.accuracy, quick, battery())
            val plain = json.encodeToString(Content.serializer(), content)
            val s = _settings.value
            val packet = if (isDm(convo)) {
                val peer = _peers.value[peerOf(convo)]
                if (peer == null) {
                    _events.tryEmit(MeshEvent.Notice("Nieznany klucz odbiorcy – poczekaj, aż pojawi się w sieci"))
                    return@launch
                }
                build(PacketType.DM, s.ttl, to = peer.id, body = Crypto.seal(dmKey(peer.id, peer.pub), plain), enc = true)
            } else {
                val ch = _channels.value.firstOrNull { it.key == convo } ?: return@launch
                val name = Channel.normalize(ch.name)
                if (ch.secured) {
                    build(PacketType.CHAT, s.ttl, channel = name, body = Crypto.seal(channelKey(name, ch.password!!), plain), enc = true)
                } else {
                    build(PacketType.CHAT, s.ttl, channel = name, body = plain)
                }
            }
            val reach = emit(packet)
            addMessage(
                Message(
                    id = packet.id,
                    convo = convo,
                    from = myId,
                    fromName = displayName(s),
                    ts = packet.ts,
                    text = clean,
                    lat = location?.lat,
                    lon = location?.lon,
                    acc = location?.accuracy,
                    mine = true,
                    status = if (reach > 0) MsgStatus.SENT else MsgStatus.QUEUED,
                    quick = quick,
                    encrypted = packet.enc,
                ),
            )
            setDraft(convo, "")
        }
    }

    fun sendSos(text: String, location: GeoPoint?) {
        scope.launch {
            val content = Content(text.trim().ifEmpty { "Potrzebuję pomocy!" }.take(MAX_TEXT), location?.lat, location?.lon, location?.accuracy, battery = battery())
            val packet = build(PacketType.SOS, SOS_TTL, body = json.encodeToString(Content.serializer(), content))
            val reach = emit(packet)
            addMessage(
                Message(
                    id = packet.id,
                    convo = Channel.convoOf(PUBLIC_CHANNEL),
                    from = myId,
                    fromName = displayName(),
                    ts = packet.ts,
                    text = content.text,
                    lat = content.lat,
                    lon = content.lon,
                    acc = content.acc,
                    mine = true,
                    sos = true,
                    status = if (reach > 0) MsgStatus.SENT else MsgStatus.QUEUED,
                ),
            )
        }
    }

    /** Ponowne nadanie wiadomości (np. SOS bez odpowiedzi) – jako nowy pakiet z tą samą treścią. */
    fun resend(message: Message) {
        if (message.sos) {
            sendSos(message.text, message.lat?.let { GeoPoint(it, message.lon ?: 0.0, message.acc) })
        } else {
            send(message.convo, message.text, message.lat?.let { GeoPoint(it, message.lon ?: 0.0, message.acc) }, message.quick)
        }
    }

    private fun sendAck(p: Packet) {
        emit(build(PacketType.ACK, maxOf(_settings.value.ttl, p.hops + 2).coerceAtMost(MAX_TTL), to = p.from, ref = p.id))
    }

    private fun hello() {
        val h = buildHello()
        markSeen(h.id)
        transport.broadcast(encode(h))
    }

    private fun buildHello(): Packet {
        val s = _settings.value
        val body = json.encodeToString(Content.serializer(), Content(text = s.status, battery = battery()))
        return build(PacketType.HELLO, s.ttl, body = body)
    }

    private fun build(type: PacketType, ttl: Int, to: String? = null, channel: String? = null, body: String = "", enc: Boolean = false, ref: String? = null): Packet {
        val raw = Packet(
            id = Crypto.randomId(),
            type = type,
            from = myId,
            name = displayName(),
            pub = myPub,
            ts = System.currentTimeMillis(),
            ttl = ttl.coerceIn(1, MAX_TTL),
            to = to,
            channel = channel,
            body = body,
            enc = enc,
            ref = ref,
        )
        return raw.copy(sig = Crypto.sign(keys.private, raw.signedBytes()))
    }

    /** Nadaje własny pakiet: zapamiętuje go do przekazania i wysyła do sąsiadów. */
    private fun emit(p: Packet): Int {
        markSeen(p.id)
        remember(p)
        _stats.update { it.copy(sent = it.sent + 1) }
        saveStats()
        return transport.broadcast(encode(p))
    }

    private fun encode(p: Packet): ByteArray = json.encodeToString(Packet.serializer(), p).encodeToByteArray()

    private fun remember(p: Packet) {
        cache.removeAll { it.id == p.id }
        cache += p
        if (cache.size > CACHE_LIMIT) cache.subList(0, cache.size - CACHE_LIMIT).clear()
        saveCache()
    }

    // endregion

    // region Załączniki

    /**
     * Wysyła zdjęcie albo plik: treść jest przygotowywana (zdjęcia zmniejszane), zapisywana
     * lokalnie i nadawana w częściach. Każda część to osobny podpisany pakiet, więc jedzie
     * tymi samymi ścieżkami co wiadomości – z przekazywaniem i store-and-forward.
     */
    fun sendAttachment(convo: String, uri: Uri) {
        scope.launch {
            val s = _settings.value
            val limit = s.attachmentMaxKb.toLong() * 1024
            val prepared = runCatching { Attachments.prepare(app, uri, limit, s.shrinkPhotos) }.getOrElse { e ->
                _events.tryEmit(
                    MeshEvent.Notice(
                        when (e) {
                            is Attachments.TooBig -> "Plik ma " + FileTransfer.formatSize(e.size) +
                                ", a limit to " + FileTransfer.formatSize(e.limit) +
                                " – zmień limit w ustawieniach albo wyślij mniejszy plik"
                            else -> "Nie udało się odczytać pliku"
                        },
                    ),
                )
                return@launch
            }
            val bytes = prepared.bytes
            val fid = Crypto.randomId()
            val cnt = FileTransfer.partCount(bytes.size)
            val saved = runCatching { Attachments.save(app, fid, bytes) }.isSuccess
            if (!saved) {
                _events.tryEmit(MeshEvent.Notice("Brak miejsca na zapis załącznika"))
                return@launch
            }
            val meta = Att(
                fid = fid,
                name = prepared.name,
                mime = prepared.mime,
                size = bytes.size.toLong(),
                sha = Crypto.hex(Crypto.sha256(bytes)),
                cnt = cnt,
            )
            val secured = isDm(convo) || _channels.value.firstOrNull { it.key == convo }?.secured == true
            addMessage(
                Message(
                    id = fid,
                    convo = convo,
                    from = myId,
                    fromName = displayName(s),
                    ts = System.currentTimeMillis(),
                    text = "",
                    mine = true,
                    status = MsgStatus.QUEUED,
                    encrypted = secured,
                    attId = fid,
                    attName = prepared.name,
                    attMime = prepared.mime,
                    attSize = bytes.size.toLong(),
                    attTotal = cnt,
                    attDone = true,
                ),
            )
            if (prepared.shrunk) {
                _events.tryEmit(MeshEvent.Notice("Zdjęcie zmniejszone do " + FileTransfer.formatSize(bytes.size.toLong())))
            }
            emitParts(convo, meta, bytes, (0 until cnt).toList())
        }
    }

    /** Nadaje wskazane części pliku; postęp widnieje na wiadomości. */
    private suspend fun emitParts(convo: String, meta: Att, bytes: ByteArray, indexes: List<Int>) {
        var reached = 0
        indexes.forEach { idx ->
            val chunk = FileTransfer.part(bytes, idx) ?: return@forEach
            val content = Content(att = meta.copy(idx = idx, data = Crypto.b64(chunk)))
            val packet = buildAttachmentPacket(convo, content) ?: return
            reached += emit(packet)
            _messages.update { list ->
                list.map { if (it.id == meta.fid && it.mine) it.copy(attParts = minOf(it.attTotal, it.attParts + 1)) else it }
            }
            // Mesh nie lubi zalewania – części wychodzą z przerwą, żeby zwykłe wiadomości dalej przechodziły.
            delay(PART_DELAY)
        }
        if (reached > 0) {
            _messages.update { list -> list.map { if (it.id == meta.fid && it.mine) it.copy(status = MsgStatus.SENT) else it } }
        }
        saveMessages()
    }

    /** Pakiet części: do kanału (ewentualnie szyfrowany hasłem) albo prywatnie do jednej osoby. */
    private fun buildAttachmentPacket(convo: String, content: Content, type: PacketType = PacketType.FILE): Packet? {
        val plain = json.encodeToString(Content.serializer(), content)
        val s = _settings.value
        return if (isDm(convo)) {
            val peer = _peers.value[peerOf(convo)]
            if (peer == null) {
                _events.tryEmit(MeshEvent.Notice("Nieznany klucz odbiorcy – poczekaj, aż pojawi się w sieci"))
                null
            } else {
                build(type, s.ttl, to = peer.id, body = Crypto.seal(dmKey(peer.id, peer.pub), plain), enc = true)
            }
        } else {
            val ch = _channels.value.firstOrNull { it.key == convo }
            val name = ch?.let { Channel.normalize(it.name) }
            when {
                ch == null || name == null -> null
                ch.secured -> build(type, s.ttl, channel = name, body = Crypto.seal(channelKey(name, ch.password!!), plain), enc = true)
                else -> build(type, s.ttl, channel = name, body = plain)
            }
        }
    }

    /** Odczytuje treść pakietu załącznika i ustala, do której rozmowy należy. */
    private fun openAttachment(p: Packet): Pair<Content, String>? {
        if (p.to != null) {
            if (p.to != myId) return null
            val content = Crypto.open(dmKey(p.from, p.pub), p.body)?.let(::decodeContent) ?: return null
            return content to dmConvo(p.from)
        }
        val name = Channel.normalize(p.channel ?: return null)
        val ch = _channels.value.firstOrNull { it.key == Channel.convoOf(name) } ?: return null
        val content = if (p.enc) {
            val pw = ch.password ?: return null
            Crypto.open(channelKey(name, pw), p.body)?.let(::decodeContent) ?: return null
        } else {
            if (ch.secured) return null
            decodeContent(p.body) ?: return null
        }
        return content to ch.key
    }

    private fun receiveChunk(p: Packet, now: Long) {
        val opened = openAttachment(p) ?: return
        val content = opened.first
        val convo = opened.second
        val att = content.att ?: return
        if (att.cnt <= 0 || att.cnt > MAX_PARTS || att.size > FileTransfer.MAX_BYTES) return
        val data = runCatching { Crypto.unb64(att.data) }.getOrNull() ?: return
        // Plik jest już złożony – powtórkę można zignorować.
        if (_messages.value.any { it.id == att.fid && it.attDone }) return

        val a = assemblies.getOrPut(att.fid) { FileTransfer.Assembly(att.fid, att.cnt, att) }
        if (a.cnt != att.cnt) return
        val fresh = a.put(att.idx, data, now)

        val existing = _messages.value.firstOrNull { it.id == att.fid }
        if (existing == null) {
            addMessage(
                Message(
                    id = att.fid,
                    convo = convo,
                    from = p.from,
                    fromName = p.name,
                    ts = p.ts,
                    text = "",
                    hops = p.hops,
                    encrypted = p.enc,
                    receivedAt = now,
                    attId = att.fid,
                    attName = FileTransfer.safeName(att.name, att.mime),
                    attMime = att.mime,
                    attSize = att.size,
                    attParts = a.received,
                    attTotal = a.cnt,
                ),
            )
        } else if (fresh) {
            _messages.update { list -> list.map { if (it.id == att.fid) it.copy(attParts = a.received) else it } }
            saveMessages()
        }

        if (!a.complete) return
        val whole = a.bytes() ?: return
        assemblies.remove(att.fid)
        // Skrót chroni przed złożeniem pliku z części, które po drodze się pomieszały.
        if (att.sha.isNotEmpty() && Crypto.hex(Crypto.sha256(whole)) != att.sha) {
            _messages.update { list -> list.filterNot { it.id == att.fid } }
            saveMessages()
            _events.tryEmit(MeshEvent.Notice("Załącznik od " + p.name + " doszedł uszkodzony"))
            return
        }
        if (runCatching { Attachments.save(app, att.fid, whole) }.isFailure) {
            _events.tryEmit(MeshEvent.Notice("Brak miejsca na zapis załącznika"))
            return
        }
        _messages.update { list ->
            list.map { if (it.id == att.fid) it.copy(attDone = true, attParts = a.cnt, attSize = whole.size.toLong()) else it }
        }
        saveMessages()
        val done = _messages.value.firstOrNull { it.id == att.fid } ?: return
        if (!(appVisible && activeConvo == convo)) bumpUnread(convo)
        val muted = !isDm(convo) && _channels.value.firstOrNull { it.key == convo }?.muted == true
        if (!muted) {
            val title = if (isDm(convo)) p.name else "#" + channelOf(convo) + " · " + p.name
            _events.tryEmit(MeshEvent.Incoming(done, title))
        }
        if (p.to == myId) sendAck(p.copy(id = att.fid))
    }

    /** Prośba o brakujące części: nadawca wysyła je ponownie z zapisanego u siebie pliku. */
    private fun resendParts(p: Packet) {
        val opened = openAttachment(p) ?: return
        val att = opened.first.att ?: return
        val convo = opened.second
        if (att.miss.isEmpty()) return
        val mine = _messages.value.firstOrNull { it.id == att.fid && it.mine && it.attId != null } ?: return
        val bytes = Attachments.read(app, att.fid) ?: return
        val meta = Att(
            fid = att.fid,
            name = mine.attName.orEmpty(),
            mime = mine.attMime.orEmpty(),
            size = bytes.size.toLong(),
            sha = Crypto.hex(Crypto.sha256(bytes)),
            cnt = FileTransfer.partCount(bytes.size),
        )
        val wanted = att.miss.filter { it in 0 until meta.cnt }.distinct().take(MAX_PARTS)
        scope.launch { emitParts(convo, meta, bytes, wanted) }
    }

    /** Prosi nadawcę o części, które nie doszły. */
    fun requestMissing(messageId: String) {
        scope.launch {
            val m = _messages.value.firstOrNull { it.id == messageId } ?: return@launch
            if (m.mine || m.attDone) return@launch
            val miss = assemblies[messageId]?.missing() ?: (0 until m.attTotal).toList()
            if (miss.isEmpty()) return@launch
            val packet = buildAttachmentPacket(m.convo, Content(att = Att(fid = messageId, miss = miss.take(MAX_PARTS))), PacketType.FILE_REQ)
                ?: return@launch
            val reach = emit(packet)
            _events.tryEmit(
                MeshEvent.Notice(
                    if (reach > 0) {
                        "Poprosiłem o brakujące części (" + miss.size + ")"
                    } else {
                        "Prośba czeka na kogoś w zasięgu"
                    },
                ),
            )
        }
    }

    /** Ponowne nadanie całego załącznika (gdy odbiorca nic nie dostał). */
    fun resendAttachment(messageId: String) {
        scope.launch {
            val m = _messages.value.firstOrNull { it.id == messageId && it.mine } ?: return@launch
            val id = m.attId ?: return@launch
            val bytes = Attachments.read(app, id)
            if (bytes == null) {
                _events.tryEmit(MeshEvent.Notice("Pliku nie ma już na tym telefonie"))
                return@launch
            }
            val meta = Att(
                fid = id,
                name = m.attName.orEmpty(),
                mime = m.attMime.orEmpty(),
                size = bytes.size.toLong(),
                sha = Crypto.hex(Crypto.sha256(bytes)),
                cnt = FileTransfer.partCount(bytes.size),
            )
            _messages.update { list -> list.map { if (it.id == messageId) it.copy(attParts = 0, attTotal = meta.cnt) else it } }
            emitParts(m.convo, meta, bytes, (0 until meta.cnt).toList())
        }
    }

    /** Ile części brakuje w trwającym odbiorze. */
    fun missingParts(messageId: String): Int = assemblies[messageId]?.missing()?.size ?: 0

    // endregion

    // region Klucze

    private fun dmKey(peerId: String, pub: String): ByteArray = dmKeys.getOrPut(peerId + pub.hashCode()) { Crypto.dmKey(keys.private, pub) }

    private fun channelKey(name: String, password: String): ByteArray = channelKeys.getOrPut(name + "\u0000" + password) { Crypto.channelKey(name, password) }

    private fun loadKeys(): KeyPair {
        val priv = prefs.getString("priv", null)
        val pub = prefs.getString("pub", null)
        if (priv != null && pub != null) {
            runCatching { return KeyPair(Crypto.decodePub(pub), Crypto.decodePriv(priv)) }
        }
        val k = Crypto.generate()
        prefs.edit().putString("priv", Crypto.encodePriv(k.private)).putString("pub", Crypto.encodePub(k.public)).apply()
        return k
    }

    // endregion

    // region Ustawienia i zarządzanie

    fun updateSettings(change: (Settings) -> Settings) {
        val before = _settings.value
        val after = change(before)
        _settings.value = after
        store.save(SETTINGS, Settings.serializer(), 200) { _settings.value }
        val restart = before.nick != after.nick || before.topology != after.topology
        if (restart && after.meshEnabled && transport.state.value != LinkState.Off) startMesh()
        if (before.status != after.status && transport.links.value.isNotEmpty()) scope.launch { hello() }
    }

    fun joinChannel(rawName: String, password: String?): String? {
        val name = Channel.normalize(rawName)
        if (name.isEmpty()) return null
        val ch = Channel(name, password?.takeIf { it.isNotEmpty() }, joinedAt = System.currentTimeMillis())
        _channels.update { list -> list.filterNot { it.key == ch.key } + ch }
        _seenChannels.update { it - name }
        saveChannels()
        return ch.key
    }

    fun leaveChannel(convo: String) {
        if (convo == Channel.convoOf(PUBLIC_CHANNEL)) return
        _channels.update { list -> list.filterNot { it.key == convo } }
        saveChannels()
        updateSettings { it.copy(pinned = it.pinned - convo, archived = it.archived - convo) }
    }

    fun setMuted(convo: String, muted: Boolean) {
        _channels.update { list -> list.map { if (it.key == convo) it.copy(muted = muted) else it } }
        saveChannels()
    }

    /** Przypięta rozmowa trzyma się góry listy (i nigdy nie jest w archiwum). */
    fun setPinned(convo: String, pinned: Boolean) = updateSettings {
        it.copy(
            pinned = if (pinned) it.pinned.filterNot { c -> c == convo } + convo else it.pinned.filterNot { c -> c == convo },
            archived = if (pinned) it.archived.filterNot { c -> c == convo } else it.archived,
        )
    }

    /** Archiwum chowa rozmowę z listy; nowa wiadomość nie przywraca jej automatycznie. */
    fun setArchived(convo: String, archived: Boolean) = updateSettings {
        it.copy(
            archived = if (archived) it.archived.filterNot { c -> c == convo } + convo else it.archived.filterNot { c -> c == convo },
            pinned = if (archived) it.pinned.filterNot { c -> c == convo } else it.pinned,
        )
    }

    fun setBlocked(peerId: String, blocked: Boolean) {
        _peers.update { m -> m[peerId]?.let { m + (peerId to it.copy(blocked = blocked)) } ?: m }
        savePeers()
    }

    fun forgetPeer(peerId: String) {
        _peers.update { it - peerId }
        savePeers()
    }

    fun markRead(convo: String) {
        if ((_unread.value[convo] ?: 0) == 0) return
        _unread.update { it - convo }
        store.save(UNREAD, MapSerializer(String.serializer(), Int.serializer())) { _unread.value }
    }

    fun setDraft(convo: String, text: String) {
        _drafts.update { if (text.isEmpty()) it - convo else it + (convo to text) }
    }

    fun deleteMessage(id: String) {
        val attId = _messages.value.firstOrNull { it.id == id }?.attId
        _messages.update { list -> list.filterNot { it.id == id } }
        saveMessages()
        assemblies.remove(id)
        if (attId != null && _messages.value.none { it.attId == attId }) Attachments.delete(app, attId)
    }

    fun clearConversation(convo: String) {
        _messages.update { list -> list.filterNot { it.convo == convo } }
        _unread.update { it - convo }
        saveMessages()
        pruneAttachments()
    }

    fun clearHistory() {
        _messages.value = emptyList()
        _unread.value = emptyMap()
        assemblies.clear()
        saveMessages()
        pruneAttachments()
        store.save(UNREAD, MapSerializer(String.serializer(), Int.serializer())) { _unread.value }
    }

    /** Nowa tożsamość: nowe klucze i identyfikator; historia i znajomi zostają usunięci. */
    fun resetIdentity() {
        scope.launch {
            val k = Crypto.generate()
            prefs.edit().putString("priv", Crypto.encodePriv(k.private)).putString("pub", Crypto.encodePub(k.public)).apply()
            keys = k
            dmKeys.clear()
            _peers.value = emptyMap()
            savePeers()
            if (transport.state.value != LinkState.Off) startMesh()
        }
    }

    /** Natychmiastowe usunięcie wszystkiego (tryb paniki): klucze, historia, kanały, ustawienia. */
    fun wipeAll() {
        stopMesh()
        scope.launch { wipeNow() }
    }

    private fun wipeNow() {
        store.wipe()
        prefs.edit().clear().commit()
        keys = loadKeys()
        dmKeys.clear()
        channelKeys.clear()
        cache.clear()
        assemblies.clear()
        Attachments.clear(app)
        _messages.value = emptyList()
        _peers.value = emptyMap()
        _channels.value = listOf(Channel(PUBLIC_CHANNEL))
        _unread.value = emptyMap()
        _stats.value = Stats()
        _settings.value = Settings()
    }

    /** Pliki, do których nie prowadzi żadna wiadomość, nie mają po co zajmować miejsca. */
    fun pruneAttachments() {
        val keep = _messages.value.mapNotNullTo(HashSet()) { it.attId }
        runCatching { Attachments.prune(app, keep) }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        val ret = retentionMs()
        // Niedokończone odbiory: po chwili ciszy pytamy nadawcę o brakujące części, potem odpuszczamy.
        assemblies.values.toList().forEach { a ->
            val quiet = now - a.lastAt
            if (quiet > ASSEMBLY_TIMEOUT) {
                assemblies.remove(a.fid)
            } else if (quiet > ASSEMBLY_ASK_AFTER && !a.asked) {
                a.asked = true
                requestMissing(a.fid)
            }
        }
        pruneAttachments()
        if (cache.removeAll { now - it.ts > ret }) saveCache()
        _seenChannels.update { m -> m.filterValues { now - it.lastSeen < 30 * MINUTE } }
        // Nieaktywni od tygodnia i bez rozmowy znikają z listy.
        val talked = _messages.value.mapTo(HashSet()) { it.from }
        _peers.update { m -> m.filterValues { now - it.lastSeen < 7 * DAY || it.id in talked || it.blocked } }
    }

    private fun retentionMs() = _settings.value.retentionHours.coerceIn(1, 24 * 7) * HOUR

    private fun battery(): Int? {
        if (!_settings.value.shareBattery) return null
        return runCatching {
            (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
        }.getOrNull()
    }

    private fun decodeContent(body: String): Content? = runCatching { json.decodeFromString(Content.serializer(), body) }.getOrNull()

    private fun saveMessages() = store.save(MESSAGES, ListSerializer(Message.serializer())) { _messages.value }
    private fun savePeers() = store.save(PEERS, MapSerializer(String.serializer(), Peer.serializer()), 2_000) { _peers.value }
    private fun saveChannels() = store.save(CHANNELS, ListSerializer(Channel.serializer())) { _channels.value }
    private fun saveStats() = store.save(STATS, Stats.serializer(), 5_000) { _stats.value }
    private fun saveCache() {
        val snapshot = cache.filter { it.type != PacketType.FILE }
        store.save(CACHE, ListSerializer(Packet.serializer()), 2_000) { snapshot }
    }

    // endregion

    companion object {
        private const val SETTINGS = "settings.json"
        private const val MESSAGES = "messages.json"
        private const val PEERS = "peers.json"
        private const val CHANNELS = "channels.json"
        private const val UNREAD = "unread.json"
        private const val STATS = "stats.json"
        private const val CACHE = "relay-cache.json"

        const val MAX_TEXT = 2000

        /** Przerwa między częściami załącznika, żeby nie zapchać radia. */
        private const val PART_DELAY = 45L

        /** Górna granica liczby części jednego pliku (4 MB / 12 kB z zapasem). */
        private const val MAX_PARTS = 400

        /** Po tym czasie ciszy prosimy o brakujące części, a po dłuższym porzucamy odbiór. */
        private const val ASSEMBLY_ASK_AFTER = 25_000L
        private const val ASSEMBLY_TIMEOUT = 20 * 60_000L
        private const val CACHE_LIMIT = 400
        private const val SYNC_LIMIT = 200
        private const val MESSAGE_LIMIT = 10_000
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
