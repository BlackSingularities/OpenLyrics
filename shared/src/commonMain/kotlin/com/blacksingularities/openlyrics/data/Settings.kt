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

/** Jak wyróżniany jest śpiewany fragment aktywnego wiersza. */
@Serializable
enum class HighlightMode {
    /** Same wiersze – aktywny jaśniejszy i większy, bez śledzenia słów. */
    LINES,
    /** Wypełnianie liter kolorem w rytm śpiewu. */
    FILL,
    /** Kreska pod tekstem przesuwająca się w rytm śpiewu. */
    BAR,
    /** Wypełnianie i kreska razem. */
    FILL_BAR,
}

@Serializable
enum class FontChoice { SANS, SERIF, MONO, CURSIVE }

@Serializable
data class OverlayStyle(
    val fontSize: Int = 30,
    /** Rozmiar pozostałych wierszy względem aktywnego. */
    val inactiveSize: Float = 0.82f,
    val font: FontChoice = FontChoice.SANS,
    /** Grubość pisma 300–900. */
    val weight: Int = 800,
    val italic: Boolean = false,
    val uppercase: Boolean = false,
    /** Odstęp między literami (em). */
    val letterSpacing: Float = 0f,
    /** Odstęp między wierszami (ułamek rozmiaru pisma). */
    val lineSpacing: Float = 0.45f,
    val visibleLines: Int = 5,
    val align: TextAlign = TextAlign.CENTER,
    /** Kolor aktywnego wiersza (ARGB). */
    val activeColor: Long = 0xFFFFFFFF,
    /** Kolor pozostałych wierszy (ARGB). */
    val inactiveColor: Long = 0xFFFFFFFF,
    /** Krycie wierszy innych niż aktywny. */
    val inactiveAlpha: Float = 0.45f,
    /** Kolor wypełnienia karaoke (zaśpiewana część). */
    val fillColor: Long = 0xFFFFE066,
    /** Kolor kreski śpiewania. */
    val barColor: Long = 0xFFFFE066,
    /** Grubość kreski śpiewania (dp). */
    val barHeight: Float = 4f,
    val highlight: HighlightMode = HighlightMode.FILL,
    /** Obrys liter (dp, 0 – brak). */
    val outlineWidth: Float = 0f,
    val outlineColor: Long = 0xFF000000,
    /** Cień: rozmycie, przesunięcie, kolor i krycie. */
    val shadowBlur: Float = 10f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 2f,
    val shadowColor: Long = 0xFF000000,
    val shadowAlpha: Float = 0.85f,
    /** Poświata aktywnego wiersza w jego kolorze (0 – brak). */
    val glow: Float = 0f,
    /** Tło nakładki: kolor i krycie (0 – całkowicie przezroczyste). */
    val backgroundColor: Long = 0xFF000000,
    val backgroundAlpha: Float = 0f,
    val scroll: ScrollMode = ScrollMode.SMOOTH,
    /** Powiększenie aktywnego wiersza. */
    val activeScale: Float = 1.08f,
    /** Wygaszanie wierszy przy górnej i dolnej krawędzi. */
    val fadeEdges: Boolean = true,
    /** Wiersz z tytułem i wykonawcą nad tekstem. */
    val showHeader: Boolean = false,
    /** Położenie aktywnego wiersza w pionie (0 – góra, 1 – dół). */
    val anchor: Float = 0.45f,
)

/** Gotowe style – punkt wyjścia do własnych zmian. */
object StylePresets {
    val all: List<Pair<String, OverlayStyle>> = listOf(
        "Klasyczny" to OverlayStyle(),
        "Karaoke" to OverlayStyle(
            highlight = HighlightMode.FILL_BAR, fillColor = 0xFF7CE3FF, barColor = 0xFF7CE3FF, inactiveAlpha = 0.6f,
            outlineWidth = 2.5f, shadowBlur = 6f, activeScale = 1.04f, inactiveSize = 0.9f,
        ),
        "Neon" to OverlayStyle(
            activeColor = 0xFFFF9EC7, fillColor = 0xFFFFFFFF, barColor = 0xFFFF9EC7, glow = 0.9f, shadowBlur = 18f,
            shadowColor = 0xFFFF3B8D, shadowAlpha = 0.9f, shadowOffsetY = 0f, inactiveColor = 0xFFC5A3FF, inactiveAlpha = 0.5f,
        ),
        "Napisy filmowe" to OverlayStyle(
            highlight = HighlightMode.LINES, font = FontChoice.SANS, weight = 600, fontSize = 28, visibleLines = 2,
            inactiveAlpha = 0.0f, activeScale = 1f, outlineWidth = 3f, shadowBlur = 4f, anchor = 0.7f, fadeEdges = false,
            fillColor = 0xFFFFFFFF, activeColor = 0xFFFFFF66,
        ),
        "Minimalny" to OverlayStyle(
            highlight = HighlightMode.LINES, weight = 400, inactiveAlpha = 0.3f, activeScale = 1f, shadowBlur = 4f,
            shadowAlpha = 0.6f, visibleLines = 3, inactiveSize = 1f,
        ),
        "Pasek" to OverlayStyle(
            highlight = HighlightMode.BAR, backgroundAlpha = 0.55f, outlineWidth = 0f, shadowBlur = 0f, shadowAlpha = 0f,
            barColor = 0xFF9CFF8A, barHeight = 5f, visibleLines = 3, activeScale = 1f, fadeEdges = false,
        ),
        "Szeryfowy" to OverlayStyle(
            font = FontChoice.SERIF, weight = 600, italic = true, activeColor = 0xFFFFE9C2, fillColor = 0xFFFFB061,
            inactiveColor = 0xFFFFE9C2, letterSpacing = 0.02f,
        ),
    )
}

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
    /** Zapisuj tekst znaleziony w sieci wewnątrz pliku MP3 / FLAC. */
    val saveEmbedded: Boolean = false,
    /** Własne kolory zapamiętane w selektorze. */
    val customColors: List<Long> = emptyList(),
    /** Przesunięcie wszystkich tekstów (ms, dodatnie – tekst wcześniej). */
    val globalOffsetMs: Long = 0,
    /** Przesunięcia dla pojedynczych utworów (klucz: wykonawca+tytuł). */
    val trackOffsets: Map<String, Long> = emptyMap(),
    /** Foldery z muzyką przeszukiwane w poszukiwaniu odtwarzanego pliku (Windows). */
    val libraryFolders: List<String> = emptyList(),
    /** Połączenia z odtwarzaczami podającymi ścieżkę grającego pliku (Windows). */
    val playerAimp: Boolean = true,
    val playerWinamp: Boolean = true,
    val playerFoobar: Boolean = false,
    val foobarPort: Int = 8880,
    val playerVlc: Boolean = false,
    val vlcPort: Int = 8080,
    val vlcPassword: String = "",
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
