package com.blacksingularities.openmesh.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.blacksingularities.openmesh.mesh.FileTransfer
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Pliki załączników w pamięci aplikacji. Każdy plik leży pod swoim identyfikatorem (bez
 * rozszerzenia), a nazwa i typ jadą w wiadomości – dzięki temu nic, co przyszło z sieci,
 * nie decyduje o nazwie pliku na dysku.
 *
 * Zdjęcia są przed wysłaniem zmniejszane: przez mesh (Bluetooth, kilkadziesiąt kB/s)
 * nie da się rozsądnie przesłać pełnej klatki z aparatu.
 */
object Attachments {
    /** Dłuższa krawędź zdjęcia po zmniejszeniu. */
    private const val MAX_EDGE = 1280
    private val QUALITIES = intArrayOf(85, 72, 60, 48, 36)

    data class Prepared(val bytes: ByteArray, val name: String, val mime: String, val shrunk: Boolean)

    /** Powód, dla którego pliku nie da się wysłać – gotowy komunikat dla użytkownika. */
    class TooBig(val size: Long, val limit: Long) : Exception()

    fun dir(ctx: Context): File = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun file(ctx: Context, id: String): File = File(dir(ctx), id.replace(Regex("[^a-zA-Z0-9]"), ""))

    fun exists(ctx: Context, id: String) = file(ctx, id).let { it.exists() && it.length() > 0 }

    fun save(ctx: Context, id: String, bytes: ByteArray): File = file(ctx, id).also { it.writeBytes(bytes) }

    fun read(ctx: Context, id: String): ByteArray? = runCatching { file(ctx, id).readBytes() }.getOrNull()

    fun delete(ctx: Context, id: String) {
        runCatching { file(ctx, id).delete() }
    }

    fun totalBytes(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Usuwa pliki, do których nie prowadzi już żadna wiadomość. */
    fun prune(ctx: Context, keep: Set<String>) {
        val wanted = keep.map { file(ctx, it).name }.toSet()
        dir(ctx).listFiles()?.forEach { if (it.name !in wanted) it.delete() }
    }

    /**
     * Czyta wskazany plik i – gdy to zdjęcie przekraczające limit – zmniejsza je tak,
     * by zmieściło się w [maxBytes]. Zwraca gotową treść albo rzuca [TooBig].
     */
    fun prepare(ctx: Context, uri: Uri, maxBytes: Long, shrinkPhotos: Boolean): Prepared {
        val (rawName, rawMime, declared) = describe(ctx, uri)
        val mime = rawMime ?: "application/octet-stream"
        val limit = maxBytes.coerceAtMost(FileTransfer.MAX_BYTES.toLong())

        if (mime.startsWith("image/") && shrinkPhotos) {
            val shrunk = shrinkImage(ctx, uri, limit)
            if (shrunk != null) {
                return Prepared(shrunk, FileTransfer.safeName(rawName ?: "zdjecie.jpg", "image/jpeg"), "image/jpeg", true)
            }
        }
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Nie udało się odczytać pliku")
        if (bytes.size > limit) throw TooBig(declared.takeIf { it > 0 } ?: bytes.size.toLong(), limit)
        return Prepared(bytes, FileTransfer.safeName(rawName ?: "", mime), mime, false)
    }

    /** Nazwa, typ i rozmiar podane przez aplikację, z której pochodzi plik. */
    private fun describe(ctx: Context, uri: Uri): Triple<String?, String?, Long> {
        var name: String? = null
        var size = 0L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        val mime = ctx.contentResolver.getType(uri)
        return Triple(name, mime, size)
    }

    /** Zdjęcie przeskalowane i zapisane jako JPEG; kolejne próby obniżają jakość. */
    private fun shrinkImage(ctx: Context, uri: Uri, limit: Long): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching null

        var sample = 1
        while (longest / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return@runCatching null
        val scale = MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        try {
            QUALITIES.forEach { q ->
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)
                val bytes = out.toByteArray()
                if (bytes.size <= limit) return@runCatching bytes
            }
            null
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }.getOrNull()

    /** Miniatura do pokazania w rozmowie; null, gdy pliku nie ma albo to nie obrazek. */
    fun thumbnail(ctx: Context, id: String, maxPx: Int = 720): Bitmap? = runCatching {
        val f = file(ctx, id)
        if (!f.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching null
        var sample = 1
        while (longest / sample > maxPx) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Kopia pliku z właściwą nazwą, gotowa do otwarcia albo wysłania inną aplikacją. */
    private fun outside(ctx: Context, id: String, name: String): Uri? = runCatching {
        val src = file(ctx, id)
        if (!src.exists()) return@runCatching null
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val dst = File(dir, FileTransfer.safeName(name, ""))
        src.copyTo(dst, overwrite = true)
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", dst)
    }.getOrNull()

    fun openIntent(ctx: Context, id: String, name: String, mime: String): Intent? {
        val uri = outside(ctx, id, name) ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(ctx: Context, id: String, name: String, mime: String): Intent? {
        val uri = outside(ctx, id, name) ?: return null
        return Intent(Intent.ACTION_SEND)
            .setType(mime.ifBlank { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
