package com.blacksingularities.openlyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.app.LyricsController
import com.blacksingularities.openlyrics.data.OverlayStyle
import com.blacksingularities.openlyrics.data.ScrollMode
import com.blacksingularities.openlyrics.data.TextAlign
import com.blacksingularities.openlyrics.i18n.AppLanguage
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.ui.components.ActionRow
import com.blacksingularities.openlyrics.ui.components.BlockShape
import com.blacksingularities.openlyrics.ui.components.ChoiceRow
import com.blacksingularities.openlyrics.ui.components.ColorDot
import com.blacksingularities.openlyrics.ui.components.Group
import com.blacksingularities.openlyrics.ui.components.RowIcon
import com.blacksingularities.openlyrics.ui.components.SectionHeader
import com.blacksingularities.openlyrics.ui.components.SliderRow
import com.blacksingularities.openlyrics.ui.components.ToggleRow
import com.blacksingularities.openlyrics.ui.theme.Palette
import com.blacksingularities.openlyrics.update.UpdateState
import kotlin.math.roundToInt

private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

@Composable
fun ColumnScope.SettingsContent(c: LyricsController) {
    val s = c.settings

    AppearanceSettings(c)

    SectionHeader(Icons.Rounded.VisibilityOff, tr("Kiedy pokazywać"))
    Group {
        ToggleRow(Icons.Rounded.Subtitles, tr("Nakładka włączona"), tr("Tekst nad innymi oknami"), s.overlayEnabled) { v -> c.updateSettings { it.copy(overlayEnabled = v) } }
        ToggleRow(Icons.Rounded.PauseCircle, tr("Ukrywaj po zatrzymaniu"), tr("Nakładka znika, gdy muzyka jest wstrzymana"), s.hideWhenPaused) { v -> c.updateSettings { it.copy(hideWhenPaused = v) } }
        ToggleRow(Icons.Rounded.VisibilityOff, tr("Ukrywaj bez tekstu"), tr("Nic nie pokazuj, gdy utwór nie ma tekstu"), s.hideWhenNoLyrics) { v -> c.updateSettings { it.copy(hideWhenNoLyrics = v) } }
    }

    SectionHeader(Icons.Rounded.Lyrics, tr("Teksty"))
    Group {
        ToggleRow(Icons.Rounded.Cloud, tr("Szukaj w sieci"), tr("Gdy plik nie ma tekstu – LRCLIB, otwarta baza tekstów z czasami"), s.searchOnline) { v -> c.updateSettings { it.copy(searchOnline = v) } }
        ToggleRow(Icons.Rounded.Timer, tr("Wolę tekst z czasami"), tr("Gdy w pliku jest tekst bez czasów, poszukaj wersji z czasami"), s.preferSynced, enabled = s.searchOnline) { v -> c.updateSettings { it.copy(preferSynced = v) } }
        ToggleRow(Icons.Rounded.Cloud, tr("Szukaj też w innych bazach"), tr("Gdy LRCLIB nie ma tekstu z czasami – NetEase Cloud Music, a na końcu lyrics.ovh (tekst bez czasów)"), s.extraSources, enabled = s.searchOnline) { v -> c.updateSettings { it.copy(extraSources = v) } }
        ToggleRow(Icons.Rounded.Save, tr("Zapisuj pliki .lrc"), tr("Tekst znaleziony w sieci zapisz obok utworu – następnym razem także inne odtwarzacze go zobaczą"), s.saveSidecar) { v -> c.updateSettings { it.copy(saveSidecar = v) } }
        ToggleRow(Icons.Rounded.Save, tr("Zapisuj tekst w plikach muzycznych"), tr("Tekst z czasami znaleziony w sieci trafia do środka pliku MP3 (ID3) albo FLAC (komentarz LYRICS) – pozostałe tagi i okładka zostają bez zmian"), s.saveEmbedded) { v -> c.updateSettings { it.copy(saveEmbedded = v) } }
        SliderRow(Icons.Rounded.Timer, tr("Przesunięcie wszystkich tekstów"), s.globalOffsetMs / 1000f, -3f..3f, { LyricsController.formatOffset((it * 1000).toLong()) + " s" }, steps = 23) { v ->
            c.updateSettings { it.copy(globalOffsetMs = (v * 4).roundToInt() * 250L) }
        }
        ActionRow(Icons.Rounded.DeleteSweep, tr("Wyczyść zapisane teksty"), tr("{0} utworów w pamięci podręcznej", c.cacheCount), danger = true) { c.clearCache() }
    }

    c.platform.settingsExtras?.invoke()

    SectionHeader(Icons.Rounded.Settings, tr("Ogólne"))
    Group {
        ChoiceRow(Icons.Rounded.Language, tr("Język aplikacji"), AppLanguage.entries, s.language, {
            when (it) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" }
        }) { v -> c.updateSettings { it.copy(language = v) } }
        ToggleRow(Icons.Rounded.SystemUpdate, tr("Aktualizacje"), tr("Sprawdzaj przy uruchomieniu"), s.autoUpdate) { v -> c.updateSettings { it.copy(autoUpdate = v) } }
        UpdateRow(c)
    }
    About(c)
}

