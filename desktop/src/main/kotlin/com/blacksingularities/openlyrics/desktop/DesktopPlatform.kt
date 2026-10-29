package com.blacksingularities.openlyrics.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.app.LyricsController
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.platform.LocatedTrack
import com.blacksingularities.openlyrics.platform.Platform
import com.blacksingularities.openlyrics.tags.ChannelSource
import com.blacksingularities.openlyrics.ui.components.ActionRow
import com.blacksingularities.openlyrics.ui.components.Group
import com.blacksingularities.openlyrics.ui.components.SectionHeader
import com.blacksingularities.openlyrics.ui.components.ToggleRow
import com.blacksingularities.openlyrics.ui.theme.Palette
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.system.exitProcess

class DesktopPlatform : Platform {
    override val name = "Windows"
    override val appVersion = BuildInfo.VERSION
    override val dataDir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "OpenLyrics").apply { mkdirs() }
    override val systemLanguage: String = Locale.getDefault().language

    /** Mostek do sesji multimediów: w instalacji – zasoby aplikacji, przy uruchomieniu z Gradle – bridge/publish. */
    private val bridge: File? = listOfNotNull(
        System.getProperty("compose.application.resources.dir")?.let { File(it, "OpenLyricsMedia.exe") },
        File("resources/windows/OpenLyricsMedia.exe"),
        File("../bridge/publish/OpenLyricsMedia.exe"),
    ).firstOrNull { it.isFile }

    override val media = WindowsMedia(bridge)
    val library = LibraryIndex(File(dataDir, "library.json"))
    lateinit var controller: LyricsController

    /** Domyślny folder z muzyką użytkownika. */
    val defaultMusic: File = File(System.getProperty("user.home"), "Music")

    fun folders(): List<File> = controller.settings.libraryFolders.map(::File).ifEmpty { listOf(defaultMusic) }

    fun rescan() = library.scan(folders())

    override fun locate(track: NowPlaying): LocatedTrack? {
        val f = (track.fileHint?.let(::File)?.takeIf { it.isFile }) ?: library.find(track) ?: return null
        val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc")
        return LocatedTrack(
            name = f.name,
            path = f.absolutePath,
            open = { ChannelSource.open(f) },
            readSidecar = { lrc.takeIf { it.isFile }?.readText(Charsets.UTF_8) },
            writeSidecar = { text ->
                runCatching {
                    lrc.writeText(text, Charsets.UTF_8)
                    true
                }.getOrDefault(false)
            },
        )
    }

    override val updateAssetSuffix = "-setup.exe"

    /**
     * Instalator Inno Setup w trybie cichym: zamyka bieżącą wersję, instaluje nową
     * i uruchamia ją ponownie (sekcja [Run] z warunkiem WizardSilent).
     */
    override suspend fun installUpdate(file: File) {
        ProcessBuilder(file.absolutePath, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/CLOSEAPPLICATIONS", "/SP-")
            .directory(file.parentFile)
            .start()
        media.close()
        exitProcess(0)
    }

    override fun openUri(uri: String) {
        runCatching { Desktop.getDesktop().browse(URI(uri)) }
            .onFailure { runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri).start() } }
    }

    override val aboutTitle = "OpenLyrics – tekst odtwarzanej piosenki nad oknami, dla Windows i Androida"
    override val aboutLicense = "Licencja MIT · teksty z plików i z otwartej bazy LRCLIB"

    var pickFolder: (() -> File?)? = null

    override val settingsExtras: @Composable () -> Unit = { LibrarySettings() }

    @Composable
    private fun LibrarySettings() {
        val c = controller
        val s = c.settings
        SectionHeader(Icons.Rounded.LibraryMusic, tr("Biblioteka muzyki"))
        Text(
            tr("Windows nie podaje ścieżki odtwarzanego pliku, więc OpenLyrics szuka go w tych folderach – po tagach, a gdy ich brak, po nazwie pliku."),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = Palette.TextDim,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Group {
            for (f in folders()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(f.absolutePath, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    if (s.libraryFolders.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            tr("Usuń folder"),
                            tint = Palette.TextDim,
                            modifier = Modifier.size(28.dp).clip(CircleShape).clickable {
                                c.updateSettings { it.copy(libraryFolders = it.libraryFolders - f.absolutePath) }
                                rescan()
                            }.padding(4.dp),
                        )
                    }
                }
            }
            ActionRow(Icons.Rounded.CreateNewFolder, tr("Dodaj folder"), null) {
                val f = pickFolder?.invoke() ?: return@ActionRow
                c.updateSettings { st ->
                    val base = st.libraryFolders.ifEmpty { listOf(defaultMusic.absolutePath) }
                    st.copy(libraryFolders = (base + f.absolutePath).distinct())
                }
                rescan()
            }
            ActionRow(
                Icons.Rounded.Refresh,
                if (library.scanning) tr("Skanowanie… {0} plików", library.scanned) else tr("Skanuj ponownie"),
                tr("{0} utworów w indeksie", library.size),
            ) { rescan() }
        }
        SectionHeader(Icons.Rounded.Login, tr("Uruchamianie"))
        Group {
            var autostart by androidx.compose.runtime.remember { mutableStateOf(Win32.isAutostart()) }
            ToggleRow(Icons.Rounded.Login, tr("Uruchamiaj z Windows"), tr("OpenLyrics startuje w zasobniku po zalogowaniu"), autostart) { v ->
                if (Win32.setAutostart(v)) autostart = Win32.isAutostart() else c.showToast(tr("Autostart działa tylko w zainstalowanej aplikacji"))
            }
            ToggleRow(Icons.Rounded.Minimize, tr("Uruchamiaj zminimalizowany"), tr("Przy starcie tylko nakładka i ikona w zasobniku"), s.startMinimized) { v ->
                c.updateSettings { it.copy(startMinimized = v) }
            }
        }
    }
}
