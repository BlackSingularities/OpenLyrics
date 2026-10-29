package com.blacksingularities.openlyrics.update

import com.blacksingularities.openlyrics.i18n.tr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File

/** Repozytorium z wydaniami aplikacji. */
const val UPDATE_REPO = "BlackSingularities/OpenLyrics"

/**
 * Wydania i pliki pobierane są z redzikowski.com (zamiennik GitHub API dla aplikacji z portfolio),
 * tak jak w pozostałych aplikacjach – repozytorium jest prywatne, a serwer dokłada token.
 */
const val UPDATE_API = "https://redzikowski.com/api/github/repos/$UPDATE_REPO/releases/latest"
const val UPDATE_HOST = "https://redzikowski.com/"
const val PROJECT_PAGE = "https://redzikowski.com/portfolio/openlyrics"

data class ReleaseInfo(
    val version: String,
    val notes: String,
    /** Plik dla tej platformy (APK albo instalator EXE). */
    val assetUrl: String,
    val assetName: String,
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

/** Najnowsze wydanie z odpowiedzi w kształcie GitHub API; [assetSuffix] – końcówka pliku platformy. */
fun parseRelease(json: String, assetSuffix: String): ReleaseInfo {
    val o = Json.parseToJsonElement(json).jsonObject
    fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    var url = ""
    var name = ""
    var size = 0L
    for (e in o["assets"] as? JsonArray ?: JsonArray(emptyList())) {
        val a = e as? JsonObject ?: continue
        if (a.str("name").endsWith(assetSuffix, ignoreCase = true)) {
            url = a.str("browser_download_url")
            name = a.str("name")
            size = (a["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            break
        }
    }
    if (url.isEmpty()) error(tr("Wydanie nie zawiera pliku {0}", assetSuffix.substringAfterLast('.').uppercase()))
    // Plik pobieramy wyłącznie z redzikowski.com.
    if (!url.startsWith(UPDATE_HOST)) error(tr("Nieoczekiwany adres pliku aktualizacji"))
    return ReleaseInfo(
        version = o.str("tag_name").removePrefix("v"),
        notes = o.str("body").substringBefore("\n---").trim(),
        assetUrl = url,
        assetName = name,
        sizeBytes = size,
        pageUrl = o.str("html_url").ifEmpty { PROJECT_PAGE },
    )
}

/** Porównanie numeryczne wersji „1.1.12” > „1.1.9”; przyrostki typu „-dev” są pomijane. */
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
