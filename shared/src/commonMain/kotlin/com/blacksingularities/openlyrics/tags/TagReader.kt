package com.blacksingularities.openlyrics.tags

import com.blacksingularities.openlyrics.lyrics.Lrc
import com.blacksingularities.openlyrics.lyrics.Lyrics
import com.blacksingularities.openlyrics.lyrics.LyricsSource
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/** Plik z dostępem swobodnym – zwykły plik (Windows) albo deskryptor z MediaStore (Android). */
interface SeekableSource : Closeable {
    val size: Long
    fun read(position: Long, length: Int): ByteArray
}

class ChannelSource(private val channel: FileChannel, private val onClose: () -> Unit = {}) : SeekableSource {
    override val size: Long = channel.size()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= size || length <= 0) return ByteArray(0)
        val n = minOf(length.toLong(), size - position).toInt()
        val buf = ByteBuffer.allocate(n)
        var p = position
        while (buf.hasRemaining()) {
            val r = channel.read(buf, p)
            if (r <= 0) break
            p += r
        }
        return buf.array().copyOf(buf.position())
    }
    override fun close() {
        runCatching { channel.close() }
        onClose()
    }

    companion object {
        fun open(file: File): ChannelSource {
            val raf = RandomAccessFile(file, "r")
            return ChannelSource(raf.channel) { runCatching { raf.close() } }
        }
    }
}

/** Podstawowe tagi utworu i znaleziony w nim tekst. */
data class TrackTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val lyrics: Lyrics? = null,
)

/**
 * Czytnik tagów bez zewnętrznych bibliotek: ID3v2.2–2.4 (MP3, także w WAV/AIFF), komentarze Vorbis
 * (FLAC, OGG, Opus) i atomy MP4 (M4A, ALAC). Szuka tekstu w kolejności: z czasami, bez czasów.
 */
object TagReader {
    private const val MAX_TAG = 32 * 1024 * 1024

    fun read(file: File, withLyrics: Boolean = true): TrackTags? =
        runCatching { ChannelSource.open(file).use { read(it, file.name, withLyrics) } }.getOrNull()

    fun read(src: SeekableSource, name: String, withLyrics: Boolean = true): TrackTags? {
        val head = src.read(0, 12)
        if (head.size < 4) return null
        val origin = name
        return when {
            head.ascii(0, 3) == "ID3" -> readId3(src, 0, withLyrics, origin)
            head.ascii(0, 4) == "fLaC" -> readFlac(src, withLyrics, origin)
            head.ascii(0, 4) == "OggS" -> readOgg(src, withLyrics, origin)
            head.size >= 8 && head.ascii(4, 4) == "ftyp" -> readMp4(src, withLyrics, origin)
            // FLAC poprzedzony tagiem ID3 – zdarza się w plikach z niektórych programów.
            else -> null
        } ?: readId3Footer(src, withLyrics, origin)
    }

    // ---------------------------------------------------------------- ID3v2

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun readId3(src: SeekableSource, at: Long, withLyrics: Boolean, origin: String): TrackTags? {
        val h = src.read(at, 10)
        if (h.size < 10) return null
        val major = h[3].toInt()
        val flags = h[5].toInt()
        val size = syncsafe(h, 6)
        if (size <= 0 || size > MAX_TAG) return null
        var body = src.read(at + 10, size)
        // Unsynchronizacja całego tagu (v2.3): 0xFF 0x00 → 0xFF.
        if (flags and 0x80 != 0 && major < 4) body = deunsync(body)
        var p = 0
        if (flags and 0x40 != 0 && major >= 3) {
            val ext = if (major == 4) syncsafe(body, 0) else body.int32(0) + 4
            p = ext.coerceIn(0, body.size)
        }
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0L
        var synced: Lyrics? = null
        var plain: String? = null
        var lrcText: String? = null
        val idLen = if (major == 2) 3 else 4
        val hdrLen = if (major == 2) 6 else 10
        while (p + hdrLen <= body.size) {
            val id = body.ascii(p, idLen)
            if (id.isEmpty() || id[0] == '\u0000') break
            val fsize = when (major) {
                2 -> ((body[p + 3].toInt() and 0xFF) shl 16) or ((body[p + 4].toInt() and 0xFF) shl 8) or (body[p + 5].toInt() and 0xFF)
                4 -> syncsafe(body, p + 4)
                else -> body.int32(p + 4)
            }
            if (fsize <= 0 || p + hdrLen + fsize > body.size) break
            var data = body.copyOfRange(p + hdrLen, p + hdrLen + fsize)
            if (major == 4) {
                val ff = body[p + 9].toInt()
                if (ff and 0x02 != 0) data = deunsync(data)
                if (ff and 0x01 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size)
            }
            when (id) {
                "TIT2", "TT2" -> title = textFrame(data)
                "TPE1", "TP1" -> artist = textFrame(data)
                "TALB", "TAL" -> album = textFrame(data)
                "TLEN", "TLE" -> duration = textFrame(data).trim().toLongOrNull() ?: duration
                "USLT", "ULT" -> if (withLyrics) {
                    val t = langTextFrame(data)
                    if (t.isNotBlank()) {
                        if (Lrc.isSynced(t)) lrcText = lrcText ?: t else plain = plain ?: t
                    }
                }
                "SYLT", "SLT" -> if (withLyrics && synced == null) synced = syltFrame(data, origin)
                "TXXX", "TXX" -> if (withLyrics) {
                    val (desc, value) = txxx(data)
                    val d = desc.uppercase()
                    if ((d == "LYRICS" || d == "SYNCEDLYRICS" || d == "UNSYNCEDLYRICS" || d == "LYRICS-SYNC") && value.isNotBlank()) {
                        if (Lrc.isSynced(value)) lrcText = lrcText ?: value else plain = plain ?: value
                    }
                }
            }
            p += hdrLen + fsize
        }
        val lyrics = when {
            lrcText != null -> Lrc.parse(lrcText, LyricsSource.EMBEDDED, origin)
            synced != null && !synced.isEmpty -> synced
            plain != null -> Lrc.plain(plain, LyricsSource.EMBEDDED, origin)
            else -> null
        }
        return TrackTags(title, artist, album, duration, lyrics)
    }

