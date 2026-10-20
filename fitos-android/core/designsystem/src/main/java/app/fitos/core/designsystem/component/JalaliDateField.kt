package app.fitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.time.JalaliDate
import java.time.LocalDate

/**
 * Jalali date input as three menus (day, month, year), right to left like a written Persian date.
 * Reports a date only once all three parts are chosen; the day is clamped to the month's length.
 * [years] lists the selectable Jalali years, shown newest first.
 */
@Composable
fun JalaliDateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    years: IntRange,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
) {
    val colors = FitTheme.colors
    val initial = date?.let(JalaliDate::of)
    var year by remember(date) { mutableStateOf(initial?.year) }
    var month by remember(date) { mutableStateOf(initial?.month) }
    var day by remember(date) { mutableStateOf(initial?.day) }

    fun commit() {
        val y = year ?: return
        val m = month ?: return
        val d = (day ?: return).coerceAtMost(JalaliCalendar.monthLength(y, m))
        day = d
        onDateChange(JalaliCalendar.toLocalDate(y, m, d))
    }

    val maxDay = if (year != null && month != null) JalaliCalendar.monthLength(year!!, month!!) else 31
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            DatePartMenu(
                label = "روز",
                value = day?.let { PersianNumbers.digits(it.toString()) },
                options = (1..maxDay).toList(),
                optionText = { PersianNumbers.digits(it.toString()) },
                onSelect = { day = it; commit() },
                isError = error != null,
                modifier = Modifier.weight(0.9f),
            )
            DatePartMenu(
                label = "ماه",
                value = month?.let { JalaliCalendar.MONTH_NAMES[it - 1] },
                options = (1..12).toList(),
                optionText = { JalaliCalendar.MONTH_NAMES[it - 1] },
                onSelect = { month = it; commit() },
                isError = error != null,
                modifier = Modifier.weight(1.4f),
            )
            DatePartMenu(
                label = "سال",
                value = year?.let { PersianNumbers.digits(it.toString()) },
                options = years.reversed().toList(),
                optionText = { PersianNumbers.digits(it.toString()) },
                onSelect = { year = it; commit() },
                isError = error != null,
                modifier = Modifier.weight(1.1f),
            )
        }
        val below = error ?: hint
        if (below != null) {
            Text(
                below,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.onSurfaceVariant,
                modifier = if (error != null) Modifier.semantics { error(error) } else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DatePartMenu(
    label: String,
    value: String?,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = FitTheme.colors
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            // No arrow icon: its 48dp slot would leave too little room for "اردیبهشت" on small phones.
            label = { Text(label, maxLines = 1) },
            isError = isError && value == null,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                errorContainerColor = colors.surface,
            ),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
