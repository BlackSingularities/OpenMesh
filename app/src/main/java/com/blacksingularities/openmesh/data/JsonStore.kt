package com.blacksingularities.openmesh.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Zapis danych do plików JSON w katalogu aplikacji. Zapisy są odraczane i scalane,
 * a plik podmieniany atomowo (najpierw .tmp, potem zmiana nazwy) – bez uszkodzeń przy przerwaniu.
 */
class JsonStore(private val dir: File, private val scope: CoroutineScope) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
    }
    private val jobs = mutableMapOf<String, Job>()

    init {
        dir.mkdirs()
    }

    fun <T> load(name: String, serializer: KSerializer<T>): T? = runCatching {
        val f = File(dir, name)
        if (!f.exists()) null else json.decodeFromString(serializer, f.readText())
    }.getOrNull()

    /** Zapis z opóźnieniem [delayMs]; kolejne wywołania w tym czasie dają jeden zapis z najnowszą wartością. */
    fun <T> save(name: String, serializer: KSerializer<T>, delayMs: Long = 600, value: () -> T) {
        synchronized(jobs) {
            jobs[name]?.cancel()
            jobs[name] = scope.launch {
                delay(delayMs)
                val text = json.encodeToString(serializer, value())
                withContext(Dispatchers.IO) { write(name, text) }
            }
        }
    }

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val target = File(dir, name)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun wipe() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        dir.listFiles()?.forEach { it.delete() }
    }
}
