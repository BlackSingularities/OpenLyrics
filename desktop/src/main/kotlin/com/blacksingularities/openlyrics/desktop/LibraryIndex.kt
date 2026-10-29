package com.blacksingularities.openlyrics.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.tags.TagReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Indeks plików muzycznych w wybranych folderach. Sesja multimediów Windows nie podaje ścieżki
 * pliku, więc utwór odnajdujemy po tagach (wykonawca + tytuł + długość), a w razie braku tagów –
 * po nazwie pliku „Wykonawca - Tytuł”. Indeks zapisuje się na dysku i odświeża przyrostowo.
 */
class LibraryIndex(private val file: File) {
    @Serializable
    data class Entry(val path: String, val modified: Long, val title: String, val artist: String, val durationMs: Long)

    @Serializable
    private data class Stored(val entries: List<Entry>)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()
    /** Klucz wykonawca+tytuł → pliki. */
    @Volatile private var byKey: Map<String, List<Entry>> = emptyMap()
    @Volatile private var byTitle: Map<String, List<Entry>> = emptyMap()
    private var job: Job? = null

    var scanning by mutableStateOf(false)
        private set
    var scanned by mutableStateOf(0)
        private set
    var size by mutableStateOf(0)
        private set

    init {
        runCatching { json.decodeFromString(Stored.serializer(), file.readText()).entries.forEach { entries[it.path] = it } }
        rebuild()
    }

    private fun rebuild() {
        val all = entries.values.toList()
        byKey = all.groupBy { NowPlaying.normalize(it.artist) + "\u0001" + NowPlaying.normalize(it.title) }
        byTitle = all.groupBy { NowPlaying.normalize(it.title) }
        size = all.size
    }

    fun scan(folders: List<File>) {
        job?.cancel()
        job = scope.launch {
            scanning = true
            scanned = 0
            val seen = HashSet<String>()
            try {
                for (root in folders.filter { it.isDirectory }) {
                    root.walkTopDown().onFail { _, _ -> }.forEach { f ->
                        if (!isActive) return@launch
                        if (!f.isFile || f.extension.lowercase() !in AUDIO) return@forEach
                        val path = f.absolutePath
                        seen += path
                        val mod = f.lastModified()
                        val old = entries[path]
                        if (old == null || old.modified != mod) {
                            val tags = TagReader.read(f, withLyrics = false)
                            val (fa, ft) = fromName(f)
                            entries[path] = Entry(
                                path,
                                mod,
                                tags?.title?.ifBlank { null } ?: ft,
                                tags?.artist?.ifBlank { null } ?: fa,
                                tags?.durationMs ?: 0,
                            )
                        }
                        scanned++
                        if (scanned % 500 == 0) rebuild()
                    }
                }
                // Pliki usunięte albo spoza wybranych folderów wypadają z indeksu.
                val roots = folders.map { it.absolutePath.lowercase().trimEnd('\\') + "\\" }
                entries.keys.filter { it !in seen && roots.any { r -> it.lowercase().startsWith(r) } || roots.none { r -> it.lowercase().startsWith(r) } }
                    .forEach { entries.remove(it) }
                rebuild()
                save()
            } finally {
                scanning = false
            }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Stored.serializer(), Stored(entries.values.toList())))
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** Najlepiej pasujący plik dla odtwarzanego utworu. */
    fun find(track: NowPlaying): File? {
        val title = NowPlaying.normalize(track.title)
        if (title.isEmpty()) return null
        val candidates = byKey[track.key].orEmpty().ifEmpty {
            // Wykonawca bywa zapisany inaczej („A, B” zamiast „A feat. B”) – dopasowanie po tytule i części wykonawcy.
            val artist = NowPlaying.normalize(track.artist)
            byTitle[title].orEmpty().filter { e ->
                val ea = NowPlaying.normalize(e.artist)
                artist.isEmpty() || ea.isEmpty() || ea.contains(artist) || artist.contains(ea) ||
                    ea.split(' ').firstOrNull() == artist.split(' ').firstOrNull()
            }
        }
        return candidates
            .filter { track.durationMs <= 0 || it.durationMs <= 0 || abs(it.durationMs - track.durationMs) < 5_000 }
            .minByOrNull { if (track.durationMs > 0 && it.durationMs > 0) abs(it.durationMs - track.durationMs) else 10_000 }
            ?.let { File(it.path) }
            ?.takeIf { it.isFile }
    }

    companion object {
        val AUDIO = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "mp4", "aac", "alac", "wav", "aif", "aiff", "wma")

        /** „01 - Wykonawca - Tytuł.mp3” → (Wykonawca, Tytuł). */
        fun fromName(f: File): Pair<String, String> {
            val base = f.nameWithoutExtension.replace('_', ' ').replace(Regex("""^\d{1,3}[\s.\-]+"""), "")
            val parts = base.split(Regex("""\s+[-–]\s+"""))
            return if (parts.size >= 2) parts[0].trim() to parts.drop(1).joinToString(" - ").trim() else (f.parentFile?.parentFile?.name ?: "") to base.trim()
        }
    }
}
