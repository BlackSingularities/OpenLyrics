package com.blacksingularities.openlyrics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.blacksingularities.openlyrics.app.LyricsState
import com.blacksingularities.openlyrics.app.SearchState
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.lyrics.LyricsSource
import com.blacksingularities.openlyrics.lyrics.formatClock
import com.blacksingularities.openlyrics.ui.components.BlockShape
import com.blacksingularities.openlyrics.ui.components.CardShape
import com.blacksingularities.openlyrics.ui.components.Chip
import com.blacksingularities.openlyrics.ui.components.DarkField
import com.blacksingularities.openlyrics.ui.components.PillButton
import com.blacksingularities.openlyrics.ui.components.RoundButton
import com.blacksingularities.openlyrics.ui.components.SectionHeader
import com.blacksingularities.openlyrics.ui.theme.OpenLyricsTheme
import com.blacksingularities.openlyrics.ui.theme.Palette
import kotlinx.coroutines.delay

/** Główne okno aplikacji (Android – ekran, Windows – okno ustawień obok nakładki). */
@Composable
fun MainScreen(c: LyricsController, header: (@Composable () -> Unit)? = null) {
    OpenLyricsTheme {
        var settingsTab by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Palette.Panel).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                header?.invoke()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("OpenLyrics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(tr("Teraz"), !settingsTab, Icons.Rounded.Lyrics) { settingsTab = false }
                        Chip(tr("Ustawienia"), settingsTab, Icons.Rounded.Settings) { settingsTab = true }
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    Column(Modifier.widthIn(max = 720.dp).align(Alignment.CenterHorizontally)) {
                        if (settingsTab) SettingsContent(c) else NowContent(c)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            val toast = c.toast
            AnimatedVisibility(
                toast != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            ) {
                Text(
                    toast ?: "",
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(BlockShape).background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.NowContent(c: LyricsController) {
    // Kroki konfiguracji (uprawnienia na Androidzie) – odświeżane co chwilę, bo użytkownik wraca z ustawień systemu.
    LaunchedEffect(Unit) {
        while (true) {
            c.refreshSetup()
            delay(1_500)
        }
    }
    val pending = c.setup.filter { !it.done }
    if (pending.isNotEmpty()) {
        SectionHeader(Icons.Rounded.Settings, tr("Konfiguracja"))
        for (item in pending) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(CardShape).background(Palette.PanelHigh).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.description, fontSize = 12.sp, lineHeight = 16.sp, color = Palette.TextDim)
                }
                PillButton(item.actionLabel, onClick = item.action)
            }
        }
    }
    UpdateBanner(c)
    TrackCard(c)
    PreviewCard(c)
    ActionsCard(c)
    SearchCard(c)
}

@Composable
private fun UpdateBanner(c: LyricsController) {
    val u = c.update as? com.blacksingularities.openlyrics.update.UpdateState.Available ?: return
    if (u.info.version == c.settings.skippedVersion) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(CardShape).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Dostępna wersja {0}", u.info.version), color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            tr("Pomiń"),
            color = Palette.Ink.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.clip(BlockShape).clickable { c.skipUpdate() }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            tr("Aktualizuj"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clip(BlockShape).background(Palette.Ink).clickable { c.updateNow() }.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TrackCard(c: LyricsController) {
    val t = c.track
    SectionHeader(Icons.Rounded.MusicNote, tr("Teraz odtwarzane"))
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Palette.PanelHigh).padding(16.dp)) {
        if (t == null) {
            Text(
                if (c.mediaAvailable) tr("Nic nie gra") else tr("Brak dostępu do odtwarzacza"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (c.mediaAvailable) tr("Włącz muzykę w dowolnym odtwarzaczu – tekst pojawi się sam.") else tr("Wykonaj kroki konfiguracji powyżej."),
                fontSize = 13.sp,
                color = Palette.TextDim,
            )
            return@Column
        }
        Text(t.player.ifBlank { tr("Odtwarzacz") }.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, color = Palette.TextFaint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
            fontSize = 14.sp,
            color = Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(t) {
            while (true) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }
        val pos = t.positionAt(now)
        Spacer(Modifier.height(12.dp))
        if (t.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (pos.toFloat() / t.durationMs).coerceIn(0f, 1f) },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.12f),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(BlockShape),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatClock(pos), fontSize = 12.sp, color = Palette.TextDim)
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Rounded.SkipPrevious, tr("Poprzedni"), size = 40.dp) { c.platform.media.previous() }
            Spacer(Modifier.width(8.dp))
            RoundButton(if (t.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr("Odtwarzaj / pauza"), active = true, size = 48.dp) { c.platform.media.playPause() }
            Spacer(Modifier.width(8.dp))
            RoundButton(Icons.Rounded.SkipNext, tr("Następny"), size = 40.dp) { c.platform.media.next() }
            Spacer(Modifier.weight(1f))
            Text(if (t.durationMs > 0) formatClock(t.durationMs) else "", fontSize = 12.sp, color = Palette.TextDim)
        }
        Spacer(Modifier.height(10.dp))
        SourceLine(c)
    }
}

