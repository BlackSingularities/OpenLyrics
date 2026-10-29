package com.blacksingularities.openlyrics.tags

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Zapis tekstu wewnątrz pliku: MP3 – ramka USLT w tagu ID3v2 (stare ramki z tekstem są zastępowane,
 * pozostałe tagi i okładka zostają bez zmian), FLAC – komentarz Vorbis LYRICS. Nowy plik powstaje
 * obok jako tymczasowy i dopiero po udanym zapisie zastępuje oryginał.
 */
object TagWriter {
    fun supports(file: File): Boolean = file.extension.lowercase() in setOf("mp3", "flac")

    /** [lrc] – tekst w formacie LRC (albo zwykły tekst). */
    fun writeLyrics(file: File, lrc: String): Boolean = runCatching {
        when (file.extension.lowercase()) {
            "mp3" -> writeMp3(file, lrc)
            "flac" -> writeFlac(file, lrc)
            else -> false
        }
    }.getOrDefault(false)

    // ---------------------------------------------------------------- MP3

    private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun readSyncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)
    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
    private fun be32Bytes(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private fun writeMp3(file: File, lrc: String): Boolean {
        val data = file.readBytes()
        var major = 4
        var audioStart = 0
        val frames = ByteArrayOutputStream()
        if (data.size > 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()) {
            major = data[3].toInt()
            val flags = data[5].toInt()
            val size = readSyncsafe(data, 6)
            audioStart = 10 + size + (if (flags and 0x10 != 0) 10 else 0)
            if (major !in 3..4 || audioStart > data.size) return false
            var body = data.copyOfRange(10, 10 + size)
            if (flags and 0x80 != 0 && major == 3) body = deunsync(body)
            var p = 0
            if (flags and 0x40 != 0) p = if (major == 4) readSyncsafe(body, 0) else be32(body, 0) + 4
            while (p + 10 <= body.size) {
                val id = String(body, p, 4, Charsets.ISO_8859_1)
                if (id[0] == '\u0000') break
                val fsize = if (major == 4) readSyncsafe(body, p + 4) else be32(body, p + 4)
                if (fsize <= 0 || p + 10 + fsize > body.size) break
                // Stare teksty (USLT, SYLT, TXXX:LYRICS…) wypadają – zostaje jeden, nowy.
                if (id != "USLT" && id != "SYLT" && !(id == "TXXX" && isLyricsTxxx(body, p + 10, fsize))) {
                    frames.write(body, p, 10 + fsize)
                }
                p += 10 + fsize
            }
        }
        frames.write(usltFrame(major, lrc))
        val padding = 1024
        val tagBody = frames.toByteArray() + ByteArray(padding)
        val out = ByteArrayOutputStream(tagBody.size + data.size)
        out.write("ID3".toByteArray())
        out.write(byteArrayOf(major.toByte(), 0, 0))
        out.write(syncsafe(tagBody.size))
        out.write(tagBody)
        out.write(data, audioStart, data.size - audioStart)
        return replace(file, out.toByteArray())
    }

    private fun isLyricsTxxx(b: ByteArray, o: Int, n: Int): Boolean {
        val enc = b[o].toInt()
        val text = String(b, o + 1, (n - 1).coerceAtMost(40), if (enc == 1 || enc == 2) Charsets.UTF_16 else Charsets.UTF_8).uppercase()
        return text.startsWith("LYRICS") || text.startsWith("SYNCEDLYRICS") || text.startsWith("UNSYNCEDLYRICS")
    }

    /** USLT: v2.4 – UTF-8, v2.3 – UTF-16 z BOM (v2.3 nie zna UTF-8). */
    private fun usltFrame(major: Int, text: String): ByteArray {
        val body = ByteArrayOutputStream()
        if (major == 4) {
            body.write(3)
            body.write("XXX".toByteArray())
            body.write(0)
            body.write(text.toByteArray(Charsets.UTF_8))
        } else {
            body.write(1)
            body.write("XXX".toByteArray())
            body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
            body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            body.write(text.toByteArray(Charsets.UTF_16LE))
        }
        val b = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("USLT".toByteArray())
        out.write(if (major == 4) syncsafe(b.size) else be32Bytes(b.size))
        out.write(byteArrayOf(0, 0))
        out.write(b)
        return out.toByteArray()
    }

    private fun deunsync(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1] == 0.toByte()) i++
            i++
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- FLAC

    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun writeFlac(file: File, lrc: String): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.ISO_8859_1) != "fLaC") return false
        }
        val data = file.readBytes()
        data class Block(val type: Int, val body: ByteArray)
        val blocks = ArrayList<Block>()
        var p = 4
        while (p + 4 <= data.size) {
            val last = data[p].toInt() and 0x80 != 0
            val type = data[p].toInt() and 0x7F
            val len = ((data[p + 1].toInt() and 0xFF) shl 16) or ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            if (p + 4 + len > data.size) return false
            blocks += Block(type, data.copyOfRange(p + 4, p + 4 + len))
            p += 4 + len
            if (last) break
        }
        val audioStart = p
        // Komentarze: stare teksty usunięte, reszta bez zmian; nowy wpis LYRICS na końcu.
        val old = blocks.firstOrNull { it.type == 4 }?.body
        var vendor = "OpenLyrics".toByteArray()
        val comments = ArrayList<ByteArray>()
        if (old != null) {
            var q = 0
            val vl = le32(old, q); q += 4
            vendor = old.copyOfRange(q, q + vl); q += vl
            val count = le32(old, q); q += 4
            repeat(count) {
                val l = le32(old, q); q += 4
                val c = old.copyOfRange(q, q + l); q += l
                val key = String(c, Charsets.UTF_8).substringBefore('=').uppercase()
                if (key !in setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYNCEDLYRICS", "LYRICS_SYNCED")) comments += c
            }
        }
        comments += ("LYRICS=" + lrc).toByteArray(Charsets.UTF_8)
        val vc = ByteArrayOutputStream().apply {
            write(le32(vendor.size)); write(vendor)
            write(le32(comments.size))
            comments.forEach { write(le32(it.size)); write(it) }
        }.toByteArray()
        val kept = blocks.filter { it.type != 4 && it.type != 1 }.toMutableList()
        // STREAMINFO musi być pierwszy; komentarze zaraz za nim, na końcu dopełnienie na przyszłe zmiany.
        val streamInfo = kept.indexOfFirst { it.type == 0 }
        kept.add(if (streamInfo >= 0) streamInfo + 1 else 0, Block(4, vc))
        kept += Block(1, ByteArray(4096))
        val out = ByteArrayOutputStream(data.size + vc.size + 4096)
        out.write("fLaC".toByteArray())
        kept.forEachIndexed { i, b ->
            val head = (if (i == kept.lastIndex) 0x80 else 0) or b.type
            out.write(head)
            out.write(byteArrayOf((b.body.size shr 16).toByte(), (b.body.size shr 8).toByte(), b.body.size.toByte()))
            out.write(b.body)
        }
        out.write(data, audioStart, data.size - audioStart)
        return replace(file, out.toByteArray())
    }

    private fun replace(file: File, bytes: ByteArray): Boolean {
        val modified = file.lastModified()
        val tmp = File(file.parentFile, "." + file.name + ".openlyrics.tmp")
        tmp.writeBytes(bytes)
        val backup = File(file.parentFile, "." + file.name + ".openlyrics.bak")
        backup.delete()
        if (!file.renameTo(backup)) {
            tmp.delete()
            return false
        }
        if (!tmp.renameTo(file)) {
            backup.renameTo(file)
            tmp.delete()
            return false
        }
        backup.delete()
        // Zachowujemy datę – biblioteki nie uznają utworu za nowy.
        file.setLastModified(modified)
        return true
    }
}
