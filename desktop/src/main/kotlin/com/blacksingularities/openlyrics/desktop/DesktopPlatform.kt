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
import com.blacksingularities.openlyrics.tags.TagWriter
import com.blacksingularities.openlyrics.ui.components.ActionRow
import com.blacksingularities.openlyrics.ui.components.Group
import com.blacksingularities.openlyrics.ui.components.SectionHeader
import com.blacksingularities.openlyrics.ui.components.ToggleRow
import com.blacksingularities.openlyrics.ui.components.DarkField
import com.blacksingularities.openlyrics.ui.components.PillButton
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.launch
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
        // Najpierw odtwarzacz (jeśli podaje ścieżkę i połączenie jest włączone), potem indeks folderów.
        val f = PlayerLinks.currentFile(track, controller.settings) ?: library.find(track) ?: return null
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
            writeEmbedded = if (TagWriter.supports(f)) { text -> TagWriter.writeLyrics(f, text) } else null,
        )
    }

    override val updateAssetSuffix = "-setup.exe"

    /**
     * Instalator Inno Setup w trybie cichym. Sam prosi o uprawnienia administratora (instalacja
     * w Program Files), instaluje do folderu poprzedniej instalacji, zamyka tę wersję i uruchamia
     * nową jako zwykły użytkownik. Czekamy na niego: gdy się uda, instalator nas zamknie; gdy
     * okno UAC zostanie odrzucone albo instalacja się nie powiedzie – aplikacja zostaje i pokazuje błąd.
     * Log instalacji trafia obok pobranego pliku.
     */
    override suspend fun installUpdate(file: File) {
        val log = File(file.parentFile, "install.log")
        val code = ProcessBuilder(file.absolutePath, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-", "/LOG=${log.absolutePath}")
            .directory(file.parentFile)
            .start()
            .waitFor()
        if (code != 0) error(tr("Aktualizacja nie została zainstalowana – wymaga zgody administratora (kod {0})", code))
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
    private fun PlayerSettings() {
        val c = controller
        val s = c.settings
        SectionHeader(Icons.Rounded.Cable, tr("Połączenie z odtwarzaczami"))
        Text(
            tr("Odtwarzacze, które podają ścieżkę grającego pliku – wtedy tekst z pliku działa od razu, bez skanowania folderów. Dla pozostałych (np. Spotify) zostaje biblioteka powyżej."),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = Palette.TextDim,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Group {
            ToggleRow(Icons.Rounded.MusicNote, "AIMP", tr("Wbudowane – bez wtyczek"), s.playerAimp) { v -> c.updateSettings { it.copy(playerAimp = v) } }
            ToggleRow(Icons.Rounded.MusicNote, "Winamp", tr("Wbudowane – także odtwarzacze zgodne z Winampem"), s.playerWinamp) { v -> c.updateSettings { it.copy(playerWinamp = v) } }
            ToggleRow(Icons.Rounded.MusicNote, "foobar2000", tr("Wymaga wtyczki beefweb (foo_beefweb)"), s.playerFoobar) { v -> c.updateSettings { it.copy(playerFoobar = v) } }
            if (s.playerFoobar) {
                LinkFields(
                    port = s.foobarPort,
                    onPort = { p -> c.updateSettings { it.copy(foobarPort = p) } },
                    password = null,
                    onPassword = {},
                    test = { PlayerLinks.test("foobar", c.settings) },
                )
            }
            ToggleRow(Icons.Rounded.MusicNote, "VLC", tr("Włącz w VLC: Narzędzia → Preferencje → Wszystkie → Interfejsy → Interfejs WWW i ustaw hasło"), s.playerVlc) { v -> c.updateSettings { it.copy(playerVlc = v) } }
            if (s.playerVlc) {
                LinkFields(
                    port = s.vlcPort,
                    onPort = { p -> c.updateSettings { it.copy(vlcPort = p) } },
                    password = s.vlcPassword,
                    onPassword = { p -> c.updateSettings { it.copy(vlcPassword = p) } },
                    test = { PlayerLinks.test("vlc", c.settings) },
                )
            }
        }
    }

    /** Port, opcjonalne hasło i przycisk sprawdzenia połączenia. */
    @Composable
    private fun LinkFields(port: Int, onPort: (Int) -> Unit, password: String?, onPassword: (String) -> Unit, test: () -> String?) {
        var result by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DarkField(port.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.let(onPort) }, tr("Port"), Modifier.width(110.dp))
                if (password != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    DarkField(password, onPassword, tr("Hasło"), Modifier.weight(1f))
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                PillButton(tr("Sprawdź"), primary = false) {
                    result = tr("Sprawdzanie…")
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) { result = test()?.let { tr("Brak połączenia: {0}", it) } ?: tr("Połączono") }
                }
            }
            result?.let { Text(it, fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 6.dp, start = 6.dp)) }
        }
    }

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
        PlayerSettings()
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
