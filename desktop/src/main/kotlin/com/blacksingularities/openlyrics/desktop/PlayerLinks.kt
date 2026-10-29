package com.blacksingularities.openlyrics.desktop

import com.blacksingularities.openlyrics.data.AppSettings
import com.blacksingularities.openlyrics.media.NowPlaying
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

/**
 * Połączenia z odtwarzaczami, które podają ścieżkę grającego pliku – wtedy nie trzeba szukać
 * utworu w indeksie folderów. AIMP i Winamp obsługuje mostek multimediów, foobar2000 i VLC –
 * ich lokalne interfejsy HTTP (każde połączenie włącza się osobno w ustawieniach).
 */
object PlayerLinks {
    private val json = Json { ignoreUnknownKeys = true }

    fun currentFile(track: NowPlaying, s: AppSettings): File? {
        val p = track.player.lowercase()
        val path = when {
            "aimp" in p -> track.fileHint.takeIf { s.playerAimp }
            "winamp" in p -> track.fileHint.takeIf { s.playerWinamp }
            "foobar" in p -> if (s.playerFoobar) runCatching { foobar(s.foobarPort) }.getOrNull() else null
            "vlc" in p -> if (s.playerVlc) runCatching { vlc(s.vlcPort, s.vlcPassword) }.getOrNull() else null
            else -> null
        }
        return path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
    }

    private fun get(url: String, auth: String? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 1_500
            readTimeout = 2_500
            if (auth != null) setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(auth.toByteArray()))
        }
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** foobar2000 z wtyczką beefweb: kolumna %path% aktywnego utworu. */
    fun foobar(port: Int): String? {
        val o = json.parseToJsonElement(get("http://127.0.0.1:$port/api/player?columns=%25path%25")).jsonObject
        val item = (o["player"] as? JsonObject)?.get("activeItem") as? JsonObject ?: return null
        return ((item["columns"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.content
    }

    /** VLC z interfejsem HTTP (Lua): adres bieżącej pozycji listy odtwarzania. */
    fun vlc(port: Int, password: String): String? {
        val o = json.parseToJsonElement(get("http://127.0.0.1:$port/requests/playlist.json", ":$password")).jsonObject
        fun find(n: JsonObject): String? {
            if ((n["current"] as? JsonPrimitive)?.content == "current") return (n["uri"] as? JsonPrimitive)?.content
            for (c in (n["children"] as? JsonArray).orEmpty()) (c as? JsonObject)?.let(::find)?.let { return it }
            return null
        }
        val uri = find(o) ?: return null
        return if (uri.startsWith("file:")) File(URI(uri)).absolutePath else null
    }

    /** Test połączenia z ustawień: null – działa, inaczej opis błędu. */
    fun test(kind: String, s: AppSettings): String? = runCatching {
        when (kind) {
            "foobar" -> foobar(s.foobarPort)
            "vlc" -> vlc(s.vlcPort, s.vlcPassword)
            else -> null
        }
        null
    }.getOrElse { it.message ?: it.toString() }
}
