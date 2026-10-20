package app.fitos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle

/** One day in the week strip. [isDone] must come from real logs; [description] is the spoken label. */
data class WeekDay(
    val initial: String,
    val number: String,
    val description: String,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isDone: Boolean,
)

/** Seven-day strip for the hero band: today is a white pill, done days carry a dot, future days are dimmer. */
@Composable
fun WeekStrip(days: List<WeekDay>, modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        days.forEach { day ->
            val fg = when {
                day.isToday -> colors.heroBand
                day.isFuture -> colors.onHeroBandVariant
                else -> colors.onHeroBand
            }
            val dot = when {
                day.isToday && day.isDone -> colors.tertiary
                day.isDone -> colors.onHeroBand
                else -> Color.Transparent
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (day.isToday) colors.onHeroBand else Color.Transparent)
                    .padding(vertical = FitSpacing.s8)
                    .clearAndSetSemantics { contentDescription = day.description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FitSpacing.s2),
            ) {
                Text(day.initial, style = MaterialTheme.typography.bodySmall, color = fg)
                Text(day.number, style = numeralStyle(17), color = fg)
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            }
        }
    }
}
