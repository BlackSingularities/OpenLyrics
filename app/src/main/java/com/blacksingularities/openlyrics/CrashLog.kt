package com.blacksingularities.openlyrics

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.blacksingularities.openlyrics.i18n.tr
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zapis awarii do pliku i pokazanie jej przy następnym uruchomieniu – z przyciskiem kopiowania,
 * żeby dało się ją przekazać bez podłączania telefonu do komputera.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            record(context, e, "wątek ${thread.name}")
            previous?.uncaughtException(thread, e)
        }
    }

    /** Zapis błędu, także takiego, który przechwyciliśmy i po którym aplikacja działa dalej. */
    fun record(context: Context, e: Throwable, where: String) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val head = buildString {
                appendLine("OpenLyrics ${pkg.versionName} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()) + " · " + where)
                runCatching {
                    val t = OpenLyricsApp.instance.controller.track
                    if (t != null) appendLine("Odtwarzacz: ${t.player} · plik: ${t.fileHint ?: "-"}")
                }
                appendLine()
            }
            file(context).writeText(head + sw.toString().take(12_000))
        }
    }

    fun showIfAny(activity: Activity) {
        val f = file(activity)
        val text = runCatching { f.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        f.delete()
        AlertDialog.Builder(activity)
            .setTitle(tr("OpenLyrics napotkał błąd"))
            .setMessage(tr("Skopiuj opis błędu i prześlij go autorowi – pomoże to szybko go naprawić.") + "\n\n" + text.take(1_500))
            .setPositiveButton(tr("Kopiuj")) { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("OpenLyrics", text))
                Toast.makeText(activity, tr("Skopiowano"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(tr("Zamknij"), null)
            .show()
    }
}
