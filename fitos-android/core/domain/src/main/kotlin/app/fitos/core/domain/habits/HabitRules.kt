package app.fitos.core.domain.habits

import app.fitos.core.domain.time.JalaliCalendar
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/** What was logged on one day, as the system habits read it. Null = nothing of that kind was logged. */
data class DayFacts(
    val waterMl: Int? = null,
    val waterTargetMl: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepTargetMinutes: Int? = null,
    val trained: Boolean = false,
    val homeWorkout: Boolean = false,
    val weighed: Boolean = false,
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val kcalTarget: Int? = null,
    val proteinTargetG: Int? = null,
    val activityMinutes: Int? = null,
    val steps: Int? = null,
)

enum class Outcome {
    DONE,
    /** Some of a daily amount. */
    PARTIAL,
    NOT_DONE,
    SKIPPED,
    /** Nothing answered and nothing logged: shown as such and counted as zero, never as done. */
    NOT_LOGGED,
}

/** One habit on one day. */
data class DayResult(
    val outcome: Outcome,
    /** Amount reached, for quantity and duration habits. */
    val value: Double?,
    /** The day's target, where one applies. */
    val target: Double?,
    /** Read from the logs of a system habit rather than answered by hand. */
    val auto: Boolean,
) {
    /** Share of the day's goal from 0 to 1; null when the day does not count (skipped). */
    val score: Double?
        get() = when (outcome) {
            Outcome.DONE -> 1.0
            Outcome.SKIPPED -> null
            Outcome.PARTIAL -> if (value != null && target != null && target > 0) (value / target).coerceIn(0.0, 1.0) else 0.0
            Outcome.NOT_DONE, Outcome.NOT_LOGGED -> 0.0
        }
}

/** A weekly habit's week. */
data class WeekResult(
    val weekStart: LocalDate,
    val done: Int,
    /** Times needed this week: the habit's target, fewer when days were skipped. */
    val needed: Int,
    /** False while the week runs and the target is not reached yet. */
    val complete: Boolean,
) {
    val score: Double? get() = if (needed <= 0) null else min(done.toDouble() / needed, 1.0)
}

/** Average adherence over a period. [score] is null when no day or week could be judged yet. */
data class Adherence(val score: Double?, val counted: Int, val full: Int)

data class TrendPoint(val weekStart: LocalDate, val score: Double?, val inProgress: Boolean)

/**
 * How habits are judged. Missing a day never resets anything: adherence is the average share of
 * the goal over the days (or weeks) that count, so one bad day only weighs one day.
 */
object HabitRules {
    const val NAME_MAX = 40
    val WEEKLY_TIMES = 1..7
    val QUANTITY_TARGET = 0.1..100_000.0
    val DURATION_TARGET = 1.0..600.0

    /** Nutrition habit: calories within ±10 % of the target and at least 90 % of the protein target. */
    const val KCAL_BAND = 0.10
    const val PROTEIN_SHARE = 0.90

    const val DEFAULT_HOME_PER_WEEK = 2
    const val DEFAULT_ACTIVITY_MINUTES = 30
    const val DEFAULT_STEPS = 7000

    enum class Problem { NAME, TARGET, WEEKLY_TIMES }

    fun check(input: HabitInput): Set<Problem> = buildSet {
        if (input.name.isBlank() || input.name.trim().length > NAME_MAX) add(Problem.NAME)
        if (input.frequency == HabitFrequency.WEEKLY) {
            val times = input.target
            if (times == null || times % 1.0 != 0.0 || times.toInt() !in WEEKLY_TIMES) add(Problem.WEEKLY_TIMES)
        } else if (input.system != SystemHabit.WATER && input.system != SystemHabit.SLEEP) {
            val range = when (input.kind) {
                HabitKind.BOOLEAN -> null
                HabitKind.QUANTITY -> QUANTITY_TARGET
                HabitKind.DURATION -> DURATION_TARGET
            }
            if (range != null && (input.target == null || input.target !in range)) add(Problem.TARGET)
        }
    }

