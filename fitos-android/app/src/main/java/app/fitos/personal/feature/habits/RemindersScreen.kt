package app.fitos.personal.feature.habits

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitStepper
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.Reminder
import app.fitos.core.domain.habits.ReminderInput
import app.fitos.core.domain.habits.ReminderRepository
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.training.ReturnPlan
import app.fitos.personal.R
import app.fitos.personal.notify.Notifications
import app.fitos.personal.notify.ReminderScheduler
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemindersUi(
    val loaded: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val habits: Map<String, Habit> = emptyMap(),
    val returnDays: Int = ReturnPlan.DEFAULT_THRESHOLD_DAYS,
    val returnNotify: Boolean = true,
)

class RemindersViewModel(
    private val reminders: ReminderRepository,
    habits: HabitRepository,
    private val scheduler: ReminderScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {
    val ui: StateFlow<RemindersUi> = combine(reminders.all, habits.habits, settings.returnThresholdDays, settings.returnNotify) { r, h, days, notify ->
        RemindersUi(true, r, h.associateBy { it.id }, days, notify)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUi())

    fun save(existing: Reminder?, type: ReminderType, time: LocalTime, days: Set<DayOfWeek>) = viewModelScope.launch {
        reminders.save(existing?.id, ReminderInput(type, existing?.habitId, time, days, existing?.enabled ?: true))
        scheduler.sync()
    }

    fun setEnabled(r: Reminder, on: Boolean) = viewModelScope.launch {
        reminders.setEnabled(r.id, on)
        scheduler.sync()
    }

    fun delete(r: Reminder) = viewModelScope.launch {
        reminders.delete(r.id)
        scheduler.sync()
    }

    fun setReturnDays(days: Int) = viewModelScope.launch { settings.setReturnThresholdDays(days) }
    fun setReturnNotify(on: Boolean) = viewModelScope.launch { settings.setReturnNotify(on) }
}

private val ReminderType.title: Int
    get() = when (this) {
        ReminderType.WEIGH_IN -> R.string.reminder_type_weigh_in
        ReminderType.WATER -> R.string.reminder_type_water
        ReminderType.WORKOUT -> R.string.reminder_type_workout
        ReminderType.BACKUP -> R.string.reminder_type_backup
        ReminderType.WEEKLY_REPORT -> R.string.reminder_type_report
        else -> R.string.reminder_type_habits
    }

private val ReminderType.about: Int
    get() = when (this) {
        ReminderType.WEIGH_IN -> R.string.reminder_weigh_about
        ReminderType.WATER -> R.string.reminder_water_about
        ReminderType.WORKOUT -> R.string.reminder_workout_about
        ReminderType.BACKUP -> R.string.reminder_backup_about
        ReminderType.WEEKLY_REPORT -> R.string.reminder_report_about
        else -> R.string.habit_reminders_hint
    }

private val ReminderType.emoji: FitEmoji
    get() = when (this) {
        ReminderType.WEIGH_IN -> FitEmoji.SCALE
        ReminderType.WATER -> FitEmoji.DROPLET
        ReminderType.WORKOUT -> FitEmoji.LIFTING
        ReminderType.BACKUP -> FitEmoji.FLOPPY
        ReminderType.WEEKLY_REPORT -> FitEmoji.BARCHART
        else -> FitEmoji.BELL
    }

/** Sensible first times: weigh-in before breakfast, water mid-morning, workout before noon, backup and the weekly report on Friday evening. */
private val ReminderType.defaultTime: LocalTime
    get() = when (this) {
        ReminderType.WEIGH_IN -> LocalTime.of(7, 30)
        ReminderType.WATER -> LocalTime.of(11, 0)
        ReminderType.WORKOUT -> LocalTime.of(11, 30)
        ReminderType.BACKUP -> LocalTime.of(20, 0)
        ReminderType.WEEKLY_REPORT -> LocalTime.of(20, 30)
        else -> LocalTime.of(20, 0)
    }

private val ReminderType.defaultDays: Set<DayOfWeek>
    get() = if (this == ReminderType.BACKUP || this == ReminderType.WEEKLY_REPORT) setOf(DayOfWeek.FRIDAY) else emptySet()

@Composable
fun RemindersScreen(viewModel: RemindersViewModel, onBack: () -> Unit, onOpenHabit: (String) -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = FitTheme.colors
    var canNotify by remember { mutableStateOf(Notifications.canNotify(context)) }
    var exact by remember { mutableStateOf(canScheduleExact(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = Notifications.canNotify(context)
        exact = canScheduleExact(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }
    var editing by remember { mutableStateOf<Pair<ReminderType, Reminder?>?>(null) }
    if (!ui.loaded) return

    EditScaffold(title = stringResource(R.string.reminders_title), onBack = onBack) {
        Text(stringResource(R.string.reminders_about), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            FitCard {
                Text(stringResource(R.string.reminders_permission_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.reminders_permission_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                FitSecondaryButton(stringResource(R.string.reminders_permission_button), onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, icon = FitIcons.Bell, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!exact) {
            FitNote(stringResource(R.string.reminders_exact_body), warning = true)
            FitSecondaryButton(
                stringResource(R.string.reminders_exact_button),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }
                },
                icon = FitIcons.Clock,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        listOf(ReminderType.WEIGH_IN, ReminderType.WATER, ReminderType.WORKOUT, ReminderType.WEEKLY_REPORT, ReminderType.BACKUP).forEach { type ->
            FitCard {
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile(type.emoji)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(type.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(stringResource(type.about), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                    }
                }
                ui.reminders.filter { it.type == type }.forEach { r ->
                    ReminderRow(r, onOpen = { editing = type to r }, onToggle = { on -> viewModel.setEnabled(r, on) })
                }
                FitSecondaryButton(stringResource(R.string.reminder_add_time), onClick = { editing = type to null }, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
            }
        }

        FitCard {
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(FitEmoji.CHECK)
                Text(stringResource(R.string.reminder_type_habits), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            }
            val habitReminders = ui.reminders.filter { it.type == ReminderType.HABIT && ui.habits[it.habitId] != null }
            if (habitReminders.isEmpty()) Text(stringResource(R.string.reminders_habits_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            habitReminders.forEach { r ->
                val habit = ui.habits.getValue(r.habitId!!)
                ReminderRow(r, onOpen = { onOpenHabit(habit.id) }, onToggle = { on -> viewModel.setEnabled(r, on) }, title = habit.name)
            }
        }

        SectionTitle(stringResource(R.string.return_settings_title))
        FitCard {
            Text(stringResource(R.string.return_settings_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            FitStepper(
                label = stringResource(R.string.return_threshold),
                valueText = stringResource(R.string.return_threshold_value, Fmt.int(ui.returnDays)),
                onDecrease = { viewModel.setReturnDays(ui.returnDays - 1) },
                onIncrease = { viewModel.setReturnDays(ui.returnDays + 1) },
                canDecrease = ui.returnDays > ReturnPlan.THRESHOLD_DAYS.first,
                canIncrease = ui.returnDays < ReturnPlan.THRESHOLD_DAYS.last,
                decreaseLabel = stringResource(R.string.action_decrease),
                increaseLabel = stringResource(R.string.action_increase),
            )
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = FitSpacing.target), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.return_notify), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = ui.returnNotify, onCheckedChange = viewModel::setReturnNotify, colors = SwitchDefaults.colors(checkedTrackColor = colors.primary))
            }
        }

        SectionTitle(stringResource(R.string.reminders_battery_title))
        Text(stringResource(R.string.reminders_battery_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        FitSecondaryButton(
            stringResource(R.string.reminders_battery_button),
            onClick = { open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            icon = FitIcons.Settings,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    editing?.let { (type, r) ->
        ReminderSheet(
            r?.time ?: type.defaultTime, r?.weekdays ?: type.defaultDays,
            onDismiss = { editing = null },
            onSave = { time, days -> viewModel.save(r, type, time, days); editing = null },
            onDelete = r?.let { { viewModel.delete(it); editing = null } },
        )
    }
}

private fun canScheduleExact(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/** Opens a system settings page; some phones lack one, which is then simply ignored. */
private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
