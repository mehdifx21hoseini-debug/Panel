package app.fitos.core.domain.habits

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow

enum class ReminderType(val code: String) {
    HABIT("habit"),
    WEIGH_IN("weigh_in"),
    WATER("water"),
    WORKOUT("workout"),
    BACKUP("backup"),
    WEEKLY_REPORT("weekly_report");

    companion object {
        fun fromCode(code: String): ReminderType = entries.firstOrNull { it.code == code } ?: HABIT
    }
}

/** One notification at [time] on [weekdays] (every day when empty). */
data class Reminder(
    val id: String,
    val type: ReminderType,
    /** For [ReminderType.HABIT]. */
    val habitId: String?,
    val time: LocalTime,
    val weekdays: Set<DayOfWeek>,
    val enabled: Boolean,
)

data class ReminderInput(
    val type: ReminderType,
    val habitId: String?,
    val time: LocalTime,
    val weekdays: Set<DayOfWeek>,
    val enabled: Boolean = true,
)

interface ReminderRepository {
    val all: Flow<List<Reminder>>

    suspend fun list(): List<Reminder>
    suspend fun get(id: String): Reminder?

    /** Creates a reminder (null [id]) or replaces one; returns its id. */
    suspend fun save(id: String?, input: ReminderInput): String
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
}

/** What a reminder looks at when it fires, so it stays quiet when there is nothing to do. */
data class ReminderFacts(
    val now: Instant,
    /** Habit reminders: the habit today, or null when it is gone, archived or not due. */
    val habitToday: DayResult? = null,
    /** Weekly habits: true once the week's target is met. */
    val habitWeekMet: Boolean = false,
    val waterMl: Int? = null,
    val waterTargetMl: Int? = null,
    val weighedToday: Boolean = false,
    val trainedToday: Boolean = false,
    val sessionOpen: Boolean = false,
    val hasActiveProgram: Boolean = false,
    val lastBackupAt: Instant? = null,
)

object ReminderRules {
    /** A backup reminder only speaks when the last backup is older than this. */
    const val BACKUP_DUE_DAYS = 7L

    /** The first moment strictly after [after] at the reminder's time on one of its weekdays. */
    fun nextFire(reminder: Reminder, after: ZonedDateTime): ZonedDateTime {
        var day = after.toLocalDate()
        repeat(8) {
            val at = day.atTime(reminder.time).atZone(after.zone)
            if (at.isAfter(after) && (reminder.weekdays.isEmpty() || day.dayOfWeek in reminder.weekdays)) return at
            day = day.plusDays(1)
        }
        error("no weekday matched")
    }

    /**
     * One reminder never repeats itself, and it says nothing when the thing is already done:
     * a habit done, skipped or not due today, water at target, a weigh-in or workout already
     * logged, or a recent backup.
     */
    fun shouldNotify(type: ReminderType, facts: ReminderFacts): Boolean = when (type) {
        ReminderType.HABIT -> {
            val today = facts.habitToday
            today != null && !facts.habitWeekMet && today.outcome != Outcome.DONE && today.outcome != Outcome.SKIPPED
        }
        ReminderType.WATER -> facts.waterTargetMl == null || (facts.waterMl ?: 0) < facts.waterTargetMl
        ReminderType.WEIGH_IN -> !facts.weighedToday
        ReminderType.WORKOUT -> facts.hasActiveProgram && !facts.trainedToday && !facts.sessionOpen
        ReminderType.BACKUP -> backupDue(facts.lastBackupAt, facts.now)
        // Arrives with the weekly report (stage 8).
        ReminderType.WEEKLY_REPORT -> false
    }

    fun backupDue(lastBackupAt: Instant?, now: Instant): Boolean =
        lastBackupAt == null || Duration.between(lastBackupAt, now).toDays() >= BACKUP_DUE_DAYS
}
