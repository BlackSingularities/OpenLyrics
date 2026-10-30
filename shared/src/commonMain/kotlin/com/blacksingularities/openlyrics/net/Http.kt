package com.blacksingularities.openlyrics.net

import com.blacksingularities.openlyrics.i18n.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    /** Ustawiane przez platformę: „OpenLyrics/1.0.0 (Windows)”. */
    var userAgent = "OpenLyrics"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** GET – tekst odpowiedzi albo null dla 404. */
    fun get(url: String, accept: String = "application/json", timeoutMs: Int = 12_000, ua: String = userAgent): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs + 3_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", ua)
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) error(tr("Serwer odpowiedział kodem {0}", code))
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Pobranie pliku z postępem; [onProgress] 0..1. */
    fun download(url: String, target: File, expectedSize: Long, cancelled: () -> Boolean, onProgress: (Float) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (conn.responseCode !in 200..299) error(tr("Pobieranie nie powiodło się ({0})", conn.responseCode))
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = 0L
                    while (true) {
                        if (cancelled()) error(tr("Pobieranie przerwane"))
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - last > 128 * 1024) {
                            last = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) error(tr("Nie można zapisać pliku aktualizacji"))
            onProgress(1f)
        } finally {
            conn.disconnect()
        }
    }
}
