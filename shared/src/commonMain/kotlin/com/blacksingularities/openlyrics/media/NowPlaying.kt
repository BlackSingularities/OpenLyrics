package com.blacksingularities.openlyrics.media

import kotlinx.coroutines.flow.StateFlow

/**
 * Utwór odtwarzany w dowolnym odtwarzaczu systemu (sesja multimediów Windows albo Androida).
 * Pozycja jest ekstrapolowana z chwili pomiaru, więc tekst przesuwa się płynnie między odczytami.
 */
data class NowPlaying(
    val player: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** Czas (System.currentTimeMillis) pomiaru [positionMs]. */
    val capturedAt: Long = System.currentTimeMillis(),
    val playing: Boolean = true,
    val rate: Float = 1f,
    /** Ścieżka albo adres pliku, jeśli odtwarzacz go podał. */
    val fileHint: String? = null,
) {
    val key: String get() = normalize(artist) + "\u0001" + normalize(title)

    fun positionAt(now: Long): Long {
        val p = if (playing) positionMs + ((now - capturedAt) * rate).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }

    /**
     * Porządkuje dane z przeglądarek i serwisów wideo: „Wykonawca - Tytuł (Official Video)”
     * z kanałem „WykonawcaVEVO” albo „Wykonawca - Topic”.
     */
    fun tidy(): NowPlaying {
        var a = artist.trim()
        var t = title.trim()
        val channel = a.endsWith("VEVO") || a.endsWith(" - Topic") || a.isEmpty() ||
            player.contains("edge", true) || player.contains("chrome", true) || player.contains("firefox", true) || player.contains("opera", true)
        if (a.endsWith(" - Topic")) a = a.removeSuffix(" - Topic")
        if (a.endsWith("VEVO")) a = a.removeSuffix("VEVO").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").trim()
        val dash = Regex("""\s+[-–—]\s+""").find(t)
        if (channel && dash != null) {
            a = t.substring(0, dash.range.first).trim()
            t = t.substring(dash.range.last + 1).trim()
        }
        t = t.replace(Regex("""\s*[(\[](official|oficjalny|lyric|lyrics|audio|video|teledysk|hd|4k|visualizer)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "").trim()
        return if (a == artist && t == title) this else copy(artist = a, title = t)
    }

    companion object {
        /** Porównywanie tytułów: bez wielkości liter, polskich znaków, nawiasów i „feat.”. */
        fun normalize(s: String): String {
            var t = s.lowercase()
            t = t.replace(Regex("""\s*[(\[](feat|ft|with|prod|remaster|remastered|live|radio edit|explicit|official)[^)\]]*[)\]]"""), "")
            t = t.replace(Regex("""\s+(feat\.?|ft\.?)\s.*$"""), "")
            t = t.replace(Regex("""\s+-\s+(\d{4}\s+)?remaster(ed)?.*$"""), "")
            t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            t = t.replace('ł', 'l')
            return t.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        }
    }
}

/** Źródło informacji o odtwarzaniu – osobne dla każdej platformy. */
interface MediaSource {
    val nowPlaying: StateFlow<NowPlaying?>
    /** Czy źródło działa (np. na Androidzie – czy jest dostęp do powiadomień). */
    val available: StateFlow<Boolean>
    fun playPause() {}
    fun next() {}
    fun previous() {}
    fun seekTo(positionMs: Long) {}
}
