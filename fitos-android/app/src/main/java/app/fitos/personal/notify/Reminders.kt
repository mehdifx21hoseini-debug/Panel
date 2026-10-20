package app.fitos.personal.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.habits.ReminderFacts
import app.fitos.core.domain.habits.ReminderRepository
import app.fitos.core.domain.habits.ReminderRules
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.Schedule
import app.fitos.personal.AppContainer
import app.fitos.personal.FitOsApplication
import app.fitos.personal.R
import app.fitos.personal.feature.habits.amountPair
import app.fitos.personal.ui.Fmt
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keeps one alarm per enabled reminder, set for its next time. */
interface ReminderScheduler {
    /** Brings the alarms in line with the saved reminders (after edits, boot, restore, clock changes). */
    suspend fun sync()
}

/** For tests and previews. */
object NoReminderScheduler : ReminderScheduler {
    override suspend fun sync() = Unit
}

class AlarmReminderScheduler(
    private val context: Context,
    private val reminders: ReminderRepository,
    private val habits: HabitRepository,
    private val clock: Clock,
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminder_alarms", Context.MODE_PRIVATE)

    override suspend fun sync() {
        val byId = habits.habits.first().associateBy { it.id }
        val active = reminders.list().filter { r ->
            r.enabled && r.type != ReminderType.WEEKLY_REPORT && (r.type != ReminderType.HABIT || byId[r.habitId]?.archived == false)
        }
        val now = ZonedDateTime.now(clock)
        active.forEach { schedule(it.id, ReminderRules.nextFire(it, now).toInstant().toEpochMilli()) }
        val ids = active.map { it.id }.toSet()
        prefs.getStringSet(KEY, emptySet()).orEmpty().filter { it !in ids }.forEach { cancel(it) }
        prefs.edit { putStringSet(KEY, ids) }
    }

    private fun intent(id: String, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).setData("fitos://reminder/$id".toUri()),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedule(id: String, at: Long) {
        val pending = intent(id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(id: String) {
        intent(id, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it) }
        Notifications.cancel(context, Notifications.reminderId(id))
    }

    companion object {
        const val ACTION_FIRE = "app.fitos.personal.REMINDER"
        private const val KEY = "scheduled"
    }
}

/** What a firing reminder would say; null when it has nothing to remind about. */
data class ReminderMessage(
    val title: String,
    val text: String,
    val destination: Destination,
    /** One-tap answer from the notification. */
    val action: ReminderAction? = null,
)

sealed interface ReminderAction {
    data class HabitDone(val habitId: String) : ReminderAction
    data class Water(val ml: Int) : ReminderAction
}

/** Decides and words a reminder from what is logged right now (see [ReminderRules.shouldNotify]). */
class ReminderDelivery(private val context: Context, private val container: AppContainer) {
    private val data get() = container.data

    suspend fun message(reminderId: String): ReminderMessage? {
        val r = data.reminders.get(reminderId)?.takeIf { it.enabled } ?: return null
        val today = LocalDate.now(container.clock)
        if (r.weekdays.isNotEmpty() && today.dayOfWeek !in r.weekdays) return null
        val day = data.dayFacts.factsOn(today)
        var facts = ReminderFacts(
            now = container.clock.instant(),
            waterMl = day.waterMl,
            waterTargetMl = day.waterTargetMl,
            weighedToday = day.weighed,
            trainedToday = day.trained,
            sessionOpen = data.workouts.active.first() != null,
            hasActiveProgram = data.programs.active.first() != null,
            lastBackupAt = container.settingsRepository.lastBackupAt.first(),
        )
        var habit: Habit? = null
        var habitLine = ""
        if (r.type == ReminderType.HABIT) {
            habit = data.habits.habits.first().firstOrNull { it.id == r.habitId }
            if (habit != null && HabitRules.isDue(habit, today)) {
                val weekStart = JalaliCalendar.startOfWeek(today)
                val results = HabitBoard.results(habit, data.habits.logs(weekStart, today).first(), data.dayFacts.facts(weekStart, today).first(), weekStart, today)
                val week = if (habit.frequency == HabitFrequency.WEEKLY) HabitRules.week(habit, weekStart, results, today) else null
                val result = results.getValue(today)
                facts = facts.copy(habitToday = result, habitWeekMet = week != null && week.done >= week.needed)
                habitLine = when {
                    week != null -> context.getString(R.string.reminder_habit_week, Fmt.int(week.done), Fmt.int(week.needed))
                    habit.kind != HabitKind.BOOLEAN && result.value != null && result.target != null ->
                        context.getString(R.string.reminder_habit_amount, amountPair(context, habit, result.value!!, result.target!!))
                    else -> context.getString(R.string.reminder_habit_open)
                }
            }
        }
        if (!ReminderRules.shouldNotify(r.type, facts)) return null
        return when (r.type) {
            ReminderType.HABIT -> ReminderMessage(
                habit!!.name, habitLine, Destination.HABITS,
                if (habit.kind == HabitKind.BOOLEAN && habit.system == null) ReminderAction.HabitDone(habit.id) else null,
            )
            ReminderType.WATER -> ReminderMessage(
                context.getString(R.string.reminder_water_title),
                if (facts.waterMl == null) context.getString(R.string.reminder_water_none)
                else context.getString(R.string.reminder_water_body, Fmt.liters(facts.waterMl!!), Fmt.liters(facts.waterTargetMl ?: 0)),
                Destination.HABITS,
                ReminderAction.Water(LifestyleRules.GLASS_ML),
            )
            ReminderType.WEIGH_IN -> ReminderMessage(context.getString(R.string.reminder_weigh_title), context.getString(R.string.reminder_weigh_body), Destination.HOME)
            ReminderType.WORKOUT -> {
                val program = data.programs.active.first()
                val next = program?.let { Schedule.nextAfter(it, data.workouts.lastCompleted(it.id).first(), today) }
                ReminderMessage(
                    context.getString(R.string.reminder_workout_title),
                    next?.let { context.getString(R.string.reminder_workout_body, it.name) } ?: context.getString(R.string.reminder_workout_generic),
                    Destination.TRAINING,
                )
            }
            ReminderType.BACKUP -> ReminderMessage(
                context.getString(R.string.reminder_backup_title),
                facts.lastBackupAt?.let { context.getString(R.string.reminder_backup_body, Fmt.int(Duration.between(it, facts.now).toDays().toInt())) }
                    ?: context.getString(R.string.reminder_backup_never),
                Destination.BACKUP,
            )
            ReminderType.WEEKLY_REPORT -> null
        }
    }

    /** Posts the reminder when it has something to say; true when it did. */
    suspend fun deliver(reminderId: String): Boolean {
        val m = message(reminderId) ?: return false
        Notifications.ensureChannels(context)
        val id = Notifications.reminderId(reminderId)
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(m.title)
            .setContentText(m.text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Notifications.open(context, m.destination))
            // A reminder is for today only.
            .setTimeoutAfter(Duration.ofHours(12).toMillis())
        when (val a = m.action) {
            is ReminderAction.HabitDone -> builder.addAction(0, context.getString(R.string.reminder_action_done), ReminderActionReceiver.intent(context, id, a))
            is ReminderAction.Water -> builder.addAction(0, context.getString(R.string.reminder_action_water, Fmt.int(a.ml)), ReminderActionReceiver.intent(context, id, a))
            null -> Unit
        }
        Notifications.post(context, id, builder.build())
        return true
    }
}

private val Context.container: AppContainer get() = (applicationContext as FitOsApplication).container

/** A reminder's time came: post it if there is something to do, then set the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        val container = context.container
        container.scope.launch {
            try {
                ReminderDelivery(context.applicationContext, container).deliver(id)
                container.reminderScheduler.sync()
            } finally {
                pending.finish()
            }
        }
    }
}

/** The one-tap buttons on a reminder: mark the habit done, or log a glass of water. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = context.container
        container.scope.launch {
            try {
                val today = LocalDate.now(container.clock)
                when (intent.action) {
                    ACTION_HABIT -> intent.getStringExtra(EXTRA_HABIT)?.let { container.data.habits.setLog(it, today, HabitStatus.DONE) }
                    ACTION_WATER -> container.data.water.add(today, intent.getIntExtra(EXTRA_ML, LifestyleRules.GLASS_ML))
                }
                Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION, 0))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_HABIT = "app.fitos.personal.HABIT_DONE"
        private const val ACTION_WATER = "app.fitos.personal.ADD_WATER"
        private const val EXTRA_HABIT = "habit"
        private const val EXTRA_ML = "ml"
        private const val EXTRA_NOTIFICATION = "notification"

        fun intent(context: Context, notificationId: Int, action: ReminderAction): PendingIntent {
            val i = Intent(context, ReminderActionReceiver::class.java).putExtra(EXTRA_NOTIFICATION, notificationId)
            when (action) {
                is ReminderAction.HabitDone -> i.setAction(ACTION_HABIT).putExtra(EXTRA_HABIT, action.habitId)
                is ReminderAction.Water -> i.setAction(ACTION_WATER).putExtra(EXTRA_ML, action.ml)
            }
            return PendingIntent.getBroadcast(context, notificationId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** Alarms do not survive a restart, an update or a clock change: set them again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        val container = context.container
        container.scope.launch {
            try {
                container.reminderScheduler.sync()
                container.jobs.ensureScheduled()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
