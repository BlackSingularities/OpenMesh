package com.blacksingularities.openmesh.mesh

import kotlinx.serialization.Serializable

/** Rodzaje pakietów w sieci. */
@Serializable
enum class PacketType {
    /** Ogłoszenie obecności: nazwa, klucz publiczny, status. Nie jest przechowywane do późniejszego przekazania. */
    HELLO,

    /** Wiadomość na kanale (publicznym albo chronionym hasłem). */
    CHAT,

    /** Wiadomość prywatna – szyfrowana kluczem uzgodnionym ECDH między nadawcą a odbiorcą. */
    DM,

    /** Wezwanie pomocy z pozycją – zawsze jawne, z maksymalnym zasięgiem. */
    SOS,

    /** Potwierdzenie doręczenia wiadomości prywatnej. */
    ACK,

    /** Jedna część załącznika (zdjęcia albo pliku). Części składa odbiorca. */
    FILE,

    /** Prośba o ponowne nadanie brakujących części załącznika. */
    FILE_REQ,
}

/**
 * Pakiet przesyłany między urządzeniami. Podpis obejmuje wszystkie pola poza [ttl] i [hops],
 * które zmieniają węzły pośredniczące. [from] to skrót klucza [pub], więc nadawcy nie da się podrobić.
 */
@Serializable
data class Packet(
    val id: String,
    val type: PacketType,
    val from: String,
    val name: String,
    val pub: String,
    val ts: Long,
    val ttl: Int,
    val hops: Int = 0,
    val to: String? = null,
    val channel: String? = null,
    val body: String = "",
    val enc: Boolean = false,
    val ref: String? = null,
    val sig: String = "",
) {
    fun signedBytes(): ByteArray =
        listOf(id, type.name, from, name, pub, ts, to, channel, body, enc, ref)
            .joinToString("\u001F")
            .encodeToByteArray()
}

/**
 * Część załącznika. Wszystkie części jednego pliku mają to samo [fid]; opis pliku powtarza się
 * w każdej z nich, żeby odbiorca mógł pokazać nazwę i rozmiar od pierwszego odebranego fragmentu.
 */
@Serializable
data class Att(
    val fid: String,
    val name: String = "",
    val mime: String = "",
    val size: Long = 0,
    /** Skrót SHA-256 całego pliku – odbiorca sprawdza, czy złożył go poprawnie. */
    val sha: String = "",
    val idx: Int = 0,
    val cnt: Int = 1,
    /** Fragment pliku zakodowany w base64. */
    val data: String = "",
    /** Dla [PacketType.FILE_REQ]: numery brakujących części. */
    val miss: List<Int> = emptyList(),
)

/** Treść wiadomości (jawna albo zaszyfrowana w [Packet.body]). */
@Serializable
data class Content(
    val text: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val acc: Float? = null,
    val quick: Boolean = false,
    val battery: Int? = null,
    val att: Att? = null,
)

@Serializable
enum class MsgStatus { QUEUED, SENT, DELIVERED }

/** Wiadomość zapisana lokalnie w historii rozmowy. */
@Serializable
data class Message(
    val id: String,
    val convo: String,
    val from: String,
    val fromName: String,
    val ts: Long,
    val text: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val acc: Float? = null,
    val mine: Boolean = false,
    val hops: Int = 0,
    val status: MsgStatus = MsgStatus.SENT,
    val sos: Boolean = false,
    val quick: Boolean = false,
    val encrypted: Boolean = false,
    val battery: Int? = null,
    val receivedAt: Long = ts,
    /** Identyfikator załącznika = nazwa pliku w pamięci aplikacji. */
    val attId: String? = null,
    val attName: String? = null,
    val attMime: String? = null,
    val attSize: Long = 0,
    /** Części odebrane (albo wysłane) i wszystkie – do pokazania postępu. */
    val attParts: Int = 0,
    val attTotal: Int = 0,
    /** Plik jest już kompletny i zapisany na telefonie. */
    val attDone: Boolean = false,
) {
    val hasLocation get() = lat != null && lon != null
    val hasAttachment get() = attId != null
    val isImage get() = attMime?.startsWith("image/") == true
}

/** Znane urządzenie w sieci (bezpośrednio albo przez innych). */
@Serializable
data class Peer(
    val id: String,
    val name: String,
    val pub: String,
    val lastSeen: Long,
    val hops: Int,
    val status: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val locTs: Long? = null,
    val battery: Int? = null,
    val blocked: Boolean = false,
)

/** Kanał rozmowy. Kanał z hasłem szyfruje treść kluczem wyprowadzonym z hasła (PBKDF2). */
@Serializable
data class Channel(
    val name: String,
    val password: String? = null,
    val muted: Boolean = false,
    val joinedAt: Long = 0L,
) {
    val key get() = convoOf(name)
    val secured get() = !password.isNullOrEmpty()

    companion object {
        fun convoOf(name: String) = "ch:" + normalize(name)
        fun normalize(name: String) = name.trim().removePrefix("#").lowercase().replace(Regex("\\s+"), "-").take(32)
    }
}

/** Kanał widziany w sieci, do którego można dołączyć. */
@Serializable
data class SeenChannel(val name: String, val secured: Boolean, val lastSeen: Long, val count: Int = 1)

@Serializable
data class Stats(
    val sent: Long = 0,
    val received: Long = 0,
    val relayed: Long = 0,
    val rejected: Long = 0,
    val connections: Long = 0,
)

@Serializable
enum class Topology { CLUSTER, STAR }

/** Wzór migania latarki alarmowej. */
@Serializable
enum class BeaconPattern { SOS, SLOW, FAST, STEADY }

@Serializable
data class Settings(
    val nick: String = "",
    val onboarded: Boolean = false,
    val meshEnabled: Boolean = true,
    val autoStart: Boolean = true,
    val relay: Boolean = true,
    val ttl: Int = 6,
    val retentionHours: Int = 48,
    val topology: Topology = Topology.CLUSTER,
    val notify: Boolean = true,
    val vibrate: Boolean = true,
    val sosAlarm: Boolean = true,
    val shareBattery: Boolean = true,
    val status: String = "",
    /** Największy rozmiar wysyłanego załącznika (w kilobajtach) – sieć mesh jest wolna. */
    val attachmentMaxKb: Int = 1536,
    /** Zmniejszanie zdjęć przed wysłaniem, żeby zmieściły się w limicie. */
    val shrinkPhotos: Boolean = true,
    val autoUpdate: Boolean = true,
    val autoInstall: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    /** Rozmowy przypięte na górze listy. */
    val pinned: List<String> = emptyList(),
    /** Rozmowy schowane do archiwum. */
    val archived: List<String> = emptyList(),
    val beaconPattern: BeaconPattern = BeaconPattern.SOS,
    val beaconScreen: Boolean = true,
)

const val PUBLIC_CHANNEL = "ogólny"
const val MAX_TTL = 10
const val SOS_TTL = 10

/** Nazwa rozmowy dla widoku: kanał „#nazwa” albo nick rozmówcy. */
fun isDm(convo: String) = convo.startsWith("dm:")
fun dmConvo(peerId: String) = "dm:$peerId"
fun peerOf(convo: String) = convo.removePrefix("dm:")
fun channelOf(convo: String) = convo.removePrefix("ch:")
