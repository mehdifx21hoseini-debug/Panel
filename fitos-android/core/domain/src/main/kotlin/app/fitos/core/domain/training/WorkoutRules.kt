package app.fitos.core.domain.training

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.round

/** Load, tonnage and estimated one-rep max. */
object Strength {
    /** Epley: w × (1 + r/30), only for 1–12 reps where it holds reasonably; a single is the weight itself. */
    fun e1rm(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || weightKg <= 0 || reps !in 1..12) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }

    /**
     * Load moved per rep. Weighted moves: the weight. Body-weight moves: the exercise's share of
     * body weight (only when that share is set and a body weight is known) plus any added weight.
     */
    fun load(exercise: Exercise, set: SetLog, bodyWeightKg: Double?): Double = when (exercise.logType) {
        LogType.WEIGHT_REPS -> set.weightKg ?: 0.0
        LogType.REPS -> {
            val body = if (exercise.bodyweightPercent != null && bodyWeightKg != null) bodyWeightKg * exercise.bodyweightPercent / 100.0 else 0.0
            body + (set.weightKg ?: 0.0)
        }
        LogType.TIME, LogType.DISTANCE -> 0.0
    }

    /** Σ load × reps over counted sets (warm-ups excluded). */
    fun tonnage(exercise: Exercise, sets: List<SetLog>, bodyWeightKg: Double?): Double =
        sets.filter { it.type.counts }.sumOf { load(exercise, it, bodyWeightKg) * (it.reps ?: 0) }
}

/** Personal records, rebuilt from the full history of an exercise. */
object Records {
    fun compute(exercise: Exercise, history: List<ExercisePerformance>): List<PersonalRecord> {
        val ordered = history.sortedWith(compareBy({ it.day }, { it.sets.minOfOrNull { s -> s.completedAt } ?: Instant.MAX }))
        val best = linkedMapOf<Pair<RecordType, Int?>, PersonalRecord>()

        // Strictly better replaces, so a tie keeps the first time it was done.
        fun offer(type: RecordType, reps: Int?, value: Double, p: ExercisePerformance, set: SetLog?) {
            if (value <= 0) return
            val key = type to reps
            val current = best[key]
            if (current == null || value > current.value + 1e-9) best[key] = PersonalRecord(type, reps, value, p.sessionId, set?.id, p.day)
        }

        for (p in ordered) {
            val sets = p.working.sortedBy { it.completedAt }
            when (exercise.logType) {
                LogType.WEIGHT_REPS -> sets.forEach { s ->
                    val w = s.weightKg ?: return@forEach
                    val r = s.reps ?: return@forEach
                    if (r > 0) offer(RecordType.MAX_WEIGHT_FOR_REPS, r, w, p, s)
                    Strength.e1rm(w, r)?.let { offer(RecordType.E1RM, null, it, p, s) }
                }
                LogType.REPS -> sets.forEach { s -> s.reps?.let { offer(RecordType.MAX_REPS_BODYWEIGHT, null, it.toDouble(), p, s) } }
                LogType.TIME, LogType.DISTANCE -> Unit
            }
            offer(RecordType.SESSION_TONNAGE, null, Strength.tonnage(exercise, sets, p.bodyWeightKg), p, null)
        }
        return best.values.toList()
    }

    /**
     * Records first reached in [sessionId]. Nothing counts when the exercise has no earlier session:
     * a first time is a starting point, not a record.
     */
    fun setIn(sessionId: String, records: List<PersonalRecord>, history: List<ExercisePerformance>): List<PersonalRecord> {
        val session = history.firstOrNull { it.sessionId == sessionId } ?: return emptyList()
        val hadEarlier = history.any { it.sessionId != sessionId && it.day <= session.day && it.working.isNotEmpty() }
        if (!hadEarlier) return emptyList()
        return records.filter { it.sessionId == sessionId }
    }
}

enum class SuggestionKind { FIRST_TIME, INCREASE, HOLD, DECREASE, RETURN, REPEAT }

