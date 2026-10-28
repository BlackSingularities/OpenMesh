package com.blacksingularities.openmesh.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val assetUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Najnowsze wydanie z odpowiedzi GitHub API; [assetSuffix] – końcówka pliku (np. „.apk”). */
fun parseRelease(json: String, assetSuffix: String): ReleaseInfo {
    val o = Json.parseToJsonElement(json).jsonObject
    fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    var url = ""
    var size = 0L
    for (e in o["assets"] as? JsonArray ?: JsonArray(emptyList())) {
        val a = e as? JsonObject ?: continue
        if (a.str("name").endsWith(assetSuffix)) {
            url = a.str("browser_download_url")
            size = (a["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            break
        }
    }
    if (url.isEmpty()) error("Wydanie nie zawiera pliku " + assetSuffix.removePrefix(".").uppercase())
    return ReleaseInfo(
        version = o.str("tag_name").removePrefix("v"),
        notes = o.str("body").substringBefore("\n---").trim(),
        assetUrl = url,
        sizeBytes = size,
        pageUrl = o.str("html_url"),
    )
}

/** Porównanie numeryczne wersji: „0.1.12” > „0.1.9”; przyrostki typu „-dev” są pomijane. */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
