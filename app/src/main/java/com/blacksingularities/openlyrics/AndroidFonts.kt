package com.blacksingularities.openlyrics

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.blacksingularities.openlyrics.ui.SystemFonts
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Czcionki zainstalowane w systemie Android. Nie ma dla nich publicznego API, więc czytamy
 * katalogi z plikami krojów – te same, z których korzysta sam system. Pliki jednej rodziny
 * (Regular, Bold, Italic…) łączymy w jedną pozycję na liście, żeby wybór wyglądał jak w Windows.
 */
object AndroidFonts : SystemFonts {
    private val dirs = listOf("/system/fonts", "/product/fonts", "/system_ext/fonts", "/data/fonts")
    private val families = ConcurrentHashMap<String, FontFamily>()

    /** Nazwa rodziny → pliki jej odmian. */
    @Volatile private var files: Map<String, List<File>> = emptyMap()

    @Volatile private var cached: List<String>? = null

    override fun names(): List<String> = cached ?: scan().keys
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .also { cached = it }

    override fun family(name: String): FontFamily? {
        val variants = scan()[name] ?: return null
        return families.getOrPut(name) {
            FontFamily(variants.map { Font(it, weightOf(it.name), styleOf(it.name)) })
        }
    }

    private fun scan(): Map<String, List<File>> {
        files.takeIf { it.isNotEmpty() }?.let { return it }
        val found = HashMap<String, MutableList<File>>()
        for (dir in dirs) {
            val list = runCatching { File(dir).listFiles() }.getOrNull() ?: continue
            for (f in list) {
                if (!f.isFile || f.extension.lowercase() !in setOf("ttf", "otf")) continue
                // Czcionki o stałej szerokości kreski kodu i ikonowe nie nadają się na tekst piosenki.
                val base = f.nameWithoutExtension
                if (base.startsWith("Noto", true) && base.contains("Emoji", true)) continue
                if (base.contains("Icons", true) || base.contains("Symbols", true)) continue
                found.getOrPut(familyOf(base)) { mutableListOf() } += f
            }
        }
        // Rodziny bez żadnej czytelnej odmiany pomijamy, resztę sortujemy – Regular jako pierwsza.
        val ready = found.mapValues { (_, v) -> v.sortedBy { weightOf(it.name).weight + if (styleOf(it.name) == FontStyle.Italic) 1000 else 0 } }
        files = ready
        return ready
    }

    /** `NotoSerif-BoldItalic` → `Noto Serif`; `Roboto-Regular` → `Roboto`. */
    private fun familyOf(base: String): String {
        val stem = base.substringBefore('-').removeSuffix("Variable")
        val spaced = stem.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").trim()
        return spaced.ifBlank { base }
    }

    private fun weightOf(fileName: String): FontWeight {
        val n = fileName.lowercase()
        return when {
            n.contains("extrabold") || n.contains("ultrabold") -> FontWeight.ExtraBold
            n.contains("semibold") || n.contains("demibold") -> FontWeight.SemiBold
            n.contains("extralight") || n.contains("ultralight") -> FontWeight.ExtraLight
            n.contains("black") || n.contains("heavy") -> FontWeight.Black
            n.contains("bold") -> FontWeight.Bold
            n.contains("medium") -> FontWeight.Medium
            n.contains("light") -> FontWeight.Light
            n.contains("thin") || n.contains("hairline") -> FontWeight.Thin
            else -> FontWeight.Normal
        }
    }

    private fun styleOf(fileName: String): FontStyle =
        if (fileName.contains("italic", true) || fileName.contains("oblique", true)) FontStyle.Italic else FontStyle.Normal
}
