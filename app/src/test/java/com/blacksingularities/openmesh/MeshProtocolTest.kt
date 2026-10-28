package com.blacksingularities.openmesh

import com.blacksingularities.openmesh.mesh.Channel
import com.blacksingularities.openmesh.mesh.Content
import com.blacksingularities.openmesh.mesh.Crypto
import com.blacksingularities.openmesh.mesh.Packet
import com.blacksingularities.openmesh.mesh.PacketType
import com.blacksingularities.openmesh.update.isNewer
import com.blacksingularities.openmesh.update.parseRelease
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshProtocolTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun signed(type: PacketType, body: String, to: String? = null): Pair<Packet, java.security.KeyPair> {
        val k = Crypto.generate()
        val pub = Crypto.encodePub(k.public)
        val raw = Packet(Crypto.randomId(), type, Crypto.peerId(pub), "Ania", pub, 1_700_000_000_000, 6, to = to, body = body)
        return raw.copy(sig = Crypto.sign(k.private, raw.signedBytes())) to k
    }

    @Test
    fun signatureSurvivesJsonAndRelayFields() {
        val (p, _) = signed(PacketType.CHAT, json.encodeToString(Content.serializer(), Content("Cześć z Kasprowego", 49.23, 19.98)))
        val wire = json.encodeToString(Packet.serializer(), p)
        val back = json.decodeFromString(Packet.serializer(), wire).copy(ttl = 2, hops = 4)
        assertTrue(Crypto.verify(back.pub, back.signedBytes(), back.sig))
        assertEquals(back.from, Crypto.peerId(back.pub))
    }

    @Test
    fun tamperedPacketIsRejected() {
        val (p, _) = signed(PacketType.CHAT, "oryginał")
        val forged = p.copy(body = "podróbka")
        assertFalse(Crypto.verify(forged.pub, forged.signedBytes(), forged.sig))
        val renamed = p.copy(name = "Ktoś inny")
        assertFalse(Crypto.verify(renamed.pub, renamed.signedBytes(), renamed.sig))
    }

    @Test
    fun directMessageKeyIsSymmetric() {
        val a = Crypto.generate()
        val b = Crypto.generate()
        val kab = Crypto.dmKey(a.private, Crypto.encodePub(b.public))
        val kba = Crypto.dmKey(b.private, Crypto.encodePub(a.public))
        assertTrue(kab.contentEquals(kba))
        val sealed = Crypto.seal(kab, "tajne 🙂")
        assertEquals("tajne 🙂", Crypto.open(kba, sealed))
        val c = Crypto.generate()
        val wrong = Crypto.dmKey(c.private, Crypto.encodePub(a.public))
        assertNull(Crypto.open(wrong, sealed))
    }

    @Test
    fun channelPasswordDerivesKey() {
        val k1 = Crypto.channelKey("ekipa", "kremówka")
        val k2 = Crypto.channelKey("ekipa", "kremówka")
        val other = Crypto.channelKey("ekipa", "inne")
        assertTrue(k1.contentEquals(k2))
        assertFalse(k1.contentEquals(other))
        assertNull(Crypto.open(other, Crypto.seal(k1, "x")))
    }

    @Test
    fun channelNamesNormalize() {
        assertEquals("schronisko-murowaniec", Channel.normalize("  #Schronisko  Murowaniec "))
        assertEquals("ch:ogólny", Channel.convoOf("#Ogólny"))
    }

    @Test
    fun fingerprintsDiffer() {
        val a = Crypto.encodePub(Crypto.generate().public)
        val b = Crypto.encodePub(Crypto.generate().public)
        assertNotEquals(Crypto.fingerprint(a), Crypto.fingerprint(b))
        assertEquals(29, Crypto.fingerprint(a).length)
    }

    @Test
    fun versionComparison() {
        assertTrue(isNewer("0.1.1", "0.1.0"))
        assertTrue(isNewer("0.1.0.12", "0.1.0"))
        assertTrue(isNewer("0.2.0", "0.1.9"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.1.0-dev"))
    }

    @Test
    fun releaseParsing() {
        val body = """
            {"tag_name":"v0.1.3","html_url":"https://github.com/x/y/releases/tag/v0.1.3","body":"Zmiany\n---\nstopka",
             "assets":[{"name":"OpenMesh-0.1.3.apk","browser_download_url":"https://example/a.apk","size":1234}]}
        """.trimIndent()
        val r = parseRelease(body, ".apk")
        assertEquals("0.1.3", r.version)
        assertEquals("Zmiany", r.notes)
        assertEquals(1234L, r.sizeBytes)
    }
}
