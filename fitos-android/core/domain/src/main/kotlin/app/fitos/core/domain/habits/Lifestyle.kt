package app.fitos.core.domain.habits

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

data class WaterEntry(val id: String, val day: LocalDate, val loggedAt: Instant, val amountMl: Int)

interface WaterRepository {
    /** The day's drinks, oldest first. */
    fun entries(day: LocalDate): Flow<List<WaterEntry>>

    /** Millilitres per day between [from] and [to]; days without a drink are missing, not zero. */
    fun totals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    suspend fun add(day: LocalDate, amountMl: Int): String

    /** Soft delete; [restore] is the undo. */
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

/** One night, filed under the day of waking up. */
data class SleepEntry(
    val id: String,
    val day: LocalDate,
    val bedtime: LocalDateTime?,
    val wake: LocalDateTime?,
    val durationMinutes: Int,
    /** 1 (poor) to 5 (great), or null when not rated. */
    val quality: Int?,
    val note: String?,
)

interface SleepRepository {
    /** Nights between [from] and [to] (by wake day), oldest first. */
    fun entries(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>>

    /** Saves the night that ended on [day], replacing one already logged for that day. */
    suspend fun save(day: LocalDate, bedtime: LocalTime, wake: LocalTime, quality: Int?, note: String?): String

    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

enum class ActivityKind(val code: String) {
    WALK("walk"),
    /** Only a step count, e.g. read from a pedometer. */
    STEPS("steps"),
    HOME_WORKOUT("home_workout"),
    OTHER("other");

    companion object {
        fun fromCode(code: String): ActivityKind = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

data class ActivityEntry(
    val id: String,
    val day: LocalDate,
    val kind: ActivityKind,
    val steps: Int?,
    val durationMinutes: Int?,
    val note: String?,
    val loggedAt: Instant,
)

data class ActivityInput(val kind: ActivityKind, val steps: Int?, val durationMinutes: Int?, val note: String?)

interface ActivityRepository {
    fun entries(from: LocalDate, to: LocalDate): Flow<List<ActivityEntry>>

    suspend fun add(day: LocalDate, input: ActivityInput): String
    suspend fun update(id: String, input: ActivityInput)
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

/** Accepted values and small calculations for water, sleep and activity. */
object LifestyleRules {
    val WATER_ML = 50..3000
    val SLEEP_MINUTES = 30..16 * 60
    val ACTIVITY_MINUTES = 1..600
    val STEPS = 1..100_000
    const val GLASS_ML = 250

    /**
     * Bed and wake times on the morning of [day]: a bedtime later than the wake time was the
     * evening before. Returns both moments and the minutes in between.
     */
    fun night(day: LocalDate, bedtime: LocalTime, wake: LocalTime): Triple<LocalDateTime, LocalDateTime, Int> {
        val wakeAt = day.atTime(wake)
        val bedAt = if (bedtime < wake) day.atTime(bedtime) else day.minusDays(1).atTime(bedtime)
        return Triple(bedAt, wakeAt, Duration.between(bedAt, wakeAt).toMinutes().toInt())
    }

    enum class ActivityProblem { EMPTY, MINUTES, STEPS }

    fun check(input: ActivityInput): Set<ActivityProblem> = buildSet {
        if (input.durationMinutes == null && input.steps == null) add(ActivityProblem.EMPTY)
        if (input.durationMinutes != null && input.durationMinutes !in ACTIVITY_MINUTES) add(ActivityProblem.MINUTES)
        if (input.steps != null && input.steps !in STEPS) add(ActivityProblem.STEPS)
    }
}
