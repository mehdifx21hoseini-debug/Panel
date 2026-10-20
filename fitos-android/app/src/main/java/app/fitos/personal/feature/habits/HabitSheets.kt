package app.fitos.personal.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.input.ImeAction
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.TimeField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.habits.ActivityEntry
import app.fitos.core.domain.habits.ActivityInput
import app.fitos.core.domain.habits.ActivityKind
import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SleepEntry
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** The shared frame of every sheet here: a title, scrolling content above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FitTheme.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
fun WaterAmountSheet(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val ml = PersianNumbers.parse(text)?.toInt()
    val valid = ml != null && ml in LifestyleRules.WATER_ML
    FitSheet(stringResource(R.string.water_add_other), onDismiss) {
        FitNumberField(
            text, { text = it }, stringResource(R.string.water_amount),
            allowDecimal = false,
            error = if (text.isNotEmpty() && !valid) stringResource(R.string.water_amount_error) else null,
            imeAction = ImeAction.Done,
        )
        FitPrimaryButton(stringResource(R.string.action_save), onClick = { ml?.let(onAdd) }, enabled = valid, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
    }
}

/** A night: bed and wake times (bed later than wake = the evening before), quality and a note. */
@Composable
fun SleepSheet(day: LocalDate, existing: SleepEntry?, onDismiss: () -> Unit, onSave: (LocalTime, LocalTime, Int?, String?) -> Unit, onDelete: (() -> Unit)?) {
    var bed by remember { mutableStateOf(existing?.bedtime?.toLocalTime() ?: LocalTime.of(23, 0)) }
    var wake by remember { mutableStateOf(existing?.wake?.toLocalTime() ?: LocalTime.of(7, 0)) }
    var quality by remember { mutableStateOf(existing?.quality) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    val minutes = LifestyleRules.night(day, bed, wake).third
    val valid = minutes in LifestyleRules.SLEEP_MINUTES
    FitSheet(stringResource(if (existing == null) R.string.sleep_log else R.string.sleep_edit), onDismiss) {
        Text(stringResource(R.string.sleep_night_of, DateFormats.longDate(day)), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        TimeField(bed, { bed = it }, stringResource(R.string.sleep_bedtime))
        TimeField(wake, { wake = it }, stringResource(R.string.sleep_wake))
        Text(
            if (valid) stringResource(R.string.sleep_duration, durationText(minutes)) else stringResource(R.string.sleep_invalid),
            style = MaterialTheme.typography.bodyMedium,
            color = if (valid) FitTheme.colors.onSurface else FitTheme.colors.error,
        )
        FieldLabel(stringResource(R.string.sleep_quality))
        FitChips((1..5).toList(), setOfNotNull(quality), { quality = if (quality == it) null else it }, label = { Fmt.int(it) }, multi = false)
        FitTextField(note, { note = it }, stringResource(R.string.activity_note), imeAction = ImeAction.Done)
        FitPrimaryButton(stringResource(R.string.action_save), onClick = { onSave(bed, wake, quality, note) }, enabled = valid, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) FitSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ActivitySheet(existing: ActivityEntry?, onDismiss: () -> Unit, onSave: (ActivityInput) -> Unit, onDelete: (() -> Unit)?) {
    var kind by remember { mutableStateOf(existing?.kind ?: ActivityKind.WALK) }
    var minutes by remember { mutableStateOf(existing?.durationMinutes?.let { Fmt.field(it) }.orEmpty()) }
    var steps by remember { mutableStateOf(existing?.steps?.let { Fmt.field(it) }.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var tried by remember { mutableStateOf(false) }
    val input = ActivityInput(
        kind = kind,
        steps = PersianNumbers.parse(steps)?.toInt(),
        durationMinutes = if (kind == ActivityKind.STEPS) null else PersianNumbers.parse(minutes)?.toInt(),
        note = note,
    )
    val problems = LifestyleRules.check(input)
    FitSheet(stringResource(R.string.activity_log), onDismiss) {
        FitChips(ActivityKind.entries, setOf(kind), { kind = it }, label = { stringResource(it.label) }, multi = false)
        if (kind != ActivityKind.STEPS) {
            FitNumberField(
                minutes, { minutes = it }, stringResource(R.string.activity_minutes), allowDecimal = false,
                error = when {
                    LifestyleRules.ActivityProblem.MINUTES in problems -> stringResource(R.string.activity_error_minutes)
                    tried && LifestyleRules.ActivityProblem.EMPTY in problems -> stringResource(R.string.activity_error_empty)
                    else -> null
                },
            )
        }
        FitNumberField(
            steps, { steps = it }, stringResource(if (kind == ActivityKind.STEPS) R.string.activity_steps_required else R.string.activity_steps),
            allowDecimal = false, maxIntegerDigits = 6,
            error = when {
                LifestyleRules.ActivityProblem.STEPS in problems -> stringResource(R.string.activity_error_steps)
                tried && kind == ActivityKind.STEPS && LifestyleRules.ActivityProblem.EMPTY in problems -> stringResource(R.string.activity_error_empty)
                else -> null
            },
        )
        FitTextField(note, { note = it }, stringResource(R.string.activity_note), imeAction = ImeAction.Done)
        FitPrimaryButton(
            stringResource(R.string.action_save),
            onClick = { if (problems.isEmpty()) onSave(input) else tried = true },
            icon = FitIcons.Check,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) FitSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Answer a habit for one day: done, not done, skipped (does not count), or clear the answer.
 * Quantity and duration habits take an amount instead of "done".
 */
@Composable
fun HabitDaySheet(
    habit: Habit,
    day: LocalDate,
    result: DayResult,
    onDismiss: () -> Unit,
    onAnswer: (HabitStatus, Double?) -> Unit,
    onClear: () -> Unit,
    onDetails: (() -> Unit)?,
) {
    val amount = habit.kind != HabitKind.BOOLEAN && habit.system == null
    var text by remember { mutableStateOf(result.value?.takeIf { !result.auto }?.let { Fmt.field(it, 1) }.orEmpty()) }
    val value = PersianNumbers.parse(text)
    FitSheet(stringResource(R.string.habit_value_title, habit.name, DateFormats.longDate(day)), onDismiss) {
        Text(statusLine(habit, result, null), style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.onSurfaceVariant)
        if (amount) {
            FitNumberField(
                text, { text = it },
                habit.unit?.let { stringResource(R.string.habit_value_label, it) }
                    ?: if (habit.kind == HabitKind.DURATION) stringResource(R.string.habit_value_label, stringResource(R.string.unit_minutes_word)) else stringResource(R.string.habit_value_label_plain),
                maxIntegerDigits = 5,
                imeAction = ImeAction.Done,
            )
            FitPrimaryButton(stringResource(R.string.action_save), onClick = { onAnswer(HabitStatus.NOT_DONE, value) }, enabled = value != null && value >= 0, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
        } else {
            FitPrimaryButton(stringResource(R.string.habit_action_done), onClick = { onAnswer(HabitStatus.DONE, null) }, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
            FitSecondaryButton(stringResource(R.string.habit_action_not_done), onClick = { onAnswer(HabitStatus.NOT_DONE, null) }, icon = FitIcons.Close, modifier = Modifier.fillMaxWidth())
        }
        FitSecondaryButton(stringResource(R.string.habit_action_skip), onClick = { onAnswer(HabitStatus.SKIPPED, null) }, modifier = Modifier.fillMaxWidth())
        if (!result.auto && result.outcome != Outcome.NOT_LOGGED) {
            FitSecondaryButton(stringResource(R.string.habit_action_clear), onClick = onClear, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
        }
        if (onDetails != null) FitSecondaryButton(stringResource(R.string.habit_action_details), onClick = onDetails, icon = FitIcons.Chart, modifier = Modifier.fillMaxWidth())
    }
}

/** Time and weekdays of one reminder. */
@Composable
fun ReminderSheet(initialTime: LocalTime, initialDays: Set<DayOfWeek>, onDismiss: () -> Unit, onSave: (LocalTime, Set<DayOfWeek>) -> Unit, onDelete: (() -> Unit)?) {
    var time by remember { mutableStateOf(initialTime) }
    var days by remember { mutableStateOf(initialDays) }
    FitSheet(stringResource(R.string.reminder_sheet_title), onDismiss) {
        TimeField(time, { time = it }, stringResource(R.string.reminder_time))
        FieldLabel(stringResource(R.string.reminder_days))
        WeekdayChips(days) { d -> days = if (d in days) days - d else days + d }
        Text(stringResource(R.string.reminder_days_hint), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        FitPrimaryButton(stringResource(R.string.action_save), onClick = { onSave(time, days) }, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) FitSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
    }
}

/** The seven days in Persian order (Saturday first). */
@Composable
fun WeekdayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    FitChips(JalaliCalendar.WEEK_ORDER, selected, onToggle, label = { JalaliCalendar.weekdayName(it) })
}

/** "۷:۳۰". */
fun timeText(time: LocalTime): String = PersianNumbers.digits("%d:%02d".format(time.hour, time.minute))