enum class SuggestionReason {
    /** No earlier session of this exercise. */
    NO_HISTORY,
    /** Every working set reached the top of the rep range with the planned reserve. */
    TOP_OF_RANGE,
    /** Still inside the rep range: same weight, more reps. */
    IN_RANGE,
    /** Two or more sets fell below the range. */
    BELOW_RANGE,
    /** A set was marked poor (or not good, when the rule asks for good quality). */
    QUALITY,
    /** Top reps were reached closer to failure than planned. */
    RIR,
    /** Pain was marked. */
    PAIN,
    /** More than [Progression.BREAK_DAYS] days since the last time. */
    BREAK,
    /** No progression rule: repeat last time. */
    NO_RULE,
    /** An easier session chosen after a break ([ReturnPlan]). */
    EASY_RETURN,
}

/** A suggestion is only ever shown with its reason; the user can reject or edit it. */
data class Suggestion(
    val kind: SuggestionKind,
    val reason: SuggestionReason,
    val weightKg: Double?,
    val reps: Int?,
    val previousWeightKg: Double?,
    val daysSince: Long?,
)

/** Double progression: reps first, then weight, never blindly. */
object Progression {
    const val BREAK_DAYS = 10
    const val BREAK_PERCENT = 10
    const val DEFAULT_STEP_KG = 2.5
    const val DEFAULT_DELOAD_PERCENT = 10

    fun step(exercise: Exercise, rule: ProgressionRule?): Double = rule?.incrementKg ?: exercise.weightIncrementKg ?: DEFAULT_STEP_KG

    fun suggest(exercise: Exercise, targets: SessionTargets, rule: ProgressionRule?, last: ExercisePerformance?, today: LocalDate): Suggestion {
        val working = last?.working.orEmpty()
        val daysSince = last?.let { ChronoUnit.DAYS.between(it.day, today) }
        val repMin = targets.repMin
        val repMax = targets.repMax
        if (last == null || working.isEmpty()) {
            return Suggestion(SuggestionKind.FIRST_TIME, SuggestionReason.NO_HISTORY, targets.weightKg, repMin ?: repMax, null, null)
        }
        val lastReps = working.first().reps

        if (exercise.logType != LogType.WEIGHT_REPS) {
            return when {
                daysSince != null && daysSince > BREAK_DAYS -> Suggestion(SuggestionKind.RETURN, SuggestionReason.BREAK, null, repMin ?: lastReps, null, daysSince)
                working.any { it.pain } -> Suggestion(SuggestionKind.DECREASE, SuggestionReason.PAIN, null, repMin ?: lastReps, null, daysSince)
                exercise.logType == LogType.REPS && repMax != null && working.size >= targets.sets && working.all { (it.reps ?: 0) >= repMax } ->
                    Suggestion(SuggestionKind.INCREASE, SuggestionReason.TOP_OF_RANGE, null, repMax, null, daysSince)
                exercise.logType == LogType.REPS && repMax != null -> Suggestion(SuggestionKind.HOLD, SuggestionReason.IN_RANGE, null, repMax, null, daysSince)
                else -> Suggestion(SuggestionKind.REPEAT, SuggestionReason.NO_RULE, null, lastReps, null, daysSince)
            }
        }

        val previous = working.mapNotNull { it.weightKg }.maxOrNull()
            ?: return Suggestion(SuggestionKind.FIRST_TIME, SuggestionReason.NO_HISTORY, targets.weightKg, repMin ?: repMax, null, daysSince)
        val top = working.filter { it.weightKg == previous }
        val step = step(exercise, rule)
        val deload = rule?.deloadPercent ?: DEFAULT_DELOAD_PERCENT

        if (daysSince != null && daysSince > BREAK_DAYS) {
            return Suggestion(SuggestionKind.RETURN, SuggestionReason.BREAK, reduce(previous, BREAK_PERCENT, step), repMin ?: lastReps, previous, daysSince)
        }
        if (rule == null || rule.type == ProgressionType.NONE) {
            return Suggestion(SuggestionKind.REPEAT, SuggestionReason.NO_RULE, previous, lastReps, previous, daysSince)
        }
        if (working.any { it.pain }) {
            return Suggestion(SuggestionKind.DECREASE, SuggestionReason.PAIN, reduce(previous, deload, step), repMin ?: lastReps, previous, daysSince)
        }
        if (repMin != null && working.count { (it.reps ?: 0) < repMin } >= 2) {
            return Suggestion(SuggestionKind.DECREASE, SuggestionReason.BELOW_RANGE, reduce(previous, deload, step), repMin, previous, daysSince)
        }
        val poor = working.any { it.quality == SetQuality.POOR }
        val notGood = rule.requireGoodQuality && working.any { it.quality == SetQuality.OK }
        if (poor || notGood) {
            return Suggestion(SuggestionKind.HOLD, SuggestionReason.QUALITY, previous, repMax ?: lastReps, previous, daysSince)
        }
        val allTop = repMax != null && top.size >= targets.sets && top.all { (it.reps ?: 0) >= repMax }
        if (allTop) {
            val rirOk = targets.rir == null || top.all { it.rir == null || it.rir >= targets.rir }
            return if (rirOk) {
                Suggestion(SuggestionKind.INCREASE, SuggestionReason.TOP_OF_RANGE, roundTo(previous + step, step), repMin ?: repMax, previous, daysSince)
            } else {
                Suggestion(SuggestionKind.HOLD, SuggestionReason.RIR, previous, repMax, previous, daysSince)
            }
        }
        return Suggestion(SuggestionKind.HOLD, SuggestionReason.IN_RANGE, previous, repMax ?: lastReps, previous, daysSince)
    }

