package com.blacksingularities.openlyrics.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.blacksingularities.openlyrics.media.MediaSource
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.tags.SeekableSource
import java.io.File

/** Plik odtwarzanego utworu odnaleziony na urządzeniu. */
class LocatedTrack(
    /** Nazwa do wyświetlenia (np. „Utwór.flac”). */
    val name: String,
    /** Pełna ścieżka, jeśli jest znana (Windows, starsze Androidy). */
    val path: String?,
    val open: () -> SeekableSource,
    /** Treść pliku .lrc leżącego obok utworu. */
    val readSidecar: () -> String? = { null },
    /** Zapis pliku .lrc obok utworu; null – niedostępny. */
    val writeSidecar: ((String) -> Boolean)? = null,
    /** Zapis tekstu wewnątrz pliku (MP3, FLAC); null – niedostępny. */
    val writeEmbedded: ((String) -> Boolean)? = null,
)

/** Krok konfiguracji (uprawnienie albo ustawienie systemu) pokazywany na ekranie głównym. */
class SetupItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val done: Boolean,
    val actionLabel: String,
    val action: () -> Unit,
)

interface Platform {
    /** „Windows” albo „Android”. */
    val name: String
    val appVersion: String
    val dataDir: File
    val systemLanguage: String
    val media: MediaSource

    /** Szuka pliku utworu (MediaStore na Androidzie, indeks folderów na Windows). */
    fun locate(track: NowPlaying): LocatedTrack?

    /** Kroki konfiguracji, które użytkownik musi wykonać (odświeżane przy powrocie do aplikacji). */
    fun setupItems(): List<SetupItem> = emptyList()

    // aktualizacje
    /** Końcówka nazwy pliku wydania dla tej platformy. */
    val updateAssetSuffix: String
    val canSelfUpdate: Boolean get() = true
    val updateDir: File get() = File(dataDir, "updates")
    /** Uruchamia instalację pobranego pliku (Android – PackageInstaller, Windows – instalator). */
    suspend fun installUpdate(file: File)

    fun openUri(uri: String)

    val aboutTitle: String
    val aboutLicense: String

    /** Dodatkowe ustawienia zależne od platformy (foldery muzyki, uprawnienia…). */
    val settingsExtras: (@Composable () -> Unit)? get() = null
}
