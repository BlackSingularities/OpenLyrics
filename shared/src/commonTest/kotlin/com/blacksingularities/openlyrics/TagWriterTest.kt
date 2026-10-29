package com.blacksingularities.openlyrics

import com.blacksingularities.openlyrics.tags.TagReader
import com.blacksingularities.openlyrics.tags.TagWriter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Zapis i ponowny odczyt tekstu w syntetycznych plikach (wymyślone wiersze). */
class TagWriterTest {
    private val lrc = "[00:01.00]Pierwszy wiersz\n[00:03.50]Drugi wiersz\n"

    private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())

    @Test
    fun writesMp3KeepingOtherFramesAndAudio() {
        val title = byteArrayOf(3) + "Próbna".toByteArray()
        val frame = "TIT2".toByteArray() + syncsafe(title.size) + byteArrayOf(0, 0) + title
        val audio = ByteArray(500) { (it % 251).toByte() }
        val f = File.createTempFile("ol", ".mp3")
        f.writeBytes("ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frame.size) + frame + audio)
        assertTrue(TagWriter.writeLyrics(f, lrc))
        assertTrue(TagWriter.writeLyrics(f, lrc)) // drugi zapis zastępuje, nie dubluje
        val t = assertNotNull(TagReader.read(f))
        assertEquals("Próbna", t.title)
        assertEquals(listOf("Pierwszy wiersz", "Drugi wiersz"), t.lyrics?.lines?.map { it.text })
        assertTrue(f.readBytes().takeLast(500).toByteArray().contentEquals(audio))
        f.delete()
    }

    @Test
    fun writesMp3WithoutTag() {
        val f = File.createTempFile("ol", ".mp3")
        f.writeBytes(ByteArray(300) { 0x55 })
        assertTrue(TagWriter.writeLyrics(f, lrc))
        assertEquals(2, TagReader.read(f)?.lyrics?.lines?.size)
        f.delete()
    }

    @Test
    fun writesFlac() {
        val streamInfo = ByteArray(34).also { it[10] = 0x0A; it[11] = 0xC4.toByte(); it[12] = 0x40 }
        val audio = ByteArray(400) { (it % 7).toByte() }
        val f = File.createTempFile("ol", ".flac")
        f.writeBytes("fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0, 34) + streamInfo + audio)
        assertTrue(TagWriter.writeLyrics(f, lrc))
        assertTrue(TagWriter.writeLyrics(f, lrc))
        val t = assertNotNull(TagReader.read(f))
        assertEquals(2, t.lyrics?.lines?.size)
        assertTrue(f.readBytes().takeLast(400).toByteArray().contentEquals(audio))
        f.delete()
    }
}