    /** About [percent] lighter, on the nearest multiple of [step], always at least one step lighter, never below zero. */
    fun reduce(weightKg: Double, percent: Int, step: Double): Double {
        val target = weightKg * (1 - percent / 100.0)
        var result = round(target / step) * step
        if (result >= weightKg - 1e-9) result = weightKg - step
        return roundTo(result.coerceAtLeast(0.0), 0.01)
    }

    private fun roundTo(value: Double, step: Double): Double = round(value / step) * step
}

/** Where the session goes after a set, supersets included. */
object SessionFlow {
    data class Step(val next: Int?, val rest: Boolean)

    /** The first exercise not yet done, in order; null when everything is done or skipped. */
    fun firstOpen(session: WorkoutSession): Int? = session.exercises.indexOfFirst { !it.isDone }.takeIf { it >= 0 }

    /**
     * After a set of exercise [index]: inside a superset the next member follows without rest;
     * after the last member comes the rest and then the first member again. Otherwise the same
     * exercise continues after the rest until its sets are done.
     */
    fun after(session: WorkoutSession, index: Int): Step {
        val list = session.exercises
        val current = list.getOrNull(index) ?: return Step(firstOpen(session), rest = false)
        val group = current.supersetGroup
        val members = if (group == null) listOf(index) else supersetAround(session, index)
        if (members.size > 1) {
            members.firstOrNull { it > index && !list[it].isDone }?.let { return Step(it, rest = false) }
            members.firstOrNull { !list[it].isDone }?.let { return Step(it, rest = true) }
        } else if (!current.isDone) {
            return Step(index, rest = true)
        }
        val after = (index + 1 until list.size).firstOrNull { !list[it].isDone } ?: firstOpen(session)
        return Step(after, rest = after != null)
    }

    /** Neighbouring exercises with the same superset group as [index]. */
    fun supersetAround(session: WorkoutSession, index: Int): List<Int> {
        val list = session.exercises
        val group = list[index].supersetGroup ?: return listOf(index)
        var start = index
        while (start > 0 && list[start - 1].supersetGroup == group) start--
        var end = index
        while (end < list.lastIndex && list[end + 1].supersetGroup == group) end++
        return (start..end).toList()
    }

    /** Where to open the session: after the last logged set, or the first open exercise. */
    fun resumeAt(session: WorkoutSession): Int? {
        val last = lastSet(session) ?: return firstOpen(session) ?: 0.takeIf { session.exercises.isNotEmpty() }
        return after(session, last.first).next ?: last.first
    }

    /** When the rest after the last logged set ends, if that set is followed by rest. */
    fun restEndsAt(session: WorkoutSession, defaultRestSec: Int = 90): Instant? {
        val (index, set) = lastSet(session) ?: return null
        if (!after(session, index).rest) return null
        val seconds = session.exercises[index].targets.restSec ?: defaultRestSec
        if (seconds <= 0) return null
        return set.completedAt.plusSeconds(seconds.toLong())
    }

    private fun lastSet(session: WorkoutSession): Pair<Int, SetLog>? =
        session.exercises.withIndex()
            .flatMap { (i, e) -> e.sets.map { i to it } }
            .maxByOrNull { it.second.completedAt }
}
