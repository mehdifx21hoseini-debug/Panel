package app.fitos.personal.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.JalaliDateField
import app.fitos.core.designsystem.component.TimeField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.body.WeightCondition
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.profile.ProfileRules
import app.fitos.core.domain.time.JalaliDate
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import java.time.LocalTime

/** What the sheet hands back on save; validated already. */
data class WeightInput(val weightKg: Double, val day: LocalDate, val time: LocalTime, val condition: WeightCondition?, val note: String?)

val WeightCondition.label: Int
    get() = when (this) {
        WeightCondition.FASTED -> R.string.condition_fasted
        WeightCondition.AFTER_MEAL -> R.string.condition_after_meal
        WeightCondition.OTHER -> R.string.condition_other
    }

/**
 * Bottom sheet to log or edit one weigh-in. New entries start with the last weight, today and
 * the current time; editing shows a delete action too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntrySheet(
    editing: WeightEntry?,
    lastWeightKg: Double?,
    today: LocalDate,
    now: LocalTime,
    onDismiss: () -> Unit,
    onSave: (WeightInput) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var weight by remember { mutableStateOf((editing?.weightKg ?: lastWeightKg)?.let { Fmt.field(it) }.orEmpty()) }
    var day by remember { mutableStateOf(editing?.day ?: today) }
    var time by remember { mutableStateOf(editing?.let { it.measuredAt.atZone(it.zone).toLocalTime() } ?: now.withSecond(0).withNano(0)) }
    var condition by remember { mutableStateOf(editing?.condition) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var showError by remember { mutableStateOf(false) }

    val parsed = PersianNumbers.parse(weight)
    val valid = parsed != null && parsed in ProfileRules.WEIGHT_KG
    val thisYear = JalaliDate.of(today).year

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FitTheme.colors.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
        ) {
            Text(
                stringResource(if (editing == null) R.string.weight_sheet_add else R.string.weight_sheet_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            FitNumberField(
                value = weight,
                onValueChange = { weight = it; showError = false },
                label = stringResource(R.string.field_weight_value),
                suffix = stringResource(R.string.unit_kg),
                maxIntegerDigits = 3,
                maxFractionDigits = 2,
                error = if (showError && !valid) stringResource(R.string.error_weight) else null,
            )
            JalaliDateField(
                date = day,
                onDateChange = { day = it },
                label = stringResource(R.string.field_date),
                years = (thisYear - 5)..thisYear,
                latest = today,
            )
            TimeField(time = time, onTimeChange = { time = it }, label = stringResource(R.string.field_time))
            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FieldLabel(stringResource(R.string.field_condition))
                FitChips(
                    options = WeightCondition.entries,
                    selected = setOfNotNull(condition),
                    onToggle = { condition = if (condition == it) null else it },
                    label = { stringResource(it.label) },
                    multi = false,
                )
            }
            FitTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")",
            )
            FitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    if (valid) onSave(WeightInput(parsed!!, day, time, condition, note)) else showError = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (onDelete != null) {
                Row(Modifier.fillMaxWidth()) {
                    FitSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
