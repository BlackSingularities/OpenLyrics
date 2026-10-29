package com.blacksingularities.openlyrics

import com.blacksingularities.openlyrics.lyrics.Lrc
import com.blacksingularities.openlyrics.lyrics.LyricsSource
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.tags.SeekableSource
import com.blacksingularities.openlyrics.tags.TagReader
import com.blacksingularities.openlyrics.update.isNewer
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testy na wymyślonych wierszach – bez prawdziwych tekstów piosenek. */
class LyricsTest {
    private val sample = """
        [ar:Testowy Zespół]
        [ti:Próbna piosenka]
        [offset:+200]
        [00:01.00]Pierwszy wiersz
        [00:05.50][00:20.00]Powtarzany refren
        [00:09.25]<00:09.25>Słowo <00:10.00>po <00:10.50>słowie
        [00:12.00]
    """.trimIndent()

    @Test
    fun parsesSyncedLrc() {
        val l = Lrc.parse(sample, LyricsSource.EMBEDDED)
        assertTrue(l.synced)
        assertEquals(5, l.lines.size)
        // offset +200 ms przesuwa tekst wcześniej
        assertEquals(800, l.lines[0].timeMs)
        assertEquals("Powtarzany refren", l.lines[1].text)
        assertEquals("Powtarzany refren", l.lines.last().text)
        assertEquals("Słowo po słowie", l.lines[2].text)
        assertEquals(3, l.lines[2].words.size)
        assertEquals(9_800, l.lines[2].words[1].timeMs)
    }

    @Test
    fun findsActiveLine() {
        val l = Lrc.parse(sample, LyricsSource.EMBEDDED)
        assertEquals(-1, l.indexAt(100))
        assertEquals(0, l.indexAt(1_000))
        assertEquals(1, l.indexAt(5_300))
        assertEquals(4, l.indexAt(60_000))
    }

    @Test
    fun plainTextStaysUnsynced() {
        val l = Lrc.parse("Linia pierwsza\nLinia druga\n\nLinia trzecia", LyricsSource.LRCLIB)
        assertFalse(l.synced)
        assertEquals(4, l.lines.size)
    }

    @Test
    fun roundTripsLrc() {
        val l = Lrc.parse(sample, LyricsSource.EMBEDDED)
        val again = Lrc.parse(l.toLrc("T", "A"), LyricsSource.CACHE)
        assertEquals(l.lines.map { it.timeMs }, again.lines.map { it.timeMs })
    }

    @Test
    fun normalizesTitles() {
        assertEquals(NowPlaying.normalize("Żółta Łąka (feat. Ktoś)"), NowPlaying.normalize("zolta laka"))
        assertEquals(NowPlaying.normalize("Piosenka - 2011 Remaster"), NowPlaying.normalize("Piosenka"))
    }

    @Test
    fun tidiesVideoTitles() {
        val np = NowPlaying("Microsoft Edge", "Testowy Zespół - Próbna piosenka (Official Video)", "TestowyZespolVEVO").tidy()
        assertEquals("Testowy Zespół", np.artist)
        assertEquals("Próbna piosenka", np.title)
    }

    @Test
    fun comparesVersions() {
        assertTrue(isNewer("1.0.10", "1.0.9"))
        assertFalse(isNewer("1.0.0", "1.0.0-dev"))
        assertTrue(isNewer("1.1", "1.0.9"))
    }

    // ------------------------------------------------------------ tagi

    private class Bytes(private val b: ByteArray) : SeekableSource {
        override val size = b.size.toLong()
        override fun read(position: Long, length: Int): ByteArray {
            if (position >= b.size) return ByteArray(0)
            return b.copyOfRange(position.toInt(), minOf(b.size, position.toInt() + length))
        }
        override fun close() {}
    }

    private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())

    private fun id3Frame(id: String, body: ByteArray) = ByteArrayOutputStream().apply {
        write(id.toByteArray())
        write(syncsafe(body.size))
        write(byteArrayOf(0, 0))
        write(body)
    }.toByteArray()

    @Test
    fun readsId3v24Uslt() {
        val lrc = "[00:01.00]Pierwszy wiersz\n[00:03.00]Drugi wiersz\n"
        val title = byteArrayOf(3) + "Próbna piosenka".toByteArray()
        val uslt = byteArrayOf(3) + "pol".toByteArray() + byteArrayOf(0) + lrc.toByteArray()
        val frames = id3Frame("TIT2", title) + id3Frame("USLT", uslt)
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frames.size) + frames
        val t = TagReader.read(Bytes(tag + ByteArray(64)), "test.mp3")
        assertNotNull(t)
        assertEquals("Próbna piosenka", t.title)
        val l = assertNotNull(t.lyrics)
        assertTrue(l.synced)
        assertEquals(listOf("Pierwszy wiersz", "Drugi wiersz"), l.lines.map { it.text })
    }

    @Test
    fun readsSylt() {
        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(3) + "pol".toByteArray() + byteArrayOf(2, 1, 0))
            for ((t, s) in listOf(1000 to "Raz", 2500 to "Dwa")) {
                write(s.toByteArray() + byteArrayOf(0))
                write(byteArrayOf((t shr 24).toByte(), (t shr 16).toByte(), (t shr 8).toByte(), t.toByte()))
            }
        }.toByteArray()
        val frames = id3Frame("SYLT", body)
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frames.size) + frames
        val l = assertNotNull(TagReader.read(Bytes(tag), "a.mp3")?.lyrics)
        assertEquals(listOf(1000L, 2500L), l.lines.map { it.timeMs })
    }

    @Test
    fun readsFlacVorbisComment() {
        fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
        val comments = listOf("TITLE=Próbna", "ARTIST=Testowy", "LYRICS=[00:02.00]Wiersz A\n[00:04.00]Wiersz B").map { it.toByteArray() }
        val vc = ByteArrayOutputStream().apply {
            write(le(4)); write("test".toByteArray())
            write(le(comments.size))
            comments.forEach { write(le(it.size)); write(it) }
        }.toByteArray()
        val streamInfo = ByteArray(34).also {
            // 44100 Hz, 441000 próbek = 10 s
            it[10] = (44100 shr 12).toByte(); it[11] = (44100 shr 4).toByte(); it[12] = ((44100 and 0xF) shl 4).toByte()
            val total = 441000L
            it[14] = (total shr 24).toByte(); it[15] = (total shr 16).toByte(); it[16] = (total shr 8).toByte(); it[17] = total.toByte()
        }
        val file = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray())
            write(byteArrayOf(0, 0, 0, 34)); write(streamInfo)
            write(byteArrayOf(0x84.toByte(), (vc.size shr 16).toByte(), (vc.size shr 8).toByte(), vc.size.toByte())); write(vc)
        }.toByteArray()
        val t = assertNotNull(TagReader.read(Bytes(file), "a.flac"))
        assertEquals("Próbna", t.title)
        assertEquals("Testowy", t.artist)
        assertEquals(10_000, t.durationMs)
        assertEquals(2, t.lyrics?.lines?.size)
    }
}
