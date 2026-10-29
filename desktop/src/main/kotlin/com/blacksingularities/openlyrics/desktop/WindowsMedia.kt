package com.blacksingularities.openlyrics.desktop

import com.blacksingularities.openlyrics.media.MediaSource
import com.blacksingularities.openlyrics.media.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.Writer

/**
 * Odtwarzany utwór z sesji multimediów Windows (te same dane, które pokazuje systemowy panel
 * głośności): Spotify, AIMP, foobar2000, MusicBee, Groove, przeglądarki… Odczyt robi mały
 * mostek OpenLyricsMedia.exe, uruchamiany w tle i wznawiany po awarii.
 */
class WindowsMedia(private val bridge: File?) : MediaSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _now = MutableStateFlow<NowPlaying?>(null)
    private val _available = MutableStateFlow(bridge?.exists() == true)
    override val nowPlaying: StateFlow<NowPlaying?> = _now
    override val available: StateFlow<Boolean> = _available

    @Volatile private var process: Process? = null
    @Volatile private var stdin: Writer? = null

    init {
        if (bridge != null && bridge.exists()) scope.launch { runLoop(bridge) }
    }

    private suspend fun runLoop(exe: File) {
        var failures = 0
        while (true) {
            try {
                val p = ProcessBuilder(exe.absolutePath).redirectErrorStream(false).start()
                process = p
                stdin = p.outputStream.bufferedWriter(Charsets.UTF_8)
                _available.value = true
                p.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        handle(line)
                        failures = 0
                    }
                }
                p.waitFor()
            } catch (_: Throwable) {
            }
            failures++
            if (failures > 5) _available.value = false
            _now.value = null
            delay((1_000L * failures).coerceAtMost(15_000))
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun handle(line: String) {
        val o = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        fun JsonObject.s(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        fun JsonObject.l(k: String) = (get(k) as? JsonPrimitive)?.longOrNull ?: 0L
        if (o.containsKey("none") || o.containsKey("error")) {
            if (o.containsKey("none")) _now.value = null
            return
        }
        val title = o.s("title")
        if (title.isBlank()) {
            _now.value = null
            return
        }
        val np = NowPlaying(
            player = playerName(o.s("app")),
            title = title,
            artist = o.s("artist"),
            album = o.s("album"),
            durationMs = o.l("duration"),
            positionMs = o.l("position"),
            capturedAt = o.l("at").takeIf { it > 0 } ?: System.currentTimeMillis(),
            playing = (o["playing"] as? JsonPrimitive)?.booleanOrNull == true,
            rate = ((o["rate"] as? JsonPrimitive)?.doubleOrNull ?: 1.0).toFloat().takeIf { it > 0f } ?: 1f,
        ).tidy()
        _now.value = np
    }

    private fun send(cmd: String) {
        runCatching {
            stdin?.apply {
                write(cmd + "\n")
                flush()
            }
        }
    }

    override fun playPause() = send("toggle")
    override fun next() = send("next")
    override fun previous() = send("prev")
    override fun seekTo(positionMs: Long) = send("seek $positionMs")

    fun close() {
        send("quit")
        runCatching { process?.destroy() }
    }

    companion object {
        /** Czytelna nazwa odtwarzacza z identyfikatora aplikacji (AUMID albo nazwy pliku). */
        fun playerName(app: String): String {
            val a = app.lowercase()
            return when {
                "spotify" in a -> "Spotify"
                "aimp" in a -> "AIMP"
                "foobar" in a -> "foobar2000"
                "musicbee" in a -> "MusicBee"
                "zunemusic" in a || "groove" in a -> "Groove"
                "microsoft.media.player" in a || "zunevideo" in a -> "Media Player"
                "msedge" in a -> "Microsoft Edge"
                "chrome" in a -> "Chrome"
                "firefox" in a -> "Firefox"
                "opera" in a -> "Opera"
                "vlc" in a -> "VLC"
                "itunes" in a || "applemusic" in a -> "Apple Music"
                "tidal" in a -> "TIDAL"
                "deezer" in a -> "Deezer"
                "winamp" in a -> "Winamp"
                "dopamine" in a -> "Dopamine"
                "mediamonkey" in a -> "MediaMonkey"
                else -> app.substringAfterLast('\\').substringBefore('!').removeSuffix(".exe").substringAfterLast('.').ifBlank { app }
            }
        }
    }
}
