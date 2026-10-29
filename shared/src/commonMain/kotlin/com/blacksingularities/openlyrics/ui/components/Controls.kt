package com.blacksingularities.openlyrics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openlyrics.ui.theme.Palette

val BlockShape = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(18.dp)

/** Nagłówek sekcji: ikona, nazwa i cienka linia do krawędzi. */
@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Line))
    }
}

/** Grupa wierszy na ciemnoszarym tle. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(Palette.PanelHigh),
        content = content,
    )
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = Color.White) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** Wiersz z ikoną, tytułem, opisem i przełącznikiem w kształcie pigułki. */
@Composable
fun ToggleRow(icon: ImageVector, title: String, description: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!value) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.White else Palette.TextFaint)
            if (description != null) Text(description, fontSize = 12.sp, lineHeight = 16.sp, color = Palette.TextDim)
        }
        Switch(value, enabled)
    }
}

/** Płaski przełącznik: biała pigułka z czarną kropką, gdy włączony. */
@Composable
fun Switch(value: Boolean, enabled: Boolean = true) {
    val bg by animateColorAsState(if (value) Color.White else Color.White.copy(alpha = 0.14f), tween(160), label = "sw")
    val pos by animateFloatAsState(if (value) 1f else 0f, tween(160), label = "swPos")
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(BlockShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.3f))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .padding(start = (18 * pos).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (value) Palette.Ink else Color.White),
        )
    }
}

/** Wiersz z akcją po dotknięciu (np. „Wyczyść pamięć”). */
@Composable
fun ActionRow(icon: ImageVector, title: String, description: String?, danger: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (danger) Palette.Danger else Color.White)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (danger) Palette.Danger else Color.White)
            if (description != null) Text(description, fontSize = 12.sp, lineHeight = 16.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) PillButton(trailing, onClick = onClick)
    }
}

/** Suwak z nazwą i wartością. */
@Composable
fun SliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            Text(display(value), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        FlatSlider(value, range, steps, onChange, Modifier.padding(start = 44.dp, top = 4.dp))
    }
}

/**
 * Płaski suwak: cienka ścieżka, biała część do wartości i okrągły uchwyt. Dotknięcie albo
 * przeciągnięcie w dowolnym miejscu ustawia wartość; [steps] – liczba pośrednich kroków.
 */
@Composable
fun FlatSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(1e-6f)
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val change by androidx.compose.runtime.rememberUpdatedState(onChange)
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val thumb by animateFloatAsState(if (dragging) 22f else 18f, tween(120), label = "thumb")
    fun pick(x: Float, width: Int) {
        var f = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (steps > 0) f = kotlin.math.round(f * (steps + 1)) / (steps + 1)
        change(range.start + f * span)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(range, steps) {
                detectTapGestures { pick(it.x, size.width) }
            }
            .pointerInput(range, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; pick(it.x, size.width) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { ch, _ -> ch.consume(); pick(ch.position.x, size.width) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(BlockShape).background(Color.White.copy(alpha = 0.15f)))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(BlockShape).background(Color.White))
        Box(
            Modifier
                .offset(x = (maxWidth - thumb.dp) * fraction)
                .size(thumb.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Wybór jednej z kilku wartości – rząd pigułek, aktywna biała. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(icon: ImageVector, title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
        }
        FlowRow(
            Modifier.padding(start = 44.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (o in options) Chip(label(o), o == selected) { onPick(o) }
        }
    }
}

@Composable
fun Chip(text: String, active: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = 0.08f), tween(160), label = "chip")
    Row(
        Modifier
            .clip(BlockShape)
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 34.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (active) Palette.Ink else Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (active) Palette.Ink else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Biała pigułka – główna akcja. */
@Composable
fun PillButton(text: String, icon: ImageVector? = null, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(BlockShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (primary) Palette.Ink else Color.White
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Okrągły przycisk z ikoną; aktywny – biały z czarną ikoną. */
@Composable
fun RoundButton(icon: ImageVector, label: String, active: Boolean = false, size: Dp = 44.dp, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color.White else Palette.PanelHigher, tween(160), label = "rb")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = (if (active) Palette.Ink else Color.White).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size * 0.48f))
    }
}

/** Kolor do wyboru – kółko z obwódką dla zaznaczonego. */
@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .scale(if (selected) 1.08f else 1f)
            .clip(CircleShape)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

/** Ciemne pole tekstowe z podpowiedzią. */
@Composable
fun DarkField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    Box(
        modifier
            .clip(BlockShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Palette.TextFaint, fontSize = 14.sp)
        BasicTextField(
            value,
            onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }, onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
