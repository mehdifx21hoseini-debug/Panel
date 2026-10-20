package app.fitos.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme

/**
 * Elevated card: surface, hairline border and a soft blue-tinted shadow (alpha < .08).
 * In dark the tonal surface and border carry the depth; shadows are invisible on navy.
 * [hero] uses the larger corner and padding of the screen's focal card; list rows with trailing
 * icon buttons pass a tighter [contentPadding].
 */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FitTheme.colors
    val shape = RoundedCornerShape(if (hero) 32.dp else 24.dp)
    val shadowInk = Color(0xFF0F1A33).copy(alpha = 0.08f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (colors.isDark) Modifier else Modifier.shadow(6.dp, shape, ambientColor = shadowInk, spotColor = shadowInk))
            .clip(shape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.hairline), shape)
            .padding(contentPadding ?: PaddingValues(if (hero) FitSpacing.s20 else FitSpacing.s16)),
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}

/**
 * The blue header band of the Home screen, with a curved bottom edge. It draws behind the status bar
 * and leaves [overlap] of empty space at the bottom so the next card can float over its edge.
 */
@Composable
fun HeroBand(
    modifier: Modifier = Modifier,
    overlap: Dp = 72.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FitTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(colors.heroBand)
            .statusBarsPadding()
            .padding(start = FitSpacing.gutter, end = FitSpacing.gutter, top = FitSpacing.s12, bottom = overlap),
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onHeroBand) { content() }
    }
}

/**
 * Moves the element up by [by] and gives that space back to the layout, so a card can float over
 * the edge of the [HeroBand] above it while the items below follow without a gap.
 */
fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
