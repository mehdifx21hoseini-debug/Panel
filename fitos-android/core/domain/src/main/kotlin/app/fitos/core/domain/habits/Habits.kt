package app.fitos.core.domain.habits

import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** What one day of a habit records. */
enum class HabitKind(val code: String) {
    /** Done or not. */
    BOOLEAN("boolean"),
    /** A number towards a daily target, e.g. pages read. */
    QUANTITY("quantity"),
    /** Minutes towards a daily target. */
    DURATION("duration");

    companion object {
        fun fromCode(code: String): HabitKind = entries.firstOrNull { it.code == code } ?: BOOLEAN
    }
}

enum class HabitFrequency(val code: String) {
    /** Due on chosen weekdays (every day when none are chosen). */
    DAILY("daily"),
    /** A number of times per week, on any days. */
    WEEKLY("weekly");

    companion object {
        fun fromCode(code: String): HabitFrequency = entries.firstOrNull { it.code == code } ?: DAILY
    }
}

/**
 * Habits the app fills in by itself from what is logged elsewhere, so nothing is entered twice.
 * Water and sleep use the daily targets; the others carry their own.
 */
enum class SystemHabit(val code: String, val kind: HabitKind, val frequency: HabitFrequency) {
    WATER("water", HabitKind.QUANTITY, HabitFrequency.DAILY),
    SLEEP("sleep", HabitKind.DURATION, HabitFrequency.DAILY),
    /** Finished workouts per week. */
    TRAINING("training", HabitKind.BOOLEAN, HabitFrequency.WEEKLY),
    /** Calories within the target band and enough protein. */
    NUTRITION("nutrition", HabitKind.BOOLEAN, HabitFrequency.DAILY),
    WEIGH_IN("weigh_in", HabitKind.BOOLEAN, HabitFrequency.DAILY),
    /** Walking or other activity, in minutes or steps. */
    ACTIVITY("activity", HabitKind.QUANTITY, HabitFrequency.DAILY),
    /** A short home workout: the home or minimal version of a program day, or a logged home activity. */
    HOME_ACTIVITY("home_activity", HabitKind.BOOLEAN, HabitFrequency.WEEKLY);

    companion object {
        fun fromCode(code: String?): SystemHabit? = entries.firstOrNull { it.code == code }
    }
}

/** What the user answered for a day. No answer at all is "not logged", never "done". */
enum class HabitStatus(val code: String) {
    DONE("done"),
    NOT_DONE("not_done"),
    /** Excused (ill, travelling): the day does not count either way. */
    SKIPPED("skipped");

    companion object {
        fun fromCode(code: String): HabitStatus = entries.firstOrNull { it.code == code } ?: NOT_DONE
    }
}

/** Units of the activity habit. */
object ActivityUnit {
    const val MINUTES = "min"
    const val STEPS = "steps"
}

data class Habit(
    val id: String,
    val name: String,
    /** Key of one of the app's emoji; null = the default for its type. */
    val emoji: String?,
    val kind: HabitKind,
    val system: SystemHabit?,
    val frequency: HabitFrequency,
    /** Daily amount (quantity, minutes) or times per week (weekly). Null for water and sleep, which follow the daily targets. */
    val target: Double?,
    val unit: String?,
    /** Weekdays a daily habit is due; empty = every day. */
    val weekdays: Set<DayOfWeek>,
    val position: Int,
    val archived: Boolean,
    /** No day before this one counts. */
    val createdOn: LocalDate,
)

/** What the editor fills in. */
data class HabitInput(
    val name: String,
    val emoji: String?,
    val kind: HabitKind,
    val system: SystemHabit?,
    val frequency: HabitFrequency,
    val target: Double?,
    val unit: String?,
    val weekdays: Set<DayOfWeek>,
)

data class HabitLog(
    val habitId: String,
    val day: LocalDate,
    val status: HabitStatus,
    /** Amount for quantity and duration habits. */
    val value: Double?,
)

interface HabitRepository {
    /** Every habit that is not deleted (archived ones included), in the user's order. */
    val habits: Flow<List<Habit>>

    fun habit(id: String): Flow<Habit?>

    /** Answers between [from] and [to], both included. */
    fun logs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>>

    suspend fun add(input: HabitInput): String
    suspend fun update(id: String, input: HabitInput)
    suspend fun setArchived(id: String, archived: Boolean)

    /** Soft delete: the habit and its history disappear but can be brought back. */
    suspend fun delete(id: String)
    suspend fun restore(id: String)
    suspend fun move(id: String, by: Int)

    /** Sets the day's answer, replacing an earlier one. */
    suspend fun setLog(habitId: String, day: LocalDate, status: HabitStatus, value: Double? = null)

    /** Removes the day's answer, so it reads "not logged" (or follows the logs again for a system habit). */
    suspend fun clearLog(habitId: String, day: LocalDate)
}
