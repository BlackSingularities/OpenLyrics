package com.blacksingularities.openlyrics.lyrics

import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/** Wynik wyszukiwania w LRCLIB (otwarta, darmowa baza tekstów z czasami). */
data class LyricsCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
) {
    val hasSynced: Boolean get() = !synced.isNullOrBlank()

    fun toLyrics(): Lyrics = when {
        instrumental -> Lyrics(emptyList(), synced = false, source = LyricsSource.LRCLIB, instrumental = true)
        hasSynced -> Lrc.parse(synced!!, LyricsSource.LRCLIB)
        else -> Lrc.plain(plain.orEmpty(), LyricsSource.LRCLIB)
    }
}

object LrcLib {
    private const val API = "https://lrclib.net/api"
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun parse(o: JsonObject) = LyricsCandidate(
        id = (o["id"] as? JsonPrimitive)?.longOrNull ?: 0,
        title = o.str("trackName").orEmpty(),
        artist = o.str("artistName").orEmpty(),
        album = o.str("albumName").orEmpty(),
        durationMs = (((o["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) * 1000).toLong(),
        synced = o.str("syncedLyrics"),
        plain = o.str("plainLyrics"),
        instrumental = (o["instrumental"] as? JsonPrimitive)?.booleanOrNull == true,
    )

    /** Dokładne dopasowanie (artysta, tytuł, album, długość ±2 s). */
    fun get(track: NowPlaying): LyricsCandidate? {
        val q = buildString {
            append("$API/get?artist_name=").append(Http.enc(track.artist))
            append("&track_name=").append(Http.enc(track.title))
            if (track.album.isNotBlank()) append("&album_name=").append(Http.enc(track.album))
            if (track.durationMs > 0) append("&duration=").append(track.durationMs / 1000)
        }
        val body = Http.get(q) ?: return null
        return parse(json.parseToJsonElement(body).jsonObject)
    }

    fun search(query: String): List<LyricsCandidate> {
        val body = Http.get("$API/search?q=" + Http.enc(query)) ?: return emptyList()
        return (json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }

    fun search(artist: String, title: String): List<LyricsCandidate> {
        val body = Http.get("$API/search?track_name=" + Http.enc(title) + "&artist_name=" + Http.enc(artist)) ?: return emptyList()
        return (json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }
    }

    /**
     * Najlepszy kandydat dla utworu: zgodny tytuł i wykonawca, długość jak najbliższa,
     * wersje z czasami przed tekstem bez czasów.
     */
    fun best(track: NowPlaying, candidates: List<LyricsCandidate>): LyricsCandidate? {
        val t = NowPlaying.normalize(track.title)
        val a = NowPlaying.normalize(track.artist)
        return candidates
            .filter { c ->
                val ct = NowPlaying.normalize(c.title)
                val ca = NowPlaying.normalize(c.artist)
                (ct == t || ct.startsWith(t) || t.startsWith(ct)) && (a.isEmpty() || ca.contains(a) || a.contains(ca))
            }
            .filter { track.durationMs <= 0 || it.durationMs <= 0 || abs(it.durationMs - track.durationMs) < 8_000 }
            .minByOrNull { c ->
                (if (c.hasSynced) 0 else 100_000) + (if (track.durationMs > 0) abs(c.durationMs - track.durationMs) / 100 else 0)
            }
    }
}
