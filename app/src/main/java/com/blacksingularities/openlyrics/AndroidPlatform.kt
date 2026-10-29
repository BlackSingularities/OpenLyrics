package com.blacksingularities.openlyrics

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NotificationsActive
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.media.AndroidMedia
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.platform.LocatedTrack
import com.blacksingularities.openlyrics.platform.Platform
import com.blacksingularities.openlyrics.platform.SetupItem
import com.blacksingularities.openlyrics.tags.ChannelSource
import com.blacksingularities.openlyrics.update.UpdateManager
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs

class AndroidPlatform(private val context: Context) : Platform {
    override val name = "Android"
    override val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"
    override val dataDir: File = context.filesDir
    override val systemLanguage: String get() = Locale.getDefault().language
    override val media = AndroidMedia(context)

    /** Wywoływane z aktywności – prośba o uprawnienie do plików muzycznych. */
    var requestAudioPermission: (() -> Unit)? = null

    private fun audioPermission() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudioAccess() = context.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    override fun setupItems(): List<SetupItem> = listOf(
        SetupItem(
            Icons.Rounded.NotificationsActive,
            tr("Dostęp do odtwarzaczy"),
            tr("Android udostępnia informację o odtwarzanym utworze tylko aplikacjom z dostępem do powiadomień. OpenLyrics nie czyta ani nie zapisuje Twoich powiadomień."),
            media.hasAccess(),
            tr("Zezwól"),
        ) { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        SetupItem(
            Icons.Rounded.Layers,
            tr("Wyświetlanie nad aplikacjami"),
            tr("Potrzebne, aby tekst był widoczny nad odtwarzaczem i innymi aplikacjami."),
            Settings.canDrawOverlays(context),
            tr("Zezwól"),
        ) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))) },
        SetupItem(
            Icons.Rounded.LibraryMusic,
            tr("Pliki muzyczne"),
            tr("Pozwala odczytać tekst osadzony w plikach MP3 i FLAC na telefonie. Bez tego teksty są wyszukiwane tylko w sieci."),
            hasAudioAccess(),
            tr("Zezwól"),
        ) { requestAudioPermission?.invoke() },
    )

    private fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Plik utworu: adres z metadanych odtwarzacza (content:// albo file://), a w pozostałych
     * przypadkach wyszukiwanie w MediaStore po tytule, wykonawcy i długości.
     */
    override fun locate(track: NowPlaying): LocatedTrack? {
        val hint = track.fileHint
        if (hint != null) {
            val uri = runCatching { Uri.parse(hint) }.getOrNull()
            if (uri?.scheme == "file") uri.path?.let { p -> File(p).takeIf { it.canRead() }?.let { return fromFile(it) } }
            if (uri?.scheme == "content" && hasAudioAccess()) fromUri(uri, uri.lastPathSegment ?: "audio", null)?.let { return it }
        }
        if (!hasAudioAccess()) return null
        return query(track)
    }

    private fun fromFile(f: File): LocatedTrack {
        val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc")
        return LocatedTrack(
            f.name,
            f.absolutePath,
            { ChannelSource.open(f) },
            readSidecar = { runCatching { lrc.takeIf { it.canRead() }?.readText() }.getOrNull() },
        )
    }

    private fun fromUri(uri: Uri, name: String, path: String?): LocatedTrack? {
        // Sprawdzenie, czy plik da się otworzyć.
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.close() }.getOrNull() ?: return null
        val lrc = path?.let { File(it.substringBeforeLast('.') + ".lrc") }
        return LocatedTrack(
            name,
            path,
            open = {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("openFileDescriptor")
                val stream = FileInputStream(pfd.fileDescriptor)
                ChannelSource(stream.channel) {
                    runCatching { stream.close() }
                    runCatching { pfd.close() }
                }
            },
            readSidecar = { runCatching { lrc?.takeIf { it.canRead() }?.readText() }.getOrNull() },
        )
    }

    private fun query(track: NowPlaying): LocatedTrack? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
        )
        val title = NowPlaying.normalize(track.title)
        val artist = NowPlaying.normalize(track.artist)
        data class Hit(val id: Long, val name: String, val path: String?, val score: Long)
        var best: Hit? = null
        // Zapytanie po fragmencie tytułu, ocena dopasowania w kodzie (wielkość liter, znaki, „feat.”).
        val needle = track.title.take(40).replace("%", "").replace("_", "")
        runCatching {
            context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.TITLE} LIKE ?", arrayOf("%$needle%"), null)?.use { cur ->
                while (cur.moveToNext()) {
                    val t = NowPlaying.normalize(cur.getString(1).orEmpty())
                    val a = NowPlaying.normalize(cur.getString(2).orEmpty())
                    if (t != title) continue
                    if (artist.isNotEmpty() && a.isNotEmpty() && !a.contains(artist) && !artist.contains(a)) continue
                    val d = cur.getLong(3)
                    if (track.durationMs > 0 && d > 0 && abs(d - track.durationMs) > 5_000) continue
                    val score = if (track.durationMs > 0 && d > 0) abs(d - track.durationMs) else 10_000
                    if (best == null || score < best!!.score) best = Hit(cur.getLong(0), cur.getString(4).orEmpty(), cur.getString(5), score)
                }
            }
        }
        val hit = best ?: return null
        return fromUri(ContentUris.withAppendedId(collection, hit.id), hit.name, hit.path)
    }

    override val updateAssetSuffix = ".apk"

    override suspend fun installUpdate(file: File) {
        if (!UpdateManager.canInstall(context)) {
            UpdateManager.openInstallPermissionSettings(context)
            error(tr("Zezwól na instalowanie aplikacji z OpenLyrics i spróbuj ponownie"))
        }
        UpdateManager.install(context, file)
    }

    override fun openUri(uri: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    override val aboutTitle = "OpenLyrics – tekst odtwarzanej piosenki nad aplikacjami, dla Androida i Windows"
    override val aboutLicense = "Licencja MIT · teksty z plików i z otwartej bazy LRCLIB"
}