    /** Daily habits are due on their weekdays; weekly ones may count on any day. Nothing before the habit existed. */
    fun isDue(habit: Habit, day: LocalDate): Boolean {
        if (habit.archived || day < habit.createdOn) return false
        return habit.frequency == HabitFrequency.WEEKLY || habit.weekdays.isEmpty() || day.dayOfWeek in habit.weekdays
    }

    /** The day's target: water and sleep follow the daily targets, the rest their own. */
    fun target(habit: Habit, facts: DayFacts?): Double? = when (habit.system) {
        SystemHabit.WATER -> facts?.waterTargetMl?.toDouble()
        SystemHabit.SLEEP -> facts?.sleepTargetMinutes?.toDouble()
        else -> if (habit.frequency == HabitFrequency.WEEKLY) null else habit.target
    }

    /** A hand-given answer wins; otherwise a system habit reads the logs, and anything else is "not logged". */
    fun day(habit: Habit, log: HabitLog?, facts: DayFacts?): DayResult {
        val target = target(habit, facts)
        if (log != null) {
            return when (log.status) {
                HabitStatus.SKIPPED -> DayResult(Outcome.SKIPPED, log.value, target, auto = false)
                HabitStatus.DONE -> DayResult(Outcome.DONE, log.value, target, auto = false)
                HabitStatus.NOT_DONE -> if (log.value != null && habit.kind != HabitKind.BOOLEAN) amount(log.value, target, auto = false)
                else DayResult(Outcome.NOT_DONE, log.value, target, auto = false)
            }
        }
        val system = habit.system ?: return DayResult(Outcome.NOT_LOGGED, null, target, auto = false)
        val f = facts ?: return DayResult(Outcome.NOT_LOGGED, null, target, auto = true)
        return when (system) {
            SystemHabit.WATER -> amount(f.waterMl?.toDouble(), target, auto = true)
            SystemHabit.SLEEP -> amount(f.sleepMinutes?.toDouble(), target, auto = true)
            SystemHabit.ACTIVITY -> amount(
                (if (habit.unit == ActivityUnit.STEPS) f.steps else f.activityMinutes)?.toDouble(),
                target,
                auto = true,
            )
            SystemHabit.TRAINING -> flag(f.trained)
            SystemHabit.HOME_ACTIVITY -> flag(f.homeWorkout)
            SystemHabit.WEIGH_IN -> flag(f.weighed)
            SystemHabit.NUTRITION -> nutrition(f)
        }
    }

    private fun flag(done: Boolean) = DayResult(if (done) Outcome.DONE else Outcome.NOT_LOGGED, null, null, auto = true)

    private fun amount(value: Double?, target: Double?, auto: Boolean): DayResult = when {
        value == null -> DayResult(Outcome.NOT_LOGGED, null, target, auto)
        target == null || value >= target -> DayResult(if (value > 0) Outcome.DONE else Outcome.NOT_DONE, value, target, auto)
        value > 0 -> DayResult(Outcome.PARTIAL, value, target, auto)
        else -> DayResult(Outcome.NOT_DONE, value, target, auto)
    }

    private fun nutrition(f: DayFacts): DayResult {
        val kcal = f.kcal ?: return DayResult(Outcome.NOT_LOGGED, null, null, auto = true)
        val kcalTarget = f.kcalTarget ?: return DayResult(Outcome.NOT_LOGGED, kcal, null, auto = true)
        val inBand = abs(kcal - kcalTarget) <= kcalTarget * KCAL_BAND
        val proteinOk = f.proteinTargetG == null || (f.proteinG ?: 0.0) >= f.proteinTargetG * PROTEIN_SHARE
        return DayResult(if (inBand && proteinOk) Outcome.DONE else Outcome.NOT_DONE, kcal, kcalTarget.toDouble(), auto = true)
    }

    /**
     * Whether a day enters the average. Skipped days never do. Today only does once it is done or
     * answered "not done" by hand, so an unfinished day is not held against you before it ends.
     */
    fun counts(result: DayResult, isToday: Boolean): Boolean = when {
        result.outcome == Outcome.SKIPPED -> false
        !isToday -> true
        result.outcome == Outcome.DONE -> true
        result.outcome == Outcome.NOT_DONE && !result.auto -> true
        else -> false
    }

