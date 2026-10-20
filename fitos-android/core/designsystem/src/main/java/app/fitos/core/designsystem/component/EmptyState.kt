package app.fitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme

/** Honest empty state: what this place is for, and what to do (or when it arrives). */
@Composable
fun EmptyState(
    emoji: FitEmoji,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = FitSpacing.s24, vertical = FitSpacing.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
    ) {
        EmojiTile(emoji, size = 96.dp, tint = FitTheme.colors.primaryContainer)
        Text(title, style = MaterialTheme.typography.titleLarge, color = FitTheme.colors.onSurface, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
