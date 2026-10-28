package com.blacksingularities.openmesh.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Pill = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(22.dp)

/** Płaskie tło panelu (bez rozmycia – lekkie dla GPU i baterii). */
fun Modifier.panel(shape: Shape = CardShape, color: Color = Palette.Card): Modifier = this.clip(shape).background(color)

/** Karta z treścią w kolumnie. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    color: Color = Palette.Card,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().panel(CardShape, color).padding(padding), content = content)
}

/** Przycisk-pigułka: biały (główny), ciemny (drugorzędny) albo czerwony (niebezpieczny). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val bg = when {
        !enabled -> Palette.CardHigh
        danger -> Palette.Sos
        primary -> Color.White
        else -> Palette.CardHigh
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        danger -> Color.White
        primary -> Palette.Ink
        else -> Color.White
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(Pill)
            .background(bg)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** Okrągły przycisk z ikoną; aktywny = biały blok z czarną ikoną. */
@Composable
fun IconCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp,
    tint: Color? = null,
    background: Color = Palette.CardHigh,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (active) Color.White else background, tween(160), label = "iconBg")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = when {
                !enabled -> Palette.TextFaint
                active -> Palette.Ink
                else -> tint ?: Color.White
            },
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Palette.TextFaint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Wiersz w grupie ustawień. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    titleColor: Color = Color.White,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.CardHigher), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = Palette.TextDim, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing()
    }
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(title, subtitle = subtitle, icon = icon, onClick = { if (enabled) onChange(!checked) }) {
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Ink,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.CardHigher,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun SettingSlider(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    label: (Int) -> String,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        SettingRow(title, subtitle = subtitle, icon = icon) {
            Text(label(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Palette.CardHigher,
                activeTickColor = Palette.Ink.copy(alpha = 0.3f),
                inactiveTickColor = Color.White.copy(alpha = 0.15f),
            ),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
    }
}

/** Separator między wierszami w karcie ustawień (cienka linia u góry, nie po lewej). */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Palette.Line))
}

/** Grupa ustawień: karta bez wewnętrznych odstępów. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().panel(CardShape, Palette.Card), content = content)
}

/** Okrągły awatar z inicjałami w kolorze nicku. */
@Composable
fun Avatar(name: String, id: String, modifier: Modifier = Modifier, size: Dp = 46.dp, online: Boolean? = null, icon: ImageVector? = null) {
    val color = Palette.nick(id)
    Box(modifier.size(size)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.46f))
            } else {
                Text(initials(name), color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
            }
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(Palette.Ink)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(if (online) Palette.Online else Palette.CardHigher),
            )
        }
    }
}

fun initials(name: String): String {
    val parts = name.trim().removePrefix("#").split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, color: Color = Color.White) {
    if (count <= 0) return
    Box(
        modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp).clip(Pill).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = if (color == Color.White) Palette.Ink else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Mała plakietka z tekstem (np. „E2E”, „2 skoki”). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Palette.TextDim, background: Color = Palette.CardHigher) {
    Row(
        modifier.clip(Pill).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Pole tekstowe w pigułce / zaokrąglonej karcie. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 6,
    leading: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    shape: Shape = RoundedCornerShape(18.dp),
    background: Color = Palette.CardHigh,
    border: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 21.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(background)
                    .then(if (border) Modifier.border(1.dp, Palette.Line, shape) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, tint = Palette.TextDim, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = Palette.TextFaint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        },
    )
}

/** Wybór jednej z opcji – segmenty w pigułce, aktywny jest biały. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(Pill).background(Palette.CardHigh).padding(4.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) Color.White else Color.Transparent, tween(160), label = "seg")
            Box(
                Modifier.weight(1f).clip(Pill).background(bg).clickable { onSelect(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) Palette.Ink else Palette.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}
