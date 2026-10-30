package com.blacksingularities.openlyrics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.app.LyricsController
import com.blacksingularities.openlyrics.app.LyricsState
import com.blacksingularities.openlyrics.data.FontChoice
import com.blacksingularities.openlyrics.data.HighlightMode
import com.blacksingularities.openlyrics.data.OverlayStyle
import com.blacksingularities.openlyrics.data.ScrollMode
import com.blacksingularities.openlyrics.data.StylePresets
import com.blacksingularities.openlyrics.data.TextAlign
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.lyrics.LyricLine
import com.blacksingularities.openlyrics.lyrics.LyricWord
import com.blacksingularities.openlyrics.lyrics.Lyrics
import com.blacksingularities.openlyrics.lyrics.LyricsSource
import com.blacksingularities.openlyrics.media.NowPlaying
import com.blacksingularities.openlyrics.ui.components.ActionRow
import com.blacksingularities.openlyrics.ui.components.BlockShape
import com.blacksingularities.openlyrics.ui.components.CardShape
import com.blacksingularities.openlyrics.ui.components.Chip
import com.blacksingularities.openlyrics.ui.components.ChoiceRow
import com.blacksingularities.openlyrics.ui.components.Group
import com.blacksingularities.openlyrics.ui.components.RowIcon
import com.blacksingularities.openlyrics.ui.components.SectionHeader
import com.blacksingularities.openlyrics.ui.components.SliderRow
import com.blacksingularities.openlyrics.ui.components.ToggleRow
import com.blacksingularities.openlyrics.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

/** Wymyślony tekst demonstracyjny z czasami słów – podgląd działa także, gdy nic nie gra. */
private val demoLyrics: Lyrics = run {
    val raw = listOf(
        "Tak wygląda tekst w nakładce",
        "każdy wiersz ma własny czas",
        "a śpiewane słowa wypełniają się kolorem",
        "zmieniaj ustawienia i patrz",
        "jak tekst płynie w górę",
        "OpenLyrics · podgląd na żywo",
    )
    var t = 600L
    val lines = raw.map { text ->
        val words = text.split(' ')
        val per = 2_600L / words.size
        val line = LyricLine(t, text, words.mapIndexed { i, w -> LyricWord(t + i * per, if (i < words.lastIndex) "$w " else w) })
        t += 2_800
        line
    }
    Lyrics(lines, synced = true, source = LyricsSource.MANUAL)
}
private const val DEMO_LENGTH = 17_600L

