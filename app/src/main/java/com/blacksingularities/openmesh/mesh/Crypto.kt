package com.blacksingularities.openmesh.mesh

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kryptografia sieci: tożsamość na krzywej P-256, podpisy ECDSA, uzgadnianie klucza ECDH
 * dla wiadomości prywatnych i AES-256-GCM dla treści. Wszystko z biblioteki standardowej Androida.
 */
object Crypto {
    private val random = SecureRandom()
    private val pubCache = ConcurrentHashMap<String, PublicKey>()

    fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    fun encodePub(k: PublicKey) = b64(k.encoded)
    fun encodePriv(k: PrivateKey) = b64(k.encoded)

    fun decodePub(s: String): PublicKey = pubCache.getOrPut(s) {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(s)))
    }

    fun decodePriv(s: String): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unb64(s)))

    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
        parts.forEach { update(it) }
        digest()
    }

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** Identyfikator urządzenia: pierwsze 8 bajtów skrótu klucza publicznego (16 znaków hex). */
    fun peerId(pubB64: String): String = runCatching { hex(sha256(unb64(pubB64)).copyOf(8)) }.getOrDefault("")

    /** Odcisk klucza do porównania „na głos” – 6 grup po 4 znaki. */
    fun fingerprint(pubB64: String): String = runCatching {
        hex(sha256(unb64(pubB64)).copyOf(12)).uppercase().chunked(4).joinToString(" ")
    }.getOrDefault("—")

    fun randomId(): String = ByteArray(12).also { random.nextBytes(it) }.let(::hex)

    fun sign(priv: PrivateKey, data: ByteArray): String = Signature.getInstance("SHA256withECDSA").run {
        initSign(priv)
        update(data)
        b64(sign())
    }

    fun verify(pubB64: String, data: ByteArray, sig: String): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(decodePub(pubB64))
            update(data)
            verify(unb64(sig))
        }
    }.getOrDefault(false)

    /** Klucz wiadomości prywatnych: ECDH(moje prywatne, ich publiczne) → SHA-256 z etykietą protokołu. */
    fun dmKey(priv: PrivateKey, theirPubB64: String): ByteArray {
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(priv)
            doPhase(decodePub(theirPubB64), true)
            generateSecret()
        }
        return sha256(secret, "openmesh-dm-v1".encodeToByteArray())
    }

    /** Klucz kanału chronionego hasłem: PBKDF2-HMAC-SHA256, sól = nazwa kanału. */
    fun channelKey(channel: String, password: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), ("openmesh-ch:" + channel).encodeToByteArray(), 20_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** AES-256-GCM: wynik = base64(iv[12] || szyfrogram+tag). */
    fun seal(key: ByteArray, plain: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return b64(iv + c.doFinal(plain.encodeToByteArray()))
    }

    fun open(key: ByteArray, sealed: String): String? = runCatching {
        val raw = unb64(sealed)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, raw, 0, 12))
        c.doFinal(raw, 12, raw.size - 12).decodeToString()
    }.getOrNull()
}
