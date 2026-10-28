package com.blacksingularities.openmesh.mesh

/**
 * Dzielenie i składanie załączników. Sieć mesh przesyła krótkie pakiety (Nearby przyjmuje
 * do 30 kB), więc plik jedzie w częściach po [CHUNK] bajtów – każda jako osobny, podpisany
 * pakiet, który inne telefony przekazują dalej i przechowują dla nowych sąsiadów.
 *
 * Logika jest czysta (bez Androida), dzięki czemu da się ją sprawdzić testem jednostkowym.
 */
object FileTransfer {
    /** Fragment pliku przed zakodowaniem w base64; po base64 rośnie o jedną trzecią. */
    const val CHUNK = 12_000

    /** Rozsądny górny limit: 4 MB to już ponad 350 części i kilka minut nadawania. */
    const val MAX_BYTES = 4 * 1024 * 1024

    fun partCount(size: Int): Int = if (size <= 0) 1 else (size + CHUNK - 1) / CHUNK

    /** Fragment o numerze [idx] albo null, gdy numer wypada poza plikiem. */
    fun part(bytes: ByteArray, idx: Int): ByteArray? {
        val from = idx.toLong() * CHUNK
        if (idx < 0 || from >= bytes.size) return null
        val to = minOf(bytes.size.toLong(), from + CHUNK).toInt()
        return bytes.copyOfRange(from.toInt(), to)
    }

    /** Wszystkie fragmenty po kolei. */
    fun parts(bytes: ByteArray): List<ByteArray> =
        (0 until partCount(bytes.size)).mapNotNull { part(bytes, it) }

    /** Stan odbioru jednego pliku: części docierają w dowolnej kolejności i mogą się powtarzać. */
    class Assembly(val fid: String, val cnt: Int, val meta: Att) {
        private val slots = arrayOfNulls<ByteArray>(cnt.coerceAtLeast(1))
        var lastAt: Long = 0L
            private set
        var asked: Boolean = false

        val received get() = slots.count { it != null }
        val complete get() = received == slots.size

        /** Zwraca true, gdy to nowa część (a nie powtórka). */
        fun put(idx: Int, data: ByteArray, now: Long): Boolean {
            lastAt = now
            if (idx !in slots.indices || slots[idx] != null) return false
            slots[idx] = data
            return true
        }

        fun missing(): List<Int> = slots.indices.filter { slots[it] == null }

        /** Złożony plik albo null, gdy czegoś jeszcze brakuje. */
        fun bytes(): ByteArray? {
            if (!complete) return null
            val out = ByteArray(slots.sumOf { it!!.size })
            var at = 0
            slots.forEach { chunk ->
                chunk!!.copyInto(out, at)
                at += chunk.size
            }
            return out
        }
    }

    /** Czytelny rozmiar pliku – używany też w opisach wiadomości. */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> ((bytes + 512) / 1024).toString() + " kB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** Nazwa pliku bez katalogów i znaków, które mogłyby wyjść z katalogu aplikacji. */
    fun safeName(name: String, mime: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim().take(80)
        val cleaned = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim('.', ' ')
        if (cleaned.isNotEmpty()) return cleaned
        val ext = when {
            mime.startsWith("image/jpeg") -> ".jpg"
            mime.startsWith("image/png") -> ".png"
            mime.startsWith("image/") -> "." + mime.substringAfter('/').take(5)
            else -> ""
        }
        return "zalacznik$ext"
    }
}