    /**
     * Week of a weekly habit. Days before the habit existed and skipped days lower the times
     * needed, so a habit started on Thursday or a week off sick is not judged against a full week.
     */
    fun week(habit: Habit, weekStart: LocalDate, results: Map<LocalDate, DayResult>, today: LocalDate): WeekResult {
        val times = (habit.target ?: 1.0).toInt().coerceAtLeast(1)
        val span = (0L..6L).map { weekStart.plusDays(it) }.filter { it >= habit.createdOn }
        val done = span.count { it <= today && results[it]?.outcome == Outcome.DONE }
        val skipped = span.count { it <= today && results[it]?.outcome == Outcome.SKIPPED }
        val needed = min(times, span.size - skipped).coerceAtLeast(0)
        val ended = weekStart.plusDays(6) < today
        return WeekResult(weekStart, done, needed, complete = ended || done >= needed)
    }

    /**
     * Average share of the goal from [from] to [to] (at most [today]). Daily habits average their
     * due days; weekly habits average their weeks that are over (the running one only once its
     * target is met).
     */
    fun adherence(habit: Habit, from: LocalDate, to: LocalDate, today: LocalDate, results: Map<LocalDate, DayResult>): Adherence {
        val first = maxOf(from, habit.createdOn)
        val last = minOf(to, today)
        if (first > last) return Adherence(null, 0, 0)
        if (habit.frequency == HabitFrequency.WEEKLY) {
            val weeks = weekStarts(first, last)
                .map { week(habit, it, results, today) }
                .filter { it.complete && it.score != null }
            if (weeks.isEmpty()) return Adherence(null, 0, 0)
            return Adherence(weeks.sumOf { it.score!! } / weeks.size, weeks.size, weeks.count { it.score!! >= 1.0 })
        }
        val scores = generateSequence(first) { it.plusDays(1) }
            .takeWhile { it <= last }
            .filter { isDue(habit, it) }
            .mapNotNull { day ->
                val r = results[day] ?: DayResult(Outcome.NOT_LOGGED, null, null, auto = habit.system != null)
                if (counts(r, day == today)) r.score else null
            }
            .toList()
        if (scores.isEmpty()) return Adherence(null, 0, 0)
        return Adherence(scores.average(), scores.size, scores.count { it >= 1.0 })
    }

    /** One point per week (oldest first) for the last [weeks] weeks; the running week is marked. */
    fun trend(habit: Habit, weeks: Int, today: LocalDate, results: Map<LocalDate, DayResult>): List<TrendPoint> {
        val current = JalaliCalendar.startOfWeek(today)
        return (weeks - 1 downTo 0).map { ago ->
            val start = current.minusWeeks(ago.toLong())
            val score = when {
                start.plusDays(6) < habit.createdOn -> null
                habit.frequency == HabitFrequency.WEEKLY -> week(habit, start, results, today).score
                else -> adherence(habit, start, start.plusDays(6), today, results).score
            }
            TrendPoint(start, score, inProgress = ago == 0)
        }
    }

    /** Mean of several habits' adherence; habits that cannot be judged yet are left out. */
    fun overall(values: List<Adherence>): Adherence {
        val known = values.filter { it.score != null }
        if (known.isEmpty()) return Adherence(null, 0, 0)
        return Adherence(known.sumOf { it.score!! } / known.size, known.sumOf { it.counted }, known.sumOf { it.full })
    }

    /**
     * Whether the habit is on [day]'s list. Daily habits are listed on their due days; weekly ones
     * until the week's target is met (and on the days they were done).
     */
    fun listed(habit: Habit, day: LocalDate, result: DayResult, week: WeekResult?): Boolean {
        if (!isDue(habit, day)) return false
        if (habit.frequency == HabitFrequency.DAILY) return true
        val met = week != null && week.done >= week.needed
        return !met || result.outcome == Outcome.DONE || result.outcome == Outcome.SKIPPED
    }

    private fun weekStarts(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from > to) return emptyList()
        val first = JalaliCalendar.startOfWeek(from)
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= to }.toList()
    }
}
