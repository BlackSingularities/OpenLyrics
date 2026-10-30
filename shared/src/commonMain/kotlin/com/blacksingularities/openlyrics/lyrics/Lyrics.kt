package com.blacksingularities.openlyrics.lyrics

import kotlinx.serialization.Serializable

/** Słowo z własnym czasem (rozszerzony LRC: `<mm:ss.xx>słowo`). */
@Serializable
data class LyricWord(val timeMs: Long, val text: String)

/** Jeden wiersz tekstu. [timeMs] = -1 dla tekstu bez czasów. */
@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
)

/** Skąd pochodzi tekst – pokazywane w interfejsie. */
@Serializable
enum class LyricsSource { EMBEDDED, SIDECAR, CACHE, LRCLIB, MANUAL, NETEASE, LYRICS_OVH }

@Serializable
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: LyricsSource,
    /** Plik, z którego odczytano tekst (osadzony lub .lrc obok utworu). */
    val origin: String? = null,
    val instrumental: Boolean = false,
) {
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() } && !instrumental

    /** Indeks wiersza aktywnego w chwili [positionMs] (-1 przed pierwszym). */
    fun indexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** Tekst w formacie LRC – do zapisu obok pliku albo do pamięci podręcznej. */
    fun toLrc(title: String = "", artist: String = ""): String = buildString {
        if (artist.isNotBlank()) append("[ar:").append(artist).append("]\n")
        if (title.isNotBlank()) append("[ti:").append(title).append("]\n")
        for (l in lines) {
            if (synced && l.timeMs >= 0) append('[').append(formatStamp(l.timeMs)).append(']')
            append(l.text).append('\n')
        }
    }
}

fun formatStamp(ms: Long): String {
    val t = ms.coerceAtLeast(0)
    val m = t / 60_000
    val s = (t / 1000) % 60
    val cs = (t % 1000) / 10
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}"
}

/** „3:07” – długość albo pozycja do wyświetlenia. */
fun formatClock(ms: Long): String {
    val t = (ms.coerceAtLeast(0) / 1000)
    return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
}

object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordStamp = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val tag = Regex("""^\[([a-zA-Z#]+):(.*)]\s*$""")

    private fun ms(m: String, s: String, frac: String?): Long {
        val f = when (frac?.length) {
            null, 0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return m.toLong() * 60_000 + s.toLong() * 1000 + f
    }

    /**
     * Czy tekst wygląda na LRC z czasami (co najmniej dwa wiersze ze znacznikiem).
     */
    fun isSynced(text: String): Boolean = text.lineSequence().count { stamp.containsMatchIn(it.trimStart()) && it.trimStart().startsWith("[") } >= 2

    /**
     * Parsuje LRC: wiele znaczników na wiersz (`[00:12.00][01:30.00]refren`), `[offset:+250]`,
     * znaczniki słów `<00:12.40>`. Tekst bez czasów zwraca jako zwykłe wiersze.
     */
    fun parse(text: String, source: LyricsSource, origin: String? = null): Lyrics {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        if (!isSynced(clean)) return plain(clean, source, origin)
        var offset = 0L
        val out = ArrayList<LyricLine>()
        for (raw in clean.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val t = tag.matchEntire(line)
            if (t != null && !stamp.containsMatchIn(line)) {
                if (t.groupValues[1].equals("offset", true)) offset = t.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                continue
            }
            var rest = line
            val times = ArrayList<Long>()
            while (true) {
                val m = stamp.find(rest) ?: break
                if (m.range.first != 0) break
                times += ms(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null })
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isEmpty()) continue
            val words = parseWords(rest)
            val body = wordStamp.replace(rest, "").replace(Regex("\\s+"), " ").trim()
            for (time in times) out += LyricLine(time, body, words)
        }
        // Dodatni offset w LRC oznacza, że tekst ma się pojawić wcześniej.
        val lines = out.map { l ->
            if (offset == 0L) l else l.copy(
                timeMs = (l.timeMs - offset).coerceAtLeast(0),
                words = l.words.map { w -> w.copy(timeMs = (w.timeMs - offset).coerceAtLeast(0)) },
            )
        }.sortedBy { it.timeMs }
        return Lyrics(lines, synced = true, source = source, origin = origin, instrumental = lines.isNotEmpty() && lines.all { it.text.isBlank() })
    }

    private fun parseWords(rest: String): List<LyricWord> {
        val matches = wordStamp.findAll(rest).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexedNotNull { i, m ->
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else rest.length
            val word = rest.substring(m.range.last + 1, end)
            if (word.isBlank()) null else LyricWord(ms(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null }), word)
        }
    }

    fun plain(text: String, source: LyricsSource, origin: String? = null): Lyrics {
        val lines = text.replace("\r\n", "\n").split('\n')
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .filterNot { tag.matches(it.trim()) }
            .map { LyricLine(-1, it) }
        return Lyrics(lines, synced = false, source = source, origin = origin)
    }

    /** Wiersze z czasami z ramki SYLT (ID3) – (czas, tekst). */
    fun fromTimed(entries: List<Pair<Long, String>>, source: LyricsSource, origin: String?): Lyrics {
        val lines = entries.map { (t, s) -> LyricLine(t, s.trim().trim('\n')) }.sortedBy { it.timeMs }
        return Lyrics(lines, synced = true, source = source, origin = origin)
    }
}