@Composable
private fun SourceLine(c: LyricsController) {
    val (dot, text) = when (val s = c.lyricsState) {
        LyricsState.Idle -> Palette.TextFaint to tr("Oczekiwanie na utwór")
        is LyricsState.Searching -> Color.White to s.step
        LyricsState.NotFound -> Palette.Danger to tr("Nie znaleziono tekstu – wyszukaj ręcznie poniżej")
        is LyricsState.Failed -> Palette.Danger to s.message
        is LyricsState.Ready -> {
            val l = s.lyrics
            val kind = when {
                l.instrumental -> tr("utwór instrumentalny")
                l.synced -> tr("z czasami")
                else -> tr("bez czasów")
            }
            val src = when (l.source) {
                LyricsSource.EMBEDDED -> tr("Osadzony w pliku")
                LyricsSource.SIDECAR -> tr("Plik .lrc obok utworu")
                LyricsSource.CACHE -> tr("Zapisany wcześniej")
                LyricsSource.LRCLIB -> tr("Znaleziony w LRCLIB")
                LyricsSource.MANUAL -> tr("Wybrany ręcznie")
            }
            Palette.Ok to "$src · $kind · " + tr("{0} wierszy", l.lines.size)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(BlockShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val file = c.located
    if (file != null) {
        Text(file.path ?: file.name, fontSize = 11.sp, color = Palette.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
    }
}

@Composable
private fun PreviewCard(c: LyricsController) {
    SectionHeader(Icons.Rounded.Subtitles, tr("Podgląd"))
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(CardShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E1E1E), Color(0xFF0A0A0A), Color(0xFF2A2A2A))),
            ),
    ) {
        LyricsOverlay(
            c.track,
            c.lyricsState,
            c.settings.style.copy(fontSize = (c.settings.style.fontSize * 0.8f).toInt().coerceIn(14, 40)),
            c.offsetFor(c.track),
            hideWhenNoLyrics = false,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActionsCard(c: LyricsController) {
    val s = c.settings
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(CardShape).background(Palette.PanelHigh).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(if (s.overlayEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff, tr("Nakładka"), active = s.overlayEnabled) {
                c.updateSettings { it.copy(overlayEnabled = !it.overlayEnabled) }
            }
            RoundButton(if (s.overlayLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, tr("Zablokuj nakładkę"), active = s.overlayLocked) {
                c.updateSettings { it.copy(overlayLocked = !it.overlayLocked) }
                c.showToast(if (!s.overlayLocked) tr("Nakładka zablokowana – kliknięcia przechodzą do okien pod nią") else tr("Nakładkę można przesuwać i zmieniać jej rozmiar"))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    when {
                        !s.overlayEnabled -> tr("Nakładka wyłączona")
                        s.overlayLocked -> tr("Nakładka zablokowana")
                        else -> tr("Nakładka – przeciągnij, aby ustawić")
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(tr("Odblokowaną nakładkę przesuwasz myszą lub palcem"), fontSize = 12.sp, color = Palette.TextDim)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Palette.Line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(tr("Przesunięcie czasu"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("Dla tego utworu · zapamiętywane"), fontSize = 12.sp, color = Palette.TextDim)
            }
            val enabled = c.track != null
            RoundButton(Icons.Rounded.Remove, tr("Tekst później"), size = 36.dp, enabled = enabled) { c.nudgeOffset(-250) }
            Text(
                LyricsController.formatOffset(c.offsetFor(c.track)) + " s",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(76.dp).clickable(enabled = enabled) { c.resetTrackOffset() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            RoundButton(Icons.Rounded.Add, tr("Tekst wcześniej"), size = 36.dp, enabled = enabled) { c.nudgeOffset(250) }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Palette.Line))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton(tr("Szukaj ponownie"), Icons.Rounded.Refresh, primary = false, enabled = c.track != null) { c.reload() }
            val ready = c.lyricsState is LyricsState.Ready
            if (ready && c.located?.writeSidecar != null) {
                PillButton(tr("Zapisz .lrc"), Icons.Rounded.Save, primary = false) { c.saveSidecarNow() }
            }
            val manual = (c.lyricsState as? LyricsState.Ready)?.lyrics?.source == LyricsSource.MANUAL
            if (manual) PillButton(tr("Zapomnij wybór"), Icons.Rounded.Delete, primary = false) { c.forgetTrack() }
        }
    }
}

@Composable
private fun SearchCard(c: LyricsController) {
    val t = c.track
    var query by remember(t?.key) { mutableStateOf(t?.let { "${it.artist} ${it.title}".trim() } ?: "") }
    SectionHeader(Icons.Rounded.Search, tr("Wyszukaj tekst"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        DarkField(query, { query = it }, tr("Wykonawca i tytuł"), Modifier.weight(1f)) { if (query.isNotBlank()) c.searchManually(query) }
        Spacer(Modifier.width(8.dp))
        PillButton(tr("Szukaj"), Icons.Rounded.Search, enabled = query.isNotBlank()) { c.searchManually(query) }
    }
    when (val s = c.search) {
        SearchState.Idle -> Text(
            tr("Wyniki z otwartej bazy LRCLIB. Wybrany tekst zostanie zapamiętany dla odtwarzanego utworu."),
            fontSize = 12.sp,
            color = Palette.TextFaint,
            modifier = Modifier.padding(6.dp),
        )
        SearchState.Searching -> Text(tr("Szukanie…"), fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(10.dp))
        is SearchState.Failed -> Text(s.message, fontSize = 13.sp, color = Palette.Danger, modifier = Modifier.padding(10.dp))
        is SearchState.Results -> {
            if (s.items.isEmpty()) Text(tr("Brak wyników"), fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                Icon(
                    Icons.Rounded.Close,
                    tr("Zamknij"),
                    tint = Palette.TextDim,
                    modifier = Modifier.size(28.dp).clip(BlockShape).clickable { c.clearSearch() }.padding(4.dp),
                )
            }
            for (item in s.items.take(20)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.PanelHigh)
                        .clickable(enabled = t != null) { c.choose(item) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (item.hasSynced) Icons.Rounded.Lyrics else Icons.AutoMirrored.Rounded.FormatAlignLeft, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 12.sp,
                            color = Palette.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                item.instrumental -> tr("instrumentalny")
                                item.hasSynced -> tr("z czasami")
                                else -> tr("bez czasów")
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.hasSynced) Palette.Ink else Color.White,
                            modifier = Modifier.clip(BlockShape).background(if (item.hasSynced) Color.White else Color.White.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        if (item.durationMs > 0) Text(formatClock(item.durationMs), fontSize = 11.sp, color = Palette.TextFaint)
                    }
                }
            }
        }
    }
    if (t == null) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Album, null, tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Wybór tekstu jest możliwy, gdy coś gra"), fontSize = 12.sp, color = Palette.TextFaint)
        }
    }
}
