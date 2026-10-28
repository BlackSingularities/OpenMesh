package com.blacksingularities.openmesh.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.MsgStatus
import com.blacksingularities.openmesh.mesh.Peer
import com.blacksingularities.openmesh.mesh.channelOf
import com.blacksingularities.openmesh.mesh.isDm
import com.blacksingularities.openmesh.util.Geo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Eksport historii do pliku tekstowego: zapis rozmowy, który da się wysłać, wydrukować
 * albo przekazać ratownikom, gdy znów jest zasięg. Nic nie opuszcza telefonu samo z siebie –
 * plik powstaje w pamięci podręcznej i jest udostępniany dopiero przez systemowe okno „Udostępnij”.
 */
object Export {
    private val pl = Locale("pl", "PL")
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", pl)
    private val fileStamp = SimpleDateFormat("yyyy-MM-dd-HHmm", pl)

    /** Zapis jednej rozmowy: nagłówek i kolejne wiadomości z czasem, autorem i pozycją. */
    fun transcript(convo: String, title: String, messages: List<Message>, myName: String): String {
        val sb = StringBuilder()
        sb.append("OpenMesh – zapis rozmowy\n")
        sb.append(title).append('\n')
        sb.append("Wyeksportowano: ").append(stamp.format(Date())).append('\n')
        sb.append("Wiadomości: ").append(messages.size).append("\n\n")
        messages.sortedBy { it.ts }.forEach { m ->
            sb.append('[').append(stamp.format(Date(m.ts))).append("] ")
            sb.append(if (m.mine) myName else m.fromName).append(": ")
            if (m.sos) sb.append("SOS – ")
            sb.append(m.text.ifBlank { if (m.hasLocation) "(pozycja)" else "(brak treści)" })
            val lat = m.lat
            val lon = m.lon
            if (lat != null && lon != null) {
                sb.append("\n    pozycja: ").append(Geo.formatCoords(lat, lon))
                sb.append(" (").append(Geo.formatDms(lat, lon)).append(')')
                m.acc?.let { sb.append(", dokładność ").append(Geo.formatDistance(it)) }
            }
            if (m.hops > 0) sb.append("\n    doszła przez ").append(m.hops).append(" węzłów")
            if (m.mine && m.status == MsgStatus.QUEUED) sb.append("\n    (nie wyszła jeszcze do sieci)")
            sb.append("\n\n")
        }
        return sb.toString()
    }

    /** Zapis wszystkich rozmów, znanych osób i statystyk – pełna kopia czytelna dla człowieka. */
    fun fullReport(messages: List<Message>, channels: List<Channel>, peers: Map<String, Peer>, myId: String, myName: String): String {
        val sb = StringBuilder()
        sb.append("OpenMesh – kopia historii\n")
        sb.append("Urządzenie: ").append(myName).append(" (").append(myId.take(12)).append(")\n")
        sb.append("Wyeksportowano: ").append(stamp.format(Date())).append("\n\n")

        sb.append("ZNANE OSOBY (").append(peers.size).append(")\n")
        peers.values.sortedByDescending { it.lastSeen }.forEach { p ->
            sb.append("- ").append(p.name).append(" · ").append(p.id.take(12))
            sb.append(" · ostatnio ").append(stamp.format(Date(p.lastSeen)))
            val lat = p.lat
            val lon = p.lon
            if (lat != null && lon != null) sb.append(" · ").append(Geo.formatCoords(lat, lon))
            if (p.blocked) sb.append(" · zablokowana")
            sb.append('\n')
        }
        sb.append('\n')

        val byConvo = messages.groupBy { it.convo }
        val titles = byConvo.keys.sortedBy { it }
        titles.forEach { convo ->
            val title = if (isDm(convo)) {
                val other = byConvo.getValue(convo).firstOrNull { !it.mine }?.fromName
                "Rozmowa prywatna: " + (other ?: convo)
            } else {
                "#" + channelOf(convo)
            }
            sb.append("==== ").append(title).append(" ====\n\n")
            sb.append(transcript(convo, title, byConvo.getValue(convo), myName))
        }
        channels.filter { byConvo[it.key] == null }.forEach {
            sb.append("==== #").append(it.name).append(" ====\n\n(brak wiadomości)\n\n")
        }
        return sb.toString()
    }

    /** Zapisuje tekst w pamięci podręcznej i zwraca odnośnik do udostępnienia. */
    fun write(ctx: Context, name: String, text: String): Uri {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        // Stare eksporty nie zostają na telefonie dłużej niż dobę.
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 24 * 60 * 60_000L) it.delete() }
        val file = File(dir, fileName(name))
        file.writeText(text)
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    }

    fun fileName(name: String): String {
        val safe = name.trim().removePrefix("#").replace(Regex("""[^\p{L}\p{N}._-]+"""), "-").trim('-').ifEmpty { "rozmowa" }
        return "OpenMesh-" + safe.take(40) + "-" + fileStamp.format(Date()) + ".txt"
    }

    /** Systemowe okno „Udostępnij” z gotowym plikiem. */
    fun shareIntent(uri: Uri, subject: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
