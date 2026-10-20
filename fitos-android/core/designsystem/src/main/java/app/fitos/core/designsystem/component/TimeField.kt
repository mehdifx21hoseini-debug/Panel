package app.fitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import java.time.LocalTime

/** Hour and minute (5-minute steps) as two menus; read right to left like "۷:۳۰". */
@Composable
fun TimeField(time: LocalTime, onTimeChange: (LocalTime) -> Unit, label: String, modifier: Modifier = Modifier) {
    fun two(n: Int) = PersianNumbers.digits(n.toString().padStart(2, '0'))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = FitTheme.colors.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            DatePartMenu(
                label = "ساعت",
                value = two(time.hour),
                options = (0..23).toList(),
                optionText = { two(it) },
                onSelect = { onTimeChange(time.withHour(it)) },
                isError = false,
                modifier = Modifier.weight(1f),
            )
            DatePartMenu(
                label = "دقیقه",
                value = two(time.minute),
                options = (0..55 step 5).toList().let { if (time.minute % 5 != 0) (it + time.minute).sorted() else it },
                optionText = { two(it) },
                onSelect = { onTimeChange(time.withMinute(it)) },
                isError = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
