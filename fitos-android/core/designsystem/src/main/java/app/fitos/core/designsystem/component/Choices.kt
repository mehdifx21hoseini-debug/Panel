package app.fitos.core.designsystem.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle

/** A selectable card with a title and an explanation; use inside a Column with Modifier.selectableGroup(). */
@Composable
fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    emoji: FitEmoji? = null,
) {
    val colors = FitTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = FitSpacing.s16, vertical = FitSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
    ) {
        if (emoji != null) EmojiTile(emoji, size = 40.dp, tint = if (selected) colors.surface else colors.surfaceContainer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline),
        )
    }
}

/** Single choice from 2–4 short options. */
@Composable
fun <T> FitSegmented(options: List<T>, selected: T?, onSelect: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primary,
                    activeContentColor = colors.onPrimary,
                    activeBorderColor = colors.primary,
                    inactiveContainerColor = colors.surface,
                    inactiveContentColor = colors.onSurface,
                    inactiveBorderColor = colors.outline,
                ),
                icon = {},
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
            )
        }
    }
}

/** Chips that wrap onto new lines. [multi] = checkboxes, otherwise radio buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FitChips(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    multi: Boolean = true,
) {
    val colors = FitTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth().then(if (multi) Modifier else Modifier.selectableGroup()),
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
    ) {
        options.forEach { option ->
            val isOn = option in selected
            FilterChip(
                selected = isOn,
                onClick = { onToggle(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (isOn) {
                    { Icon(FitIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surface,
                    labelColor = colors.onSurface,
                    selectedContainerColor = colors.primaryContainer,
                    selectedLabelColor = colors.onPrimaryContainer,
                    selectedLeadingIconColor = colors.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isOn,
                    borderColor = colors.outline,
                    selectedBorderColor = colors.primary,
                ),
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

/** − value + control with 48dp targets. */
@Composable
fun FitStepper(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = FitTheme.colors
    val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        disabledContainerColor = colors.surfaceContainer,
        disabledContentColor = colors.outline,
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onDecrease, enabled = canDecrease, colors = buttonColors, modifier = Modifier.size(48.dp)) {
            Icon(FitIcons.Minus, contentDescription = decreaseLabel)
        }
        Text(
            valueText,
            style = numeralStyle(22),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease, colors = buttonColors, modifier = Modifier.size(48.dp)) {
            Icon(FitIcons.Plus, contentDescription = increaseLabel)
        }
    }
}

/** A short tinted note: neutral for explanations, warning for things worth a second look. */
@Composable
fun FitNote(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val colors = FitTheme.colors
    val bg = if (warning) colors.errorContainer else colors.surfaceContainer
    val fg = if (warning) colors.onErrorContainer else colors.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(FitSpacing.s12)
            .clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
    ) {
        Icon(if (warning) FitIcons.Alert else FitIcons.Info, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg, modifier = Modifier.weight(1f))
    }
}