@Composable
private fun UpdateRow(c: LyricsController) {
    val update = c.update
    val v = c.platform.appVersion
    val (status, action, handler) = when (update) {
        UpdateState.Idle -> Triple(tr("Wersja {0}", v), tr("Sprawdź"), { c.checkUpdate() })
        UpdateState.Checking -> Triple(tr("Sprawdzanie…"), null, {})
        UpdateState.UpToDate -> Triple(tr("Wersja {0} jest najnowsza", v), tr("Sprawdź"), { c.checkUpdate() })
        is UpdateState.Available -> Triple(tr("Dostępna wersja {0}", update.info.version), tr("Aktualizuj"), { c.updateNow() })
        is UpdateState.Downloading -> Triple(tr("Pobieranie {0} · {1}%", update.info.version, (update.progress * 100).roundToInt()), null, {})
        is UpdateState.Ready -> Triple(tr("Wersja {0} gotowa", update.info.version), tr("Zainstaluj"), { c.installUpdate() })
        is UpdateState.Installing -> Triple(tr("Instalowanie {0}…", update.info.version), null, {})
        is UpdateState.Failed -> Triple(update.message, tr("Ponów"), { c.checkUpdate() })
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.SystemUpdate)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("OpenLyrics $v", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(status, fontSize = 12.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (action != null) {
                Text(
                    action,
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Accent)
                        .clickable(onClick = handler)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        if (update is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { update.progress },
                color = Palette.Accent,
                trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        val notes = (update as? UpdateState.Available)?.info?.notes ?: (update as? UpdateState.Ready)?.info?.notes
        if (!notes.isNullOrBlank()) {
            Text(notes, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 8.dp, start = 44.dp))
        }
        Text(
            tr("Aktualizacje pobierane są bezpośrednio z redzikowski.com"),
            fontSize = 11.sp,
            color = Palette.TextFaint,
            modifier = Modifier.padding(top = 6.dp, start = 44.dp),
        )
    }
}

@Composable
private fun About(c: LyricsController) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(Icons.Rounded.Info, null, tint = Palette.TextFaint, modifier = Modifier.padding(bottom = 4.dp))
        Text(tr(c.platform.aboutTitle), fontSize = 13.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(tr(c.platform.aboutLicense), fontSize = 11.sp, color = Palette.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            tr("Pomysł i wykonanie: Adam Rędzikowski · redzikowski.com"),
            fontSize = 12.sp,
            color = Palette.TextDim,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { runCatching { c.platform.openUri("https://redzikowski.com") } }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            "☕  " + tr("Postaw kawę"),
            fontSize = 13.sp,
            color = Palette.Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(BlockShape)
                .background(Color.White)
                .clickable { runCatching { c.platform.openUri("https://buycoffee.to/adamredzikowski") } }
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}
