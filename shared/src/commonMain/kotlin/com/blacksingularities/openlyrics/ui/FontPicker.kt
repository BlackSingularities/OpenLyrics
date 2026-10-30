package com.blacksingularities.openlyrics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.i18n.tr
import com.blacksingularities.openlyrics.ui.components.DarkField
import com.blacksingularities.openlyrics.ui.components.RowIcon
import com.blacksingularities.openlyrics.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Czcionki zainstalowane w systemie. Ustawiane przez platformę, która je obsługuje (Windows);
 * bez niej wybór ogranicza się do krojów wbudowanych.
 */
interface SystemFonts {
    /** Nazwy rodzin czcionek, posortowane. Może trwać chwilę – wywoływane poza wątkiem UI. */
    fun names(): List<String>

    /** Rodzina ze wszystkimi grubościami i kursywą; null, gdy czcionki już nie ma w systemie. */
    fun family(name: String): FontFamily?

    companion object {
        var current: SystemFonts? = null
    }
}

/**
 * Wiersz wyboru dowolnej czcionki systemowej: rozwijana lista z wyszukiwaniem, w której
 * każda nazwa jest pokazana własnym krojem.
 */
@Composable
fun SystemFontRow(fonts: SystemFonts, selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(Icons.Rounded.FontDownload)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("Czcionka z systemu"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (selected.isBlank()) tr("Nie wybrano – używany jest krój powyżej") else selected,
                    fontSize = 12.sp,
                    color = Palette.TextDim,
                    fontFamily = selected.takeIf { it.isNotBlank() }?.let { fonts.family(it) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Palette.TextDim)
        }
        AnimatedVisibility(open) { FontList(fonts, selected, onPick) }
    }
}

@Composable
private fun FontList(fonts: SystemFonts, selected: String, onPick: (String) -> Unit) {
    val all by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { fonts.names() } }
    var query by remember { mutableStateOf("") }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.contains(q, ignoreCase = true) }
    }
    val list = rememberLazyListState()
    // Po otwarciu lista startuje przy wybranej czcionce.
    LaunchedEffect(all) {
        val i = all.indexOf(selected)
        if (i > 0) list.scrollToItem((i - 2).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 12.dp, bottom = 12.dp)) {
        DarkField(query, { query = it }, tr("Szukaj czcionki ({0})", all.size), Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), state = list) {
                if (query.isBlank()) {
                    item(key = "") { FontItem(tr("Krój wbudowany"), null, selected.isBlank()) { onPick("") } }
                }
                items(shown, key = { it }) { name ->
                    FontItem(name, fonts.family(name), name == selected) { onPick(name) }
                }
                if (shown.isEmpty() && all.isNotEmpty()) {
                    item(key = "-") {
                        Text(tr("Brak czcionek pasujących do „{0}”", query.trim()), fontSize = 13.sp, color = Palette.TextFaint, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FontItem(name: String, family: FontFamily?, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (active) Color.White else Color.Transparent))
        Text(name, fontSize = 16.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (family != null) {
            Text("Aa Ąą", fontSize = 13.sp, fontFamily = family, color = Palette.TextFaint)
            Spacer(Modifier.width(2.dp))
        }
    }
}
