package com.blacksingularities.openmesh

import com.blacksingularities.openmesh.mesh.Att
import com.blacksingularities.openmesh.mesh.Content
import com.blacksingularities.openmesh.mesh.Crypto
import com.blacksingularities.openmesh.mesh.FileTransfer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FileTransferTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun file(size: Int) = Random(7).nextBytes(size)

    @Test
    fun partsCoverWholeFile() {
        val bytes = file(FileTransfer.CHUNK * 3 + 517)
        val parts = FileTransfer.parts(bytes)
        assertEquals(4, parts.size)
        assertEquals(FileTransfer.partCount(bytes.size), parts.size)
        assertEquals(bytes.size, parts.sumOf { it.size })
        assertEquals(517, parts.last().size)
        assertNull(FileTransfer.part(bytes, parts.size))
    }

    @Test
    fun emptyFileIsOnePart() {
        assertEquals(1, FileTransfer.partCount(0))
    }

    @Test
    fun assemblyRebuildsFileFromShuffledParts() {
        val bytes = file(FileTransfer.CHUNK * 2 + 10)
        val parts = FileTransfer.parts(bytes)
        val meta = Att(fid = "abc", cnt = parts.size)
        val a = FileTransfer.Assembly("abc", parts.size, meta)

        parts.indices.shuffled(kotlin.random.Random(3)).forEach { i ->
            assertTrue(a.put(i, parts[i], 1L))
        }
        assertTrue(a.complete)
        assertArrayEquals(bytes, a.bytes())
        assertEquals(Crypto.hex(Crypto.sha256(bytes)), Crypto.hex(Crypto.sha256(a.bytes()!!)))
    }

    @Test
    fun assemblyReportsMissingPartsAndIgnoresRepeats() {
        val bytes = file(FileTransfer.CHUNK * 3)
        val parts = FileTransfer.parts(bytes)
        val a = FileTransfer.Assembly("abc", parts.size, Att(fid = "abc", cnt = parts.size))

        assertTrue(a.put(0, parts[0], 1L))
        assertFalse(a.put(0, parts[0], 2L))
        assertFalse(a.put(99, parts[0], 3L))
        assertEquals(listOf(1, 2), a.missing())
        assertNull(a.bytes())
        assertEquals(1, a.received)

        a.put(1, parts[1], 4L)
        a.put(2, parts[2], 5L)
        assertTrue(a.missing().isEmpty())
        assertArrayEquals(bytes, a.bytes())
    }

    @Test
    fun chunkPacketFitsNearbyPayload() {
        val bytes = file(FileTransfer.CHUNK)
        val content = Content(
            att = Att(
                fid = Crypto.randomId(),
                name = "dlugie-zdjecie-ze-szlaku-przez-przelecz.jpg",
                mime = "image/jpeg",
                size = 4L * 1024 * 1024,
                sha = Crypto.hex(Crypto.sha256(bytes)),
                idx = 399,
                cnt = 400,
                data = Crypto.b64(bytes),
            ),
        )
        val wire = json.encodeToString(Content.serializer(), content)
        // Nawet po zaszyfrowaniu (base64 szyfrogramu) pakiet musi zmiescic sie w limicie Nearby.
        val sealed = Crypto.seal(ByteArray(32), wire)
        assertTrue("szyfrogram " + sealed.length, sealed.length < 30 * 1024)
    }

    @Test
    fun safeNameStripsPathsAndKeepsExtension() {
        assertEquals("zdjecie.jpg", FileTransfer.safeName("../../zdjecie.jpg", "image/jpeg"))
        assertEquals("raport_2026.pdf", FileTransfer.safeName("raport:2026.pdf", "application/pdf"))
        assertEquals("zalacznik.jpg", FileTransfer.safeName("   ", "image/jpeg"))
        assertEquals("zalacznik", FileTransfer.safeName("", "application/octet-stream"))
    }

    @Test
    fun sizesAreReadable() {
        assertEquals("512 B", FileTransfer.formatSize(512))
        assertEquals("12 kB", FileTransfer.formatSize(12 * 1024))
        assertEquals("1.5 MB", FileTransfer.formatSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun attachmentSurvivesJsonRoundTrip() {
        val bytes = file(3000)
        val att = Att(fid = "f1", name = "a.bin", mime = "application/octet-stream", size = 3000, idx = 0, cnt = 1, data = Crypto.b64(bytes))
        val wire = json.encodeToString(Content.serializer(), Content(att = att))
        val back = json.decodeFromString(Content.serializer(), wire).att
        assertEquals(att, back)
        assertArrayEquals(bytes, Crypto.unb64(back!!.data))
    }
}
