package com.blacksingularities.openlyrics.data

import com.blacksingularities.openlyrics.i18n.AppLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class TextAlign { LEFT, CENTER, RIGHT }

/** Sposób przesuwania tekstu w nakładce. */
@Serializable
enum class ScrollMode {
    /** Płynnie, z prędkością wynikającą z czasów kolejnych wierszy. */
    SMOOTH,
    /** Skok o wiersz w chwili jego rozpoczęcia (z krótką animacją). */
    STEP,
}

@Serializable
data class OverlayStyle(
    val fontSize: Int = 30,
    val visibleLines: Int = 5,
    val align: TextAlign = TextAlign.CENTER,
    /** Kolor aktywnego wiersza (ARGB). */
    val activeColor: Long = 0xFFFFFFFF,
    /** Kolor pozostałych wierszy (ARGB). */
    val inactiveColor: Long = 0xFFFFFFFF,
    /** Krycie wierszy innych niż aktywny. */
    val inactiveAlpha: Float = 0.45f,
    val bold: Boolean = true,
    /** Obrys/cień liter – czytelność na każdym tle. */
    val outline: Float = 0.8f,
    /** Krycie tła nakładki (0 – całkowicie przezroczyste). */
    val backgroundAlpha: Float = 0f,
    val scroll: ScrollMode = ScrollMode.SMOOTH,
    /** Wypełnianie słów w aktywnym wierszu (karaoke), gdy tekst ma czasy słów – albo równomiernie. */
    val karaoke: Boolean = true,
    /** Powiększenie aktywnego wiersza. */
    val activeScale: Float = 1.12f,
    /** Wygaszanie wierszy przy górnej i dolnej krawędzi. */
    val fadeEdges: Boolean = true,
    /** Wiersz z tytułem i wykonawcą nad tekstem. */
    val showHeader: Boolean = false,
    /** Położenie aktywnego wiersza w pionie (0 – góra, 1 – dół). */
    val anchor: Float = 0.45f,
)

@Serializable
data class OverlayBounds(val x: Int = -1, val y: Int = -1, val width: Int = 900, val height: Int = 320)

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val autoUpdate: Boolean = true,
    val overlayEnabled: Boolean = true,
    /** Zablokowana nakładka przepuszcza kliknięcia do okien pod spodem. */
    val overlayLocked: Boolean = false,
    val style: OverlayStyle = OverlayStyle(),
    val bounds: OverlayBounds = OverlayBounds(),
    val hideWhenPaused: Boolean = false,
    val hideWhenNoLyrics: Boolean = true,
    val searchOnline: Boolean = true,
    /** Gdy w pliku jest tylko tekst bez czasów – poszukaj wersji z czasami w sieci. */
    val preferSynced: Boolean = true,
    /** Zapisuj tekst znaleziony w sieci jako plik .lrc obok utworu. */
    val saveSidecar: Boolean = false,
    /** Przesunięcie wszystkich tekstów (ms, dodatnie – tekst wcześniej). */
    val globalOffsetMs: Long = 0,
    /** Przesunięcia dla pojedynczych utworów (klucz: wykonawca+tytuł). */
    val trackOffsets: Map<String, Long> = emptyMap(),
    /** Foldery z muzyką przeszukiwane w poszukiwaniu odtwarzanego pliku (Windows). */
    val libraryFolders: List<String> = emptyList(),
    val startWithSystem: Boolean = false,
    val startMinimized: Boolean = false,
    /** Ostatnio pominięta wersja w powiadomieniu o aktualizacji. */
    val skippedVersion: String = "",
)

class SettingsStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun load(): AppSettings = runCatching { json.decodeFromString(AppSettings.serializer(), file.readText()) }.getOrElse { AppSettings() }

    fun save(s: AppSettings) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppSettings.serializer(), s))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