/** Podgląd nakładki: bieżący utwór, a gdy nic nie gra lub nie ma tekstu – tekst demonstracyjny w pętli. */
@Composable
fun StylePreview(c: LyricsController, style: OverlayStyle, height: Int = 220) {
    val real = c.lyricsState as? LyricsState.Ready
    val useDemo = c.track == null || real == null || real.lyrics.lines.isEmpty()
    var demoStart by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(useDemo) {
        while (useDemo) {
            demoStart = System.currentTimeMillis()
            delay(DEMO_LENGTH)
        }
    }
    val demoTrack = NowPlaying("OpenLyrics", "Podgląd", "OpenLyrics", durationMs = DEMO_LENGTH, positionMs = 0, capturedAt = demoStart, playing = true)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF3A3F47), Color(0xFF15171A), Color(0xFF5B5146)))),
    ) {
        LyricsOverlay(
            if (useDemo) demoTrack else c.track,
            if (useDemo) LyricsState.Ready(demoLyrics) else c.lyricsState,
            style.copy(fontSize = (style.fontSize * 0.75f).roundToInt().coerceIn(12, 44)),
            if (useDemo) 0 else c.offsetFor(c.track),
            hideWhenNoLyrics = false,
        )
        if (useDemo) {
            Text(
                tr("Podgląd"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(BlockShape).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun presetName(n: String) = when (n) {
    "Klasyczny" -> tr("Klasyczny")
    "Karaoke" -> tr("Karaoke")
    "Neon" -> tr("Neon")
    "Napisy filmowe" -> tr("Napisy filmowe")
    "Minimalny" -> tr("Minimalny")
    "Pasek" -> tr("Pasek")
    "Szeryfowy" -> tr("Szeryfowy")
    else -> n
}

/** Wszystkie ustawienia wyglądu nakładki, pogrupowane, z podglądem na górze. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.AppearanceSettings(c: LyricsController) {
    val st = c.settings.style
    fun style(t: (OverlayStyle) -> OverlayStyle) = c.updateSettings { it.copy(style = t(it.style)) }
    val custom = c.settings.customColors
    fun keep(color: Long) = c.updateSettings { it.copy(customColors = (listOf(color) + it.customColors).distinct().take(12)) }

    SectionHeader(Icons.Rounded.Subtitles, tr("Wygląd nakładki"))
    StylePreview(c, st)

    SectionHeader(Icons.Rounded.Style, tr("Gotowe style"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        for ((name, preset) in StylePresets.all) {
            // Styl z zachowaniem rozmiaru i układu użytkownika.
            val applied = preset.copy(fontSize = st.fontSize, visibleLines = if (preset.visibleLines != OverlayStyle().visibleLines) preset.visibleLines else st.visibleLines)
            Chip(presetName(name), applied == st) { style { applied } }
        }
    }

    SectionHeader(Icons.Rounded.Lyrics, tr("Śledzenie śpiewu"))
    Group {
        ChoiceRow(Icons.Rounded.Lyrics, tr("Tryb"), HighlightMode.entries, st.highlight, {
            when (it) {
                HighlightMode.LINES -> tr("Same wiersze")
                HighlightMode.FILL -> tr("Wypełnianie")
                HighlightMode.BAR -> tr("Kreska")
                HighlightMode.FILL_BAR -> tr("Wypełnianie i kreska")
            }
        }) { v -> style { it.copy(highlight = v) } }
        if (st.highlight == HighlightMode.FILL || st.highlight == HighlightMode.FILL_BAR) {
            ColorPickerRow(tr("Kolor wypełnienia"), st.fillColor, custom, ::keep) { v -> style { it.copy(fillColor = v) } }
        }
        if (st.highlight == HighlightMode.BAR || st.highlight == HighlightMode.FILL_BAR) {
            ColorPickerRow(tr("Kolor kreski"), st.barColor, custom, ::keep) { v -> style { it.copy(barColor = v) } }
            SliderRow(Icons.Rounded.LineWeight, tr("Grubość kreski"), st.barHeight, 1f..10f, { "${(it * 10).roundToInt() / 10f} dp" }) { v -> style { it.copy(barHeight = v) } }
        }
        Text(
            tr("Dokładnie co do słowa, gdy tekst ma czasy słów (rozszerzony LRC); inaczej wypełnienie biegnie równo przez cały wiersz."),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = Palette.TextFaint,
            modifier = Modifier.padding(start = 56.dp, end = 12.dp, bottom = 10.dp),
        )
    }

    SectionHeader(Icons.Rounded.TextFields, tr("Czcionka"))
    Group {
        // Wybór kroju wbudowanego wyłącza czcionkę systemową.
        ChoiceRow(Icons.Rounded.TextFormat, tr("Krój"), FontChoice.entries, if (st.customFont.isBlank()) st.font else null, {
            when (it) { FontChoice.SANS -> tr("Bezszeryfowy"); FontChoice.SERIF -> tr("Szeryfowy"); FontChoice.MONO -> tr("Stała szerokość"); FontChoice.CURSIVE -> tr("Odręczny") }
        }) { v -> style { it.copy(font = v, customFont = "") } }
        SystemFonts.current?.let { fonts -> SystemFontRow(fonts, st.customFont) { v -> style { it.copy(customFont = v) } } }
        SliderRow(Icons.Rounded.FormatSize, tr("Rozmiar aktywnego wiersza"), st.fontSize.toFloat(), 14f..96f, { "${it.roundToInt()} pt" }) { v -> style { it.copy(fontSize = v.roundToInt()) } }
        SliderRow(Icons.Rounded.FormatSize, tr("Rozmiar pozostałych wierszy"), st.inactiveSize, 0.4f..1f, { pct(it) }) { v -> style { it.copy(inactiveSize = v) } }
        SliderRow(Icons.Rounded.LineWeight, tr("Grubość"), st.weight.toFloat(), 100f..900f, { weightName(it.roundToInt()) }, steps = 7) { v -> style { it.copy(weight = (v / 100).roundToInt() * 100) } }
        SliderRow(Icons.Rounded.SpaceBar, tr("Odstęp liter"), st.letterSpacing, -0.05f..0.3f, { "${(it * 100).roundToInt()}" }) { v -> style { it.copy(letterSpacing = v) } }
        SliderRow(Icons.Rounded.FormatLineSpacing, tr("Odstęp wierszy"), st.lineSpacing, 0f..1.5f, { pct(it) }) { v -> style { it.copy(lineSpacing = v) } }
        ToggleRow(Icons.Rounded.FormatItalic, tr("Kursywa"), null, st.italic) { v -> style { it.copy(italic = v) } }
        ToggleRow(Icons.Rounded.Title, tr("Wielkie litery"), null, st.uppercase) { v -> style { it.copy(uppercase = v) } }
    }

    SectionHeader(Icons.Rounded.Palette, tr("Kolory"))
    Group {
        ColorPickerRow(tr("Aktywny wiersz"), st.activeColor, custom, ::keep) { v -> style { it.copy(activeColor = v) } }
        ColorPickerRow(tr("Pozostałe wiersze"), st.inactiveColor, custom, ::keep) { v -> style { it.copy(inactiveColor = v) } }
        SliderRow(Icons.Rounded.Opacity, tr("Krycie pozostałych wierszy"), st.inactiveAlpha, 0f..1f, { pct(it) }) { v -> style { it.copy(inactiveAlpha = v) } }
        SliderRow(Icons.Rounded.ZoomIn, tr("Powiększenie aktywnego wiersza"), st.activeScale, 1f..1.4f, { pct(it) }) { v -> style { it.copy(activeScale = v) } }
        SliderRow(Icons.Rounded.WbSunny, tr("Poświata aktywnego wiersza"), st.glow, 0f..1f, { if (it < 0.01f) tr("brak") else pct(it) }) { v -> style { it.copy(glow = v) } }
    }

    SectionHeader(Icons.Rounded.Contrast, tr("Obrys i cień"))
    Group {
        SliderRow(Icons.Rounded.BorderColor, tr("Obrys liter"), st.outlineWidth, 0f..6f, { if (it < 0.05f) tr("brak") else "${(it * 10).roundToInt() / 10f} dp" }) { v -> style { it.copy(outlineWidth = v) } }
        if (st.outlineWidth > 0f) ColorPickerRow(tr("Kolor obrysu"), st.outlineColor, custom, ::keep) { v -> style { it.copy(outlineColor = v) } }
        SliderRow(Icons.Rounded.Gradient, tr("Rozmycie cienia"), st.shadowBlur, 0f..30f, { "${it.roundToInt()}" }) { v -> style { it.copy(shadowBlur = v) } }
        SliderRow(Icons.Rounded.Opacity, tr("Krycie cienia"), st.shadowAlpha, 0f..1f, { if (it < 0.01f) tr("brak") else pct(it) }) { v -> style { it.copy(shadowAlpha = v) } }
        SliderRow(Icons.Rounded.Tune, tr("Przesunięcie cienia w poziomie"), st.shadowOffsetX, -10f..10f, { "${it.roundToInt()}" }) { v -> style { it.copy(shadowOffsetX = v) } }
        SliderRow(Icons.Rounded.Tune, tr("Przesunięcie cienia w pionie"), st.shadowOffsetY, -10f..10f, { "${it.roundToInt()}" }) { v -> style { it.copy(shadowOffsetY = v) } }
        ColorPickerRow(tr("Kolor cienia"), st.shadowColor, custom, ::keep) { v -> style { it.copy(shadowColor = v) } }
    }

    SectionHeader(Icons.Rounded.ViewDay, tr("Układ i tło"))
    Group {
        SliderRow(Icons.Rounded.ViewDay, tr("Widoczne wiersze"), st.visibleLines.toFloat(), 1f..15f, { it.roundToInt().toString() }, steps = 13) { v -> style { it.copy(visibleLines = v.roundToInt()) } }
        SliderRow(Icons.Rounded.VerticalAlignCenter, tr("Wysokość aktywnego wiersza"), st.anchor, 0.1f..0.9f, { pct(it) }) { v -> style { it.copy(anchor = v) } }
        ChoiceRow(Icons.AutoMirrored.Rounded.FormatAlignLeft, tr("Wyrównanie"), TextAlign.entries, st.align, {
            when (it) { TextAlign.LEFT -> tr("Do lewej"); TextAlign.CENTER -> tr("Do środka"); TextAlign.RIGHT -> tr("Do prawej") }
        }) { v -> style { it.copy(align = v) } }
        ChoiceRow(Icons.Rounded.Animation, tr("Przewijanie"), ScrollMode.entries, st.scroll, {
            when (it) { ScrollMode.SMOOTH -> tr("Płynne"); ScrollMode.STEP -> tr("Wiersz po wierszu") }
        }) { v -> style { it.copy(scroll = v) } }
        SliderRow(Icons.Rounded.Gradient, tr("Tło nakładki"), st.backgroundAlpha, 0f..0.9f, { if (it < 0.01f) tr("przezroczyste") else pct(it) }) { v -> style { it.copy(backgroundAlpha = v) } }
        if (st.backgroundAlpha > 0f) ColorPickerRow(tr("Kolor tła"), st.backgroundColor, custom, ::keep) { v -> style { it.copy(backgroundColor = v) } }
        ToggleRow(Icons.Rounded.Height, tr("Wygaszanie krawędzi"), tr("Wiersze łagodnie znikają przy górnej i dolnej krawędzi"), st.fadeEdges) { v -> style { it.copy(fadeEdges = v) } }
        ToggleRow(Icons.Rounded.Title, tr("Tytuł nad tekstem"), tr("Wykonawca i tytuł w górnej części nakładki"), st.showHeader) { v -> style { it.copy(showHeader = v) } }
        ActionRow(Icons.Rounded.AutoAwesome, tr("Przywróć domyślny wygląd"), null) { style { OverlayStyle() } }
    }
}

private fun weightName(w: Int) = when {
    w <= 100 -> tr("włosowa")
    w <= 200 -> tr("bardzo cienka")
    w <= 300 -> tr("cienka")
    w <= 400 -> tr("zwykła")
    w <= 500 -> tr("średnia")
    w <= 600 -> tr("półgruba")
    w <= 700 -> tr("gruba")
    w <= 800 -> tr("bardzo gruba")
    else -> tr("czarna")
}

// ------------------------------------------------------------------ selektor kolorów

private fun toHsv(c: Long): FloatArray {
    val r = ((c shr 16) and 0xFF) / 255f
    val g = ((c shr 8) and 0xFF) / 255f
    val b = (c and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0) it + 360f else it }
    return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
}

private fun fromHsv(h: Float, s: Float, v: Float): Long = (Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)).toArgb().toLong() and 0xFFFFFFFFL)

/** Kolor: paleta, zapamiętane kolory i rozwijany edytor odcienia, nasycenia i jasności. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerRow(title: String, value: Long, custom: List<Long>, onRemember: (Long) -> Unit, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.Palette)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).background(Color(value)))
            Spacer(Modifier.width(8.dp))
            Text(
                "#" + (value and 0xFFFFFF).toString(16).uppercase().padStart(6, '0'),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Palette.TextDim,
            )
        }
        FlowRow(Modifier.padding(start = 40.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val swatches = (Palette.LyricColors + listOf(0xFF000000, 0xFF808080) + custom).distinct()
            for (col in swatches) Swatch(col, col == value) { onPick(col) }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(if (open) Color.White else Color.White.copy(alpha = 0.1f)).clickable { open = !open },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, tr("Własny kolor"), tint = if (open) Palette.Ink else Color.White, modifier = Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(open) {
            val hsv = toHsv(value)
            Column(Modifier.padding(start = 40.dp, top = 10.dp)) {
                GradientSlider(tr("Odcień"), hsv[0] / 360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 59.9f, 1f, 1f) })) { f -> onPick(fromHsv(f * 360f, hsv[1].coerceAtLeast(0.05f), hsv[2].coerceAtLeast(0.2f))) }
                GradientSlider(tr("Nasycenie"), hsv[1], Brush.horizontalGradient(listOf(Color.hsv(hsv[0], 0f, hsv[2]), Color.hsv(hsv[0], 1f, hsv[2])))) { f -> onPick(fromHsv(hsv[0], f, hsv[2])) }
                GradientSlider(tr("Jasność"), hsv[2], Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv[0], hsv[1], 1f)))) { f -> onPick(fromHsv(hsv[0], hsv[1], f)) }
                Text(
                    tr("Zapamiętaj kolor"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                    modifier = Modifier.padding(top = 6.dp).clip(BlockShape).background(Color.White).clickable { onRemember(value) }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .background(Color(color))
            .clickable(onClick = onClick),
    )
}

/** Suwak z kolorową ścieżką (odcień, nasycenie, jasność). */
@Composable
private fun GradientSlider(label: String, value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = Palette.TextDim)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> ch.consume(); onChange((ch.position.x / size.width).coerceIn(0f, 1f)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Box(
                Modifier
                    .offset(x = (maxWidth - 20.dp) * value.coerceIn(0f, 1f))
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
            )
        }
    }
}
