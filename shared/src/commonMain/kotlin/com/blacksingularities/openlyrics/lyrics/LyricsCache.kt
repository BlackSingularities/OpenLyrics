package com.blacksingularities.openlyrics.lyrics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Teksty znalezione w sieci albo wybrane ręcznie, zapisane lokalnie – kolejne odtworzenie utworu
 * nie wymaga sieci. Zapamiętuje też brak wyniku, żeby nie pytać serwera przy każdym odtworzeniu.
 */
class LyricsCache(private val dir: File) {
    @Serializable
    private data class Entry(val lyrics: Lyrics? = null, val missingAt: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    fun get(key: String): Lyrics? = read(key)?.lyrics

    /** Czy niedawno (7 dni) nic nie znaleziono. */
    fun recentlyMissing(key: String): Boolean {
        val e = read(key) ?: return false
        return e.lyrics == null && System.currentTimeMillis() - e.missingAt < 7L * 24 * 3600 * 1000
    }

    fun put(key: String, lyrics: Lyrics) = write(key, Entry(lyrics.copy(origin = null)))

    fun markMissing(key: String) = write(key, Entry(null, System.currentTimeMillis()))

    fun remove(key: String) {
        file(key).delete()
    }

    fun clear(): Int {
        val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        files.forEach { it.delete() }
        return files.size
    }

    fun count(): Int = dir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    private fun read(key: String): Entry? = runCatching { json.decodeFromString(Entry.serializer(), file(key).readText()) }.getOrNull()

    private fun write(key: String, e: Entry) {
        runCatching {
            dir.mkdirs()
            file(key).writeText(json.encodeToString(Entry.serializer(), e))
        }
    }
}