    private fun readId3Footer(src: SeekableSource, withLyrics: Boolean, origin: String): TrackTags? {
        // ID3 wewnątrz WAV/AIFF („id3 ” / „ID3 ”) – szukamy w pierwszych kilku MB.
        val scan = src.read(0, minOf(src.size, 4L * 1024 * 1024).toInt())
        var i = 12
        while (i + 10 < scan.size) {
            if (scan[i] == 'I'.code.toByte() && scan[i + 1] == 'D'.code.toByte() && scan[i + 2] == '3'.code.toByte() &&
                scan[i + 3].toInt() in 2..4 && scan[i + 4].toInt() == 0
            ) {
                return readId3(src, i.toLong(), withLyrics, origin)
            }
            i++
        }
        return null
    }

    private fun deunsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    private fun charset(enc: Int): Charset = when (enc) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun terminatorLen(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    /** Pozycja końca napisu zakończonego zerem (od [from]) – albo koniec tablicy. */
    private fun findTerminator(b: ByteArray, from: Int, enc: Int): Int {
        val step = terminatorLen(enc)
        var i = from
        while (i + step - 1 < b.size) {
            if (step == 1 && b[i] == 0.toByte()) return i
            if (step == 2 && b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i
            i += step
        }
        return b.size
    }

    private fun decode(b: ByteArray, from: Int, to: Int, enc: Int): String {
        if (to <= from) return ""
        val s = String(b, from, to - from, charset(enc))
        return s.trimEnd('\u0000').removePrefix("﻿")
    }

    private fun textFrame(d: ByteArray): String {
        if (d.isEmpty()) return ""
        val enc = d[0].toInt()
        // W v2.4 wiele wartości oddziela zero – bierzemy pierwszą.
        return decode(d, 1, findTerminator(d, 1, enc), enc).trim()
    }

    /** USLT: kodowanie, język (3), opis zakończony zerem, tekst. */
    private fun langTextFrame(d: ByteArray): String {
        if (d.size < 5) return ""
        val enc = d[0].toInt()
        val descEnd = findTerminator(d, 4, enc)
        val start = (descEnd + terminatorLen(enc)).coerceAtMost(d.size)
        return decode(d, start, d.size, enc)
    }

    private fun txxx(d: ByteArray): Pair<String, String> {
        if (d.size < 2) return "" to ""
        val enc = d[0].toInt()
        val descEnd = findTerminator(d, 1, enc)
        val desc = decode(d, 1, descEnd, enc)
        val start = (descEnd + terminatorLen(enc)).coerceAtMost(d.size)
        return desc to decode(d, start, d.size, enc)
    }

    /** SYLT: kodowanie, język, format czasu (2 = ms), typ, opis, potem pary (tekst\0, czas 4 B). */
    private fun syltFrame(d: ByteArray, origin: String): Lyrics? {
        if (d.size < 7) return null
        val enc = d[0].toInt()
        val format = d[4].toInt()
        if (format != 2) return null
        var p = findTerminator(d, 6, enc) + terminatorLen(enc)
        val entries = ArrayList<Pair<Long, String>>()
        while (p < d.size) {
            val end = findTerminator(d, p, enc)
            val text = decode(d, p, end, enc)
            p = end + terminatorLen(enc)
            if (p + 4 > d.size) break
            entries += d.int32(p).toLong() to text
            p += 4
        }
        if (entries.isEmpty()) return null
        // Niektóre programy zapisują każde słowo osobno – łączymy je w wiersze po znakach nowej linii.
        val merged = ArrayList<Pair<Long, String>>()
        val wordLevel = entries.count { it.second.startsWith("\n") || it.second.startsWith("\r") } > entries.size / 4
        if (wordLevel) {
            var cur: Pair<Long, StringBuilder>? = null
            for ((t, s) in entries) {
                if (cur == null || s.startsWith("\n") || s.startsWith("\r")) {
                    cur?.let { merged += it.first to it.second.toString() }
                    cur = t to StringBuilder(s.trimStart('\n', '\r'))
                } else {
                    cur.second.append(s)
                }
            }
            cur?.let { merged += it.first to it.second.toString() }
        } else {
            merged += entries
        }
        return Lrc.fromTimed(merged, LyricsSource.EMBEDDED, origin)
    }

    // ---------------------------------------------------------------- Vorbis (FLAC, OGG)

    private fun readFlac(src: SeekableSource, withLyrics: Boolean, origin: String): TrackTags? {
        var p = 4L
        var tags: TrackTags? = null
        var duration = 0L
        while (p + 4 <= src.size) {
            val h = src.read(p, 4)
            if (h.size < 4) break
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            if (type == 0 && len >= 18) {
                val si = src.read(p + 4, 18)
                val rate = ((si[10].toInt() and 0xFF) shl 12) or ((si[11].toInt() and 0xFF) shl 4) or ((si[12].toInt() and 0xF0) ushr 4)
                val total = ((si[13].toLong() and 0x0F) shl 32) or ((si[14].toLong() and 0xFF) shl 24) or
                    ((si[15].toLong() and 0xFF) shl 16) or ((si[16].toLong() and 0xFF) shl 8) or (si[17].toLong() and 0xFF)
                if (rate > 0) duration = total * 1000 / rate
            } else if (type == 4 && len in 1..MAX_TAG) {
                tags = vorbisComments(src.read(p + 4, len), 0, withLyrics, origin)
            }
            p += 4 + len
            if (last) break
        }
        return (tags ?: TrackTags()).copy(durationMs = duration)
    }

    private fun readOgg(src: SeekableSource, withLyrics: Boolean, origin: String): TrackTags? {
        // Pakiet komentarzy to drugi pakiet strumienia – składamy go ze stron OGG (może być długi).
        val packets = ArrayList<java.io.ByteArrayOutputStream>()
        var cur = java.io.ByteArrayOutputStream()
        var p = 0L
        var guard = 0
        while (p + 27 <= src.size && packets.size < 2 && guard++ < 4096) {
            val h = src.read(p, 27)
            if (h.ascii(0, 4) != "OggS") break
            val segs = h[26].toInt() and 0xFF
            val table = src.read(p + 27, segs)
            var dataPos = p + 27 + segs
            for (i in 0 until segs) {
                val l = table[i].toInt() and 0xFF
                cur.write(src.read(dataPos, l))
                dataPos += l
                if (l < 255) {
                    packets += cur
                    cur = java.io.ByteArrayOutputStream()
                    if (packets.size == 2 || cur.size() > MAX_TAG) break
                }
            }
            p = dataPos
        }
        if (packets.size < 2) return null
        val pkt = packets[1].toByteArray()
        val skip = when {
            pkt.size > 7 && pkt[0].toInt() == 3 && pkt.ascii(1, 6) == "vorbis" -> 7
            pkt.size > 8 && pkt.ascii(0, 8) == "OpusTags" -> 8
            else -> return null
        }
        return vorbisComments(pkt, skip, withLyrics, origin)
    }

    private fun vorbisComments(b: ByteArray, start: Int, withLyrics: Boolean, origin: String): TrackTags {
        var p = start
        fun le32(): Int {
            val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            return v
        }
        if (p + 4 > b.size) return TrackTags()
        val vendor = le32()
        p += vendor.coerceAtLeast(0)
        if (p + 4 > b.size) return TrackTags()
        val count = le32()
        var title = ""
        var artist = ""
        var album = ""
        var lyricsText: String? = null
        var synced: String? = null
        for (i in 0 until count) {
            if (p + 4 > b.size) break
            val len = le32()
            if (len < 0 || p + len > b.size) break
            val c = String(b, p, len, Charsets.UTF_8)
            p += len
            val eq = c.indexOf('=')
            if (eq <= 0) continue
            val key = c.substring(0, eq).uppercase()
            val value = c.substring(eq + 1)
            when (key) {
                "TITLE" -> if (title.isEmpty()) title = value
                "ARTIST" -> if (artist.isEmpty()) artist = value
                "ALBUM" -> if (album.isEmpty()) album = value
                "SYNCEDLYRICS", "LYRICS_SYNCED" -> if (withLyrics) synced = synced ?: value
                "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (withLyrics) {
                    if (Lrc.isSynced(value)) synced = synced ?: value else lyricsText = lyricsText ?: value
                }
            }
        }
        val lyrics = when {
            synced != null && Lrc.isSynced(synced) -> Lrc.parse(synced, LyricsSource.EMBEDDED, origin)
            lyricsText != null && lyricsText.isNotBlank() -> Lrc.plain(lyricsText, LyricsSource.EMBEDDED, origin)
            synced != null -> Lrc.plain(synced, LyricsSource.EMBEDDED, origin)
            else -> null
        }
        return TrackTags(title, artist, album, 0, lyrics)
    }

    // ---------------------------------------------------------------- MP4 (M4A)

    private fun readMp4(src: SeekableSource, withLyrics: Boolean, origin: String): TrackTags? {
        var title = ""
        var artist = ""
        var album = ""
        var lyrics: String? = null
        var duration = 0L

        fun walk(from: Long, to: Long, depth: Int) {
            var p = from
            while (p + 8 <= to && depth < 8) {
                val h = src.read(p, 16)
                if (h.size < 8) return
                var size = h.int32(0).toLong() and 0xFFFFFFFFL
                val type = h.ascii(4, 4)
                var hdr = 8L
                if (size == 1L && h.size >= 16) {
                    size = h.int64(8)
                    hdr = 16
                } else if (size == 0L) {
                    size = to - p
                }
                if (size < hdr || p + size > to) return
                when (type) {
                    "moov", "udta", "ilst", "trak", "mdia" -> walk(p + hdr, p + size, depth + 1)
                    "meta" -> walk(p + hdr + 4, p + size, depth + 1)
                    "mvhd" -> {
                        val m = src.read(p + hdr, 32)
                        if (m.isNotEmpty()) {
                            val v1 = m[0].toInt() == 1
                            val scale = if (v1) m.int32(20).toLong() else m.int32(12).toLong()
                            val dur = if (v1) m.int64(24) else (m.int32(16).toLong() and 0xFFFFFFFFL)
                            if (scale > 0) duration = dur * 1000 / scale
                        }
                    }
                    "©nam", "©ART", "©alb", "©lyr", "aART" -> {
                        if (size <= MAX_TAG && (type != "©lyr" || withLyrics)) {
                            val v = mp4Data(src.read(p + hdr, (size - hdr).toInt()))
                            when (type) {
                                "©nam" -> title = v
                                "©ART" -> artist = v
                                "aART" -> if (artist.isEmpty()) artist = v
                                "©alb" -> album = v
                                "©lyr" -> lyrics = v
                            }
                        }
                    }
                }
                p += size
            }
        }
        walk(0, src.size, 0)
        val l = lyrics?.takeIf { it.isNotBlank() }?.let {
            if (Lrc.isSynced(it)) Lrc.parse(it, LyricsSource.EMBEDDED, origin) else Lrc.plain(it, LyricsSource.EMBEDDED, origin)
        }
        return TrackTags(title, artist, album, duration, l)
    }

    private fun mp4Data(b: ByteArray): String {
        // Atom „data”: rozmiar, „data”, typ (4), locale (4), wartość UTF-8.
        if (b.size < 16 || b.ascii(4, 4) != "data") return ""
        val len = (b.int32(0)).coerceAtMost(b.size)
        return String(b, 16, (len - 16).coerceAtLeast(0), Charsets.UTF_8)
    }

    // ---------------------------------------------------------------- pomocnicze

    private fun ByteArray.ascii(o: Int, n: Int): String {
        if (o + n > size) return ""
        val sb = StringBuilder(n)
        for (i in o until o + n) sb.append((this[i].toInt() and 0xFF).toChar())
        return sb.toString()
    }

    private fun ByteArray.int32(o: Int): Int {
        if (o + 4 > size) return 0
        return ((this[o].toInt() and 0xFF) shl 24) or ((this[o + 1].toInt() and 0xFF) shl 16) or ((this[o + 2].toInt() and 0xFF) shl 8) or (this[o + 3].toInt() and 0xFF)
    }

    private fun ByteArray.int64(o: Int): Long =
        ((int32(o).toLong() and 0xFFFFFFFFL) shl 32) or (int32(o + 4).toLong() and 0xFFFFFFFFL)
}
