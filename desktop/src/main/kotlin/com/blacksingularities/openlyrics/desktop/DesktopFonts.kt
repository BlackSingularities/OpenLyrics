package com.blacksingularities.openlyrics.desktop

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import com.blacksingularities.openlyrics.ui.SystemFonts
import java.awt.GraphicsEnvironment
import java.util.concurrent.ConcurrentHashMap

/** Czcionki zainstalowane w Windows (także te zainstalowane tylko dla użytkownika). */
@OptIn(ExperimentalTextApi::class)
object DesktopFonts : SystemFonts {
    /** Logiczne nazwy Javy – nie są prawdziwymi czcionkami. */
    private val logical = setOf("Dialog", "DialogInput", "Monospaced", "SansSerif", "Serif")
    private val families = ConcurrentHashMap<String, FontFamily>()

    @Volatile private var cached: List<String>? = null

    override fun names(): List<String> = cached ?: GraphicsEnvironment.getLocalGraphicsEnvironment()
        .availableFontFamilyNames
        .filter { it !in logical && !it.startsWith("@") }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .also { cached = it }

    /** Wszystkie grubości i kursywa – Skia dobiera najbliższy plik czcionki z rodziny. */
    override fun family(name: String): FontFamily? = families.getOrPut(name) {
        FontFamily(
            (100..900 step 100).flatMap { w ->
                listOf(FontStyle.Normal, FontStyle.Italic).map { st -> SystemFont(name, FontWeight(w), st) }
            },
        )
    }
}
