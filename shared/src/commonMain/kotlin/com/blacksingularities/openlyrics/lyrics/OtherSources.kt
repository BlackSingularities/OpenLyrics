package com.blacksingularities.openlyrics.lyrics

import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

private val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.str(k: String) = (get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.obj(k: String) = get(k) as? JsonObject

/**
 * NetEase Cloud Music – ogromny katalog z tekstami z czasami, także dla muzyki spoza
 * LRCLIB. Bez klucza API; używany, gdy LRCLIB nie ma tekstu albo ma go tylko bez czasów.
 */
object NetEase {
    private const val API = "https://music.163.com/api"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OpenLyrics"

    private data class Song(val id: Long, val title: String, val artist: String, val album: String, val durationMs: Long)

    private fun songs(query: String, limit: Int): List<Song> {
        val body = Http.get("$API/search/get?type=1&limit=$limit&s=" + Http.enc(query), ua = UA) ?: return emptyList()
        val list = json.parseToJsonElement(body).jsonObject.obj("result")?.get("songs") as? JsonArray ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Song(
                id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                title = o.str("name").orEmpty(),
                artist = (o["artists"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }.joinToString(", "),
                album = o.obj("album")?.str("name").orEmpty(),
                durationMs = (o["duration"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        }
    }

    private fun lyric(song: Song): LyricsCandidate? {
        val body = Http.get("$API/song/lyric?lv=1&id=${song.id}", ua = UA) ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        val raw = o.obj("lrc")?.str("lyric").orEmpty()
        val instrumental = (o["pureMusic"] as? JsonPrimitive)?.content == "true" || (o["nolyric"] as? JsonPrimitive)?.content == "true"
        val text = clean(raw)
        if (text.isBlank() && !instrumental) return null
        val synced = Lrc.parse(text, LyricsSource.NETEASE).synced
        return LyricsCandidate(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationMs = song.durationMs,
            synced = text.takeIf { synced },
            plain = text.takeIf { !synced },
            instrumental = instrumental && text.isBlank(),
            source = LyricsSource.NETEASE,
        )
    }

    /**
     * Usuwa dopiski autorów na początku tekstu (np. „作曲 : …”, „Lyrics by: …”),
     * które NetEase umieszcza jako zwykłe wiersze z czasem.
     */
    internal fun clean(lrc: String): String {
        val credit = Regex("""^(\[[\d:.]+])+\s*[^\[\]]{0,24}?\s*[:：]\s*\S.*$""")
        val cjk = Regex("""[　-鿿]""")
        val lines = lrc.lines()
        val firstLyric = lines.indexOfFirst { l ->
            val t = l.replace(Regex("""\[[^\]]*]"""), "").trim()
            t.isNotEmpty() && !(credit.matches(l.trim()) && (cjk.containsMatchIn(t.substringBefore(':').substringBefore('：')) || t.length < 80 && Regex("""(?i)^(lyrics|music|composer|producer|arranger|written|words|vocals?|mix|mastering|guitar|bass|drums|piano)\b""").containsMatchIn(t)))
        }
        return (if (firstLyric > 0) lines.drop(firstLyric) else lines).joinToString("\n").trim()
    }

    /** Najlepszy tekst dla utworu: dopasowanie jak w LRCLIB, pobierane tylko dla kilku pierwszych trafień. */
    fun find(track: NowPlaying): LyricsCandidate? {
        val t = NowPlaying.normalize(track.title)
        val a = NowPlaying.normalize(track.artist)
        val matching = songs("${track.artist} ${track.title}", 10).filter { s ->
            val st = NowPlaying.normalize(s.title)
            val sa = NowPlaying.normalize(s.artist)
            (st == t || st.startsWith(t) || t.startsWith(st)) &&
                (a.isEmpty() || sa.contains(a) || a.contains(sa) || a.split(' ').any { it.length > 2 && sa.contains(it) }) &&
                (track.durationMs <= 0 || s.durationMs <= 0 || abs(s.durationMs - track.durationMs) < 8_000)
        }.sortedBy { if (track.durationMs > 0) abs(it.durationMs - track.durationMs) else 0 }
        var plainFallback: LyricsCandidate? = null
        for (s in matching.take(3)) {
            val c = runCatching { lyric(s) }.getOrNull() ?: continue
            if (c.hasSynced || c.instrumental) return c
            if (plainFallback == null) plainFallback = c
        }
        return plainFallback
    }

    /** Wyniki do ręcznego wyboru – z pobranym tekstem. */
    fun search(query: String, limit: Int = 6): List<LyricsCandidate> =
        songs(query, limit).mapNotNull { runCatching { lyric(it) }.getOrNull() }
}

/** lyrics.ovh – tekst bez czasów; ostatnia deska ratunku, gdy nikt inny nic nie ma. */
object LyricsOvh {
    fun find(track: NowPlaying): LyricsCandidate? {
        if (track.artist.isBlank()) return null
        val body = Http.get("https://api.lyrics.ovh/v1/" + Http.enc(track.artist) + "/" + Http.enc(track.title), timeoutMs = 8_000) ?: return null
        val text = json.parseToJsonElement(body).jsonObject.str("lyrics")?.replace("\r\n", "\n")?.trim().orEmpty()
        // Serwis dokleja czasem nagłówek „Paroles de la chanson …” w pierwszym wierszu.
        val clean = text.lines().dropWhile { it.startsWith("Paroles de la chanson") }.joinToString("\n").trim()
        if (clean.isBlank()) return null
        return LyricsCandidate(0, track.title, track.artist, track.album, 0, synced = null, plain = clean, instrumental = false, source = LyricsSource.LYRICS_OVH)
    }
}
