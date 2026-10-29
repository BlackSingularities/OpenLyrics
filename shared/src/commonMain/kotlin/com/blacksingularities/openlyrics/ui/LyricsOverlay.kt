package com.blacksingularities.openlyrics.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.app.LyricsState
import com.blacksingularities.openlyrics.data.OverlayStyle
import com.blacksingularities.openlyrics.data.ScrollMode
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.lyrics.LyricLine
import com.blacksingularities.openlyrics.lyrics.Lyrics
import com.blacksingularities.openlyrics.media.NowPlaying
import kotlin.math.abs
import kotlin.math.roundToInt
import com.blacksingularities.openlyrics.data.TextAlign as Align

/**
 * Nakładka z tekstem: przezroczyste tło, aktywny wiersz na wysokości [OverlayStyle.anchor],
 * wiersze przesuwają się od dołu do góry zgodnie z czasami (płynnie albo skokowo).
 * [frame] – obwódka i podpowiedź, gdy nakładkę można przesuwać.
 */
@Composable
fun LyricsOverlay(
    track: NowPlaying?,
    state: LyricsState,
    style: OverlayStyle,
    offsetMs: Long,
    modifier: Modifier = Modifier,
    frame: Boolean = false,
    hideWhenPaused: Boolean = false,
    hideWhenNoLyrics: Boolean = true,
) {
    val paused = track != null && !track.playing
    val lyrics = (state as? LyricsState.Ready)?.lyrics
    val hasContent = lyrics != null && (lyrics.lines.isNotEmpty() || lyrics.instrumental)
    val visible = track != null && !(paused && hideWhenPaused) && (hasContent || !hideWhenNoLyrics || frame)
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "overlayAlpha")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxSize()
            .then(if (frame) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.55f), shape) else Modifier)
            .graphicsLayer { this.alpha = if (frame) maxOf(alpha, 0.6f) else alpha }
            .background(Color.Black.copy(alpha = style.backgroundAlpha), shape)
            .clipToBounds(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (style.showHeader && track != null) {
                Text(
                    listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = lineStyle(style, (style.fontSize * 0.5f).coerceAtLeast(11f), false, Color.White.copy(alpha = 0.75f)),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    textAlign = style.align.toCompose(),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics != null && lyrics.instrumental -> Message("♪  " + tr("Utwór instrumentalny") + "  ♪", style)
                    lyrics != null && lyrics.lines.isNotEmpty() && track != null -> Lines(lyrics, track, style, offsetMs)
                    frame || !hideWhenNoLyrics -> Message(
                        when (state) {
                            is LyricsState.Searching -> state.step
                            LyricsState.NotFound -> tr("Brak tekstu dla tego utworu")
                            is LyricsState.Failed -> state.message
                            else -> if (track == null) tr("OpenLyrics · uruchom odtwarzanie muzyki") else track.title
                        },
                        style,
                    )
                }
            }
        }
        if (frame) {
            Text(
                tr("Przeciągnij, aby przesunąć · róg – rozmiar"),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

private fun Align.toCompose() = when (this) {
    Align.LEFT -> TextAlign.Start
    Align.CENTER -> TextAlign.Center
    Align.RIGHT -> TextAlign.End
}

private fun lineStyle(style: OverlayStyle, size: Float, bold: Boolean, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = (size * 1.22f).sp,
    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
    shadow = if (style.outline > 0f) Shadow(Color.Black.copy(alpha = (0.55f + 0.4f * style.outline).coerceAtMost(1f)), Offset(0f, size * 0.04f), blurRadius = 2f + 10f * style.outline) else null,
)

@Composable
private fun Message(text: String, style: OverlayStyle) {
    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = lineStyle(style, style.fontSize * 0.7f, style.bold, Color(style.inactiveColor).copy(alpha = 0.85f)),
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

/** Czas zakończenia wiersza – początek następnego (z limitem dla długich przerw). */
private fun lineEnd(lines: List<LyricLine>, i: Int, durationMs: Long): Long {
    val start = lines[i].timeMs
    val next = lines.getOrNull(i + 1)?.timeMs ?: if (durationMs > start) durationMs else start + 5_000
    return minOf(next, start + 10_000).coerceAtLeast(start + 1)
}

/** Postęp wypełniania aktywnego wiersza 0..1 – według czasów słów albo równomiernie. */
private fun fillProgress(line: LyricLine, end: Long, pos: Long): Float {
    if (line.words.isNotEmpty()) {
        val total = line.words.sumOf { it.text.length }.coerceAtLeast(1)
        var done = 0
        for ((k, w) in line.words.withIndex()) {
            val wEnd = line.words.getOrNull(k + 1)?.timeMs ?: end
            if (pos >= wEnd) {
                done += w.text.length
                continue
            }
            if (pos >= w.timeMs) {
                val part = ((pos - w.timeMs).toFloat() / (wEnd - w.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                return (done + w.text.length * part) / total
            }
            return done.toFloat() / total
        }
        return 1f
    }
    // Bez czasów słów: wypełnienie kończy się nieco przed następnym wierszem.
    val span = ((end - line.timeMs) * 0.85f).coerceAtLeast(1f)
    return ((pos - line.timeMs) / span).coerceIn(0f, 1f)
}

@Composable
private fun Lines(lyrics: Lyrics, track: NowPlaying, style: OverlayStyle, offsetMs: Long) {
    val currentTrack by rememberUpdatedState(track)
    val currentOffset by rememberUpdatedState(offsetMs)
    val pos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { pos.longValue = currentTrack.positionAt(System.currentTimeMillis()) + currentOffset }
        }
    }
    val lines = lyrics.lines
    val synced = lyrics.synced
    val active by remember(lyrics) { derivedStateOf { if (synced) lyrics.indexAt(pos.longValue) else -1 } }
    // Tryb skokowy: animowany indeks przewijania.
    val stepAnim = remember(lyrics) { Animatable(0f) }
    LaunchedEffect(lyrics, style.scroll) {
        if (style.scroll == ScrollMode.STEP && synced) {
            androidx.compose.runtime.snapshotFlow { active }.collect { a ->
                stepAnim.animateTo(a.coerceAtLeast(0).toFloat(), tween(420, easing = FastOutSlowInEasing))
            }
        }
    }
    val baseSize = style.fontSize.toFloat()
    val fadeEdges = style.fadeEdges
    Layout(
        content = {
            lines.forEachIndexed { i, line ->
                key(i) {
                    LineText(i, line, lines, track.durationMs, style, baseSize, synced, { active }, { pos.longValue })
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (fadeEdges) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.2f to Color.Black,
                            0.8f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .padding(horizontal = 18.dp),
    ) { measurables, constraints ->
        val c = Constraints(maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(c) }
        val tops = IntArray(placeables.size)
        var y = 0
        for (i in placeables.indices) {
            tops[i] = y
            y += placeables[i].height
        }
        val total = y
        fun center(i: Int): Float = if (i < 0) 0f else tops[i] + placeables[i].height / 2f
        // Pozycja przewijania odczytywana w fazie układu – tekst przesuwa się bez przebudowy drzewa.
        val p = pos.longValue
        val scroll: Float = when {
            placeables.isEmpty() -> 0f
            !synced -> {
                val d = track.durationMs.takeIf { it > 0 } ?: 240_000
                (p.toFloat() / d).coerceIn(0f, 1f) * total
            }
            style.scroll == ScrollMode.STEP -> {
                val f = stepAnim.value
                val a = f.toInt().coerceIn(0, placeables.lastIndex)
                val b = (a + 1).coerceAtMost(placeables.lastIndex)
                center(a) + (center(b) - center(a)) * (f - a)
            }
            else -> {
                val a = active
                if (a < 0) {
                    // Przed pierwszym wierszem: wjazd od dołu.
                    val first = lines[0].timeMs.coerceAtLeast(1)
                    val lead = (1f - (p.toFloat() / first).coerceIn(0f, 1f)).let { it * it }
                    center(0) - lead * constraints.maxHeight * 0.35f
                } else {
                    val end = lineEnd(lines, a, track.durationMs)
                    val raw = ((p - lines[a].timeMs).toFloat() / (end - lines[a].timeMs)).coerceIn(0f, 1f)
                    // Wiersz stoi chwilę na miejscu, potem płynnie ustępuje następnemu.
                    val t = ((raw - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    val eased = t * t * (3 - 2 * t)
                    val b = (a + 1).coerceAtMost(placeables.lastIndex)
                    center(a) + (center(b) - center(a)) * eased
                }
            }
        }
        val anchorY = constraints.maxHeight * style.anchor
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, pl ->
                val top = (anchorY - scroll + tops[i]).roundToInt()
                if (top + pl.height >= -pl.height && top <= constraints.maxHeight + pl.height) {
                    val x = when (style.align) {
                        Align.LEFT -> 0
                        Align.CENTER -> (constraints.maxWidth - pl.width) / 2
                        Align.RIGHT -> constraints.maxWidth - pl.width
                    }
                    pl.place(x, top)
                }
            }
        }
    }
}

@Composable
private fun LineText(
    index: Int,
    line: LyricLine,
    lines: List<LyricLine>,
    durationMs: Long,
    style: OverlayStyle,
    baseSize: Float,
    synced: Boolean,
    active: () -> Int,
    pos: () -> Long,
) {
    val a = active()
    val isActive = synced && index == a
    val distance = if (synced && a >= 0) abs(index - a) else if (synced) index + 1 else 1
    val half = (style.visibleLines / 2f).coerceAtLeast(1f)
    val targetAlpha = when {
        !synced -> 0.9f
        isActive -> 1f
        else -> (style.inactiveAlpha * (1f - ((distance - 1) / (half + 1f)))).coerceIn(0f, 1f)
    }
    val lineAlpha by animateFloatAsState(targetAlpha, tween(380), label = "lineAlpha")
    val scale by animateFloatAsState(if (isActive) style.activeScale else 1f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "lineScale")
    val activeColor = Color(style.activeColor)
    val inactiveColor = Color(style.inactiveColor)
    val text = line.text.ifBlank { "♪" }
    val origin = when (style.align) {
        Align.LEFT -> TransformOrigin(0f, 0.5f)
        Align.CENTER -> TransformOrigin(0.5f, 0.5f)
        Align.RIGHT -> TransformOrigin(1f, 0.5f)
    }
    val karaoke = isActive && style.karaoke && line.text.isNotBlank()
    Box(
        Modifier
            .padding(vertical = (baseSize * 0.22f).dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lineAlpha
                transformOrigin = origin
            },
    ) {
        if (karaoke) {
            // Najpierw cały wiersz przygaszony, na nim wypełnienie kolorem aktywnym do bieżącego miejsca.
            Text(text, style = lineStyle(style, baseSize, style.bold, inactiveColor.copy(alpha = 0.55f)), textAlign = style.align.toCompose())
            val end = lineEnd(lines, index, durationMs)
            Text(
                text,
                style = lineStyle(style, baseSize, style.bold, activeColor).copy(shadow = null),
                textAlign = style.align.toCompose(),
                modifier = Modifier.drawWithContent {
                    val f = fillProgress(line, end, pos())
                    clipRect(right = size.width * f) { this@drawWithContent.drawContent() }
                },
            )
        } else {
            Text(
                text,
                style = lineStyle(style, baseSize, style.bold && (isActive || !synced), if (isActive) activeColor else inactiveColor),
                textAlign = style.align.toCompose(),
            )
        }
    }
}
