package app.fitos.personal.feature.habits

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitSegmented
import app.fitos.core.designsystem.component.FitStepper
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.habits.ActivityUnit
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.Reminder
import app.fitos.core.domain.habits.ReminderInput
import app.fitos.core.domain.habits.ReminderRepository
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.notify.ReminderScheduler
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The editor's fields as typed. */
data class HabitForm(
    val loaded: Boolean = false,
    val name: String = "",
    val emoji: FitEmoji? = null,
    val system: SystemHabit? = null,
    val kind: HabitKind = HabitKind.BOOLEAN,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val times: Int = 3,
    val target: String = "",
    val unit: String = "",
    val activityUnit: String = ActivityUnit.MINUTES,
    val archived: Boolean = false,
    val problems: Set<HabitRules.Problem> = emptySet(),
) {
    val effectiveKind: HabitKind get() = system?.kind ?: kind
    val effectiveFrequency: HabitFrequency get() = system?.frequency ?: if (kind == HabitKind.BOOLEAN) frequency else HabitFrequency.DAILY
    val followsTargets: Boolean get() = system == SystemHabit.WATER || system == SystemHabit.SLEEP

    fun input(): HabitInput {
        val weekly = effectiveFrequency == HabitFrequency.WEEKLY
        return HabitInput(
            name = name,
            emoji = emoji?.name,
            kind = effectiveKind,
            system = system,
            frequency = effectiveFrequency,
            target = when {
                weekly -> times.toDouble()
                followsTargets -> null
                effectiveKind == HabitKind.BOOLEAN -> null
                else -> PersianNumbers.parse(target)
            },
            unit = when {
                system == SystemHabit.ACTIVITY -> activityUnit
                system == null && kind == HabitKind.QUANTITY -> unit
                else -> null
            },
            weekdays = if (weekly) emptySet() else weekdays,
        )
    }

    companion object {
        fun of(h: Habit) = HabitForm(
            loaded = true,
            name = h.name,
            emoji = h.emojiIcon.takeIf { h.emoji != null },
            system = h.system,
            kind = h.kind,
            frequency = h.frequency,
            weekdays = h.weekdays,
            times = if (h.frequency == HabitFrequency.WEEKLY) (h.target ?: 3.0).toInt() else 3,
            target = if (h.frequency == HabitFrequency.DAILY) h.target?.let { Fmt.field(it, 1) }.orEmpty() else "",
            unit = if (h.system == null) h.unit.orEmpty() else "",
            activityUnit = if (h.system == SystemHabit.ACTIVITY) h.unit ?: ActivityUnit.MINUTES else ActivityUnit.MINUTES,
            archived = h.archived,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HabitEditorViewModel(
    private val habits: HabitRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    targets: NutritionTargetRepository,
    today: LocalDate,
    private val habitId: String?,
) : ViewModel() {
    private val _form = MutableStateFlow(HabitForm(loaded = habitId == null))
    val form: StateFlow<HabitForm> = _form.asStateFlow()

    /** Reminders of a new habit wait here until it is saved. */
    private val pending = MutableStateFlow<List<Reminder>>(emptyList())

    val habitReminders: StateFlow<List<Reminder>> = (if (habitId == null) pending else reminders.all.map { all -> all.filter { it.habitId == habitId } })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val target: StateFlow<NutritionTarget?> = targets.effectiveOn(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (habitId != null) viewModelScope.launch { habits.habit(habitId).first()?.let { h -> _form.value = HabitForm.of(h) } }
    }

    fun edit(change: (HabitForm) -> HabitForm) = _form.update { change(it).copy(problems = emptySet()) }

    fun save(onSaved: () -> Unit) {
        val input = _form.value.input()
        val problems = HabitRules.check(input)
        if (problems.isNotEmpty()) {
            _form.update { it.copy(problems = problems) }
            return
        }
        viewModelScope.launch {
            if (habitId == null) {
                val id = habits.add(input)
                pending.value.forEach { r -> reminders.save(null, ReminderInput(ReminderType.HABIT, id, r.time, r.weekdays, r.enabled)) }
            } else {
                habits.update(habitId, input)
            }
            scheduler.sync()
            onSaved()
        }
    }

    fun setArchived(archived: Boolean) {
        val id = habitId ?: return
        viewModelScope.launch {
            habits.setArchived(id, archived)
            _form.update { it.copy(archived = archived) }
            scheduler.sync()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = habitId ?: return
        viewModelScope.launch {
            habits.delete(id)
            scheduler.sync()
            onDeleted()
        }
    }

    fun saveReminder(existing: Reminder?, time: LocalTime, days: Set<DayOfWeek>) {
        if (habitId == null) {
            val r = Reminder(existing?.id ?: "pending-${System.nanoTime()}", ReminderType.HABIT, null, time, days, true)
            pending.update { list -> if (existing == null) list + r else list.map { if (it.id == existing.id) r else it } }
            return
        }
        viewModelScope.launch {
            reminders.save(existing?.id, ReminderInput(ReminderType.HABIT, habitId, time, days, existing?.enabled ?: true))
            scheduler.sync()
        }
    }

    fun setReminderEnabled(r: Reminder, on: Boolean) {
        if (habitId == null) {
            pending.update { list -> list.map { if (it.id == r.id) it.copy(enabled = on) else it } }
            return
        }
        viewModelScope.launch {
            reminders.setEnabled(r.id, on)
            scheduler.sync()
        }
    }

    fun deleteReminder(r: Reminder) {
        if (habitId == null) {
            pending.update { list -> list.filter { it.id != r.id } }
            return
        }
        viewModelScope.launch {
            reminders.delete(r.id)
            scheduler.sync()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    viewModel: HabitEditorViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: (String) -> Unit,
    onOpenTargets: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val reminders by viewModel.habitReminders.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var reminderSheet by remember { mutableStateOf<Reminder?>(null) }
    var newReminder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (!form.loaded) return

    EditScaffold(
        title = stringResource(if (isNew) R.string.habit_new_title else R.string.habit_edit_title),
        onBack = onBack,
        bottomBar = { FitPrimaryButton(stringResource(R.string.action_save), onClick = { viewModel.save(onSaved) }, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth()) },
    ) {
        form.system?.let { s ->
            FitNote(stringResource(R.string.habit_system_label) + " · " + stringResource(s.about))
        }
        FitTextField(
            form.name, { v -> viewModel.edit { it.copy(name = v.take(HabitRules.NAME_MAX + 5)) } }, stringResource(R.string.habit_name),
            error = if (HabitRules.Problem.NAME in form.problems) stringResource(R.string.habit_name_error) else null,
        )
        FieldLabel(stringResource(R.string.habit_emoji))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            val options = listOfNotNull(form.system?.emoji) + HABIT_EMOJI
            options.distinct().forEachIndexed { i, e ->
                val selected = (form.emoji ?: form.system?.emoji ?: FitEmoji.SPARKLES) == e
                val description = stringResource(R.string.habit_emoji_option, Fmt.int(i + 1))
                EmojiTile(
                    e, size = 44.dp, tint = if (selected) colors.primaryContainer else colors.surfaceContainer,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(14.dp)) else Modifier)
                        .selectable(selected, role = Role.RadioButton) { viewModel.edit { it.copy(emoji = e) } }
                        .semantics { contentDescription = description },
                )
            }
        }

        if (form.system == null) {
            FieldLabel(stringResource(R.string.habit_kind))
            FitSegmented(
                HabitKind.entries, form.kind, { k -> viewModel.edit { it.copy(kind = k) } },
                label = {
                    stringResource(
                        when (it) {
                            HabitKind.BOOLEAN -> R.string.habit_kind_boolean
                            HabitKind.QUANTITY -> R.string.habit_kind_quantity
                            HabitKind.DURATION -> R.string.habit_kind_duration
                        },
                    )
                },
            )
            if (form.kind == HabitKind.BOOLEAN) {
                FieldLabel(stringResource(R.string.habit_frequency))
                FitSegmented(
                    HabitFrequency.entries, form.frequency, { f -> viewModel.edit { it.copy(frequency = f) } },
                    label = { stringResource(if (it == HabitFrequency.DAILY) R.string.habit_daily else R.string.habit_weekly) },
                )
            }
        }

        if (form.effectiveFrequency == HabitFrequency.WEEKLY) {
            FitStepper(
                label = stringResource(R.string.habit_times_week),
                valueText = Fmt.int(form.times),
                onDecrease = { viewModel.edit { it.copy(times = (it.times - 1).coerceAtLeast(1)) } },
                onIncrease = { viewModel.edit { it.copy(times = (it.times + 1).coerceAtMost(7)) } },
                canDecrease = form.times > 1,
                canIncrease = form.times < 7,
                decreaseLabel = stringResource(R.string.action_decrease),
                increaseLabel = stringResource(R.string.action_increase),
            )
        } else {
            FieldLabel(stringResource(R.string.habit_days))
            WeekdayChips(form.weekdays) { d -> viewModel.edit { it.copy(weekdays = if (d in it.weekdays) it.weekdays - d else it.weekdays + d) } }
            Text(stringResource(R.string.habit_days_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        }

        when {
            form.followsTargets -> {
                val t = target
                val value = when {
                    t == null -> "—"
                    form.system == SystemHabit.WATER -> stringResource(R.string.amount_liters, Fmt.liters(t.waterMl))
                    else -> durationText(t.sleepMinutes)
                }
                FitNote(stringResource(R.string.habit_target_from_targets, value))
                FitSecondaryButton(stringResource(R.string.habit_open_targets), onClick = onOpenTargets, icon = FitIcons.Target, modifier = Modifier.fillMaxWidth())
            }
            form.system == SystemHabit.ACTIVITY -> {
                FieldLabel(stringResource(R.string.habit_activity_unit))
                FitSegmented(
                    listOf(ActivityUnit.MINUTES, ActivityUnit.STEPS), form.activityUnit, { u -> viewModel.edit { it.copy(activityUnit = u) } },
                    label = { stringResource(if (it == ActivityUnit.STEPS) R.string.unit_steps_word else R.string.unit_minutes_word) },
                )
                TargetField(form, viewModel)
            }
            form.effectiveKind != HabitKind.BOOLEAN && form.effectiveFrequency == HabitFrequency.DAILY -> {
                TargetField(form, viewModel)
                if (form.system == null && form.kind == HabitKind.QUANTITY) {
                    FitTextField(form.unit, { v -> viewModel.edit { it.copy(unit = v.take(16)) } }, stringResource(R.string.habit_unit))
                }
            }
        }
        if (HabitRules.Problem.WEEKLY_TIMES in form.problems) FitNote(stringResource(R.string.habit_times_error), warning = true)

        SectionTitle(stringResource(R.string.habit_reminders))
        Text(stringResource(R.string.habit_reminders_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        if (reminders.isNotEmpty()) {
            FitCard {
                reminders.forEach { r ->
                    ReminderRow(r, onOpen = { reminderSheet = r }, onToggle = { on -> viewModel.setReminderEnabled(r, on) })
                }
            }
        }
        FitSecondaryButton(stringResource(R.string.reminder_add_time), onClick = { newReminder = true }, icon = FitIcons.Bell, modifier = Modifier.fillMaxWidth())

        if (!isNew) {
            SectionTitle(stringResource(R.string.habit_manage))
            Text(stringResource(R.string.habit_archive_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            FitSecondaryButton(
                stringResource(if (form.archived) R.string.habit_unarchive else R.string.habit_archive),
                onClick = { viewModel.setArchived(!form.archived) },
                icon = FitIcons.Download,
                modifier = Modifier.fillMaxWidth(),
            )
            FitSecondaryButton(stringResource(R.string.habit_delete), onClick = { confirmDelete = true }, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
        }
    }

    if (newReminder || reminderSheet != null) {
        val r = reminderSheet
        ReminderSheet(
            r?.time ?: LocalTime.of(20, 0), r?.weekdays ?: form.weekdays,
            onDismiss = { newReminder = false; reminderSheet = null },
            onSave = { time, days -> viewModel.saveReminder(r, time, days); newReminder = false; reminderSheet = null },
            onDelete = r?.let { { viewModel.deleteReminder(it); reminderSheet = null } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.habit_delete_title, form.name)) },
            text = { Text(stringResource(R.string.habit_delete_body)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { confirmDelete = false; viewModel.delete { onDeleted(form.name) } }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { confirmDelete = false }) },
        )
    }
}

@Composable
private fun TargetField(form: HabitForm, viewModel: HabitEditorViewModel) {
    val minutes = form.effectiveKind == HabitKind.DURATION || (form.system == SystemHabit.ACTIVITY && form.activityUnit == ActivityUnit.MINUTES)
    FitNumberField(
        form.target, { v -> viewModel.edit { it.copy(target = v) } },
        stringResource(if (minutes) R.string.habit_target_minutes else R.string.habit_target),
        maxIntegerDigits = 6,
        error = if (HabitRules.Problem.TARGET in form.problems) stringResource(R.string.habit_target_error) else null,
    )
}

/** "۲۱:۳۰ · هر روز" with an on/off switch; tap to change. */
@Composable
internal fun ReminderRow(r: Reminder, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, title: String? = null) {
    val colors = FitTheme.colors
    val line = stringResource(R.string.reminder_line, timeText(r.time), weekdaysText(r.weekdays))
    val label = stringResource(R.string.reminder_toggle, line)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).heightIn(min = FitSpacing.target),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
    ) {
        IconButton(onClick = onOpen) { Icon(FitIcons.Edit, contentDescription = stringResource(R.string.habit_edit) + " " + line, tint = colors.onSurfaceVariant) }
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            Text(line, style = MaterialTheme.typography.bodyLarge, color = if (r.enabled) colors.onSurface else colors.subtle)
        }
        Switch(
            checked = r.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
