package com.blacksingularities.openmesh

import com.blacksingularities.openmesh.data.Export
import com.blacksingularities.openmesh.mesh.BeaconPattern
import com.blacksingularities.openmesh.mesh.Message
import com.blacksingularities.openmesh.mesh.MsgStatus
import com.blacksingularities.openmesh.util.Beacon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private fun msg(
        id: String,
        text: String,
        mine: Boolean = false,
        lat: Double? = null,
        lon: Double? = null,
        sos: Boolean = false,
        status: MsgStatus = MsgStatus.SENT,
        ts: Long = 1_700_000_000_000,
    ) = Message(
        id = id,
        convo = "ch:ogólny",
        from = if (mine) "me" else "ania",
        fromName = if (mine) "Ja" else "Ania",
        ts = ts,
        text = text,
        lat = lat,
        lon = lon,
        mine = mine,
        sos = sos,
        status = status,
    )

    @Test
    fun transcriptKeepsAuthorsOrderAndPositions() {
        val out = Export.transcript(
            convo = "ch:ogólny",
            title = "#ogólny",
            messages = listOf(
                msg("2", "Jesteśmy na przełęczy", lat = 49.2321, lon = 19.9810, ts = 1_700_000_600_000),
                msg("1", "Cześć", ts = 1_700_000_000_000),
            ),
            myName = "Marek",
        )
        assertTrue(out.contains("#ogólny"))
        assertTrue(out.contains("Ania: Cześć"))
        assertTrue(out.contains("49.23210"))
        // Kolejność chronologiczna niezależnie od kolejności wejścia.
        assertTrue(out.indexOf("Cześć") < out.indexOf("przełęczy"))
    }

    @Test
    fun transcriptMarksSosAndQueuedMessages() {
        val out = Export.transcript(
            convo = "ch:ogólny",
            title = "#ogólny",
            messages = listOf(msg("3", "Potrzebuję pomocy", mine = true, sos = true, status = MsgStatus.QUEUED)),
            myName = "Marek",
        )
        assertTrue(out.contains("Marek: SOS – Potrzebuję pomocy"))
        assertTrue(out.contains("nie wyszła jeszcze do sieci"))
    }

    @Test
    fun fullReportCoversEveryConversation() {
        val out = Export.fullReport(
            messages = listOf(msg("1", "Na kanale"), msg("4", "Prywatnie").copy(convo = "dm:ania")),
            channels = emptyList(),
            peers = emptyMap(),
            myId = "me",
            myName = "Marek",
        )
        assertTrue(out.contains("Na kanale"))
        assertTrue(out.contains("Prywatnie"))
        assertTrue(out.contains("Rozmowa prywatna"))
    }

    @Test
    fun fileNameIsSafeAndKeepsPolishLetters() {
        val name = Export.fileName("#schronisko pod Łomnicą/../")
        assertTrue(name.startsWith("OpenMesh-"))
        assertTrue(name.endsWith(".txt"))
        assertTrue(name.contains("Łomnicą"))
        assertEquals(-1, name.indexOf('/'))
    }

    @Test
    fun everyBeaconPatternIsDescribed() {
        BeaconPattern.entries.forEach { p ->
            assertTrue(Beacon.label(p).isNotBlank())
            assertTrue(Beacon.description(p).isNotBlank())
        }
    }
}
