package app.fitos.core.domain.training

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** Accepted ranges when planning (the UI explains each one). */
object TrainingRules {
    val SETS = 1..10
    val REPS = 1..100
    val RIR = 0..5
    val REST_SEC = 0..600
    val WEIGHT_KG = 0.0..500.0
    val DURATION_SEC = 5..3600
    val DISTANCE_M = 10..50_000
    val INCREMENT_KG = 0.25..20.0
    const val NAME_MAX = 40
    const val MAX_DAYS = 14
    const val MAX_EXERCISES = 20

    enum class Problem { SETS, REPS, REP_ORDER, RIR, REST, WEIGHT, DURATION, DISTANCE, INCREMENT }

    fun check(t: PlannedTargets, logType: LogType): Set<Problem> = buildSet {
        if (t.sets !in SETS) add(Problem.SETS)
        if (logType == LogType.WEIGHT_REPS || logType == LogType.REPS) {
            if (t.repMin == null || t.repMax == null || t.repMin !in REPS || t.repMax !in REPS) add(Problem.REPS)
            else if (t.repMin > t.repMax) add(Problem.REP_ORDER)
        }
        if (logType == LogType.TIME && (t.durationSec == null || t.durationSec !in DURATION_SEC)) add(Problem.DURATION)
        if (logType == LogType.DISTANCE && (t.distanceM == null || t.distanceM !in DISTANCE_M)) add(Problem.DISTANCE)
        if (t.targetRir != null && t.targetRir !in RIR) add(Problem.RIR)
        if (t.restSec != null && t.restSec !in REST_SEC) add(Problem.REST)
        if (t.startWeightKg != null && t.startWeightKg !in WEIGHT_KG) add(Problem.WEIGHT)
        val inc = t.progression?.incrementKg
        if (t.progression?.type == ProgressionType.DOUBLE_PROGRESSION && (inc == null || inc !in INCREMENT_KG)) add(Problem.INCREMENT)
    }

    enum class ExerciseProblem { NAME, PRIMARY, INCREMENT }

    /** An own exercise needs a name, at least one primary muscle and, if given, a sane weight step. */
    fun check(input: ExerciseInput): Set<ExerciseProblem> = buildSet {
        if (input.nameFa.isBlank() || input.nameFa.trim().length > NAME_MAX) add(ExerciseProblem.NAME)
        if (input.primary.isEmpty()) add(ExerciseProblem.PRIMARY)
        if (input.weightIncrementKg != null && input.weightIncrementKg !in INCREMENT_KG) add(ExerciseProblem.INCREMENT)
    }

    /** Sensible starting targets for a newly added exercise. */
    fun defaults(exercise: Exercise): PlannedTargets = when (exercise.logType) {
        LogType.WEIGHT_REPS -> PlannedTargets(
            sets = 3, repMin = 8, repMax = 12, targetRir = 2, durationSec = null, distanceM = null,
            startWeightKg = null, restSec = 90, supersetGroup = null, note = null,
            progression = ProgressionRule(ProgressionType.DOUBLE_PROGRESSION, exercise.weightIncrementKg ?: 2.5, 10, requireGoodQuality = true),
        )
        LogType.REPS -> PlannedTargets(3, 8, 15, 2, null, null, null, 75, null, null, null)
        LogType.TIME -> PlannedTargets(3, null, null, null, 30, null, null, 60, null, null, null)
        LogType.DISTANCE -> PlannedTargets(1, null, null, null, null, 2000, null, null, null, null, null)
    }
}

/** Which program day comes next. */
object Schedule {
    /**
     * Sequence mode: the training day after the last completed one, wrapping around; the first
     * training day when nothing is done yet. Weekday mode: today's day if it has one, otherwise
     * the next weekday that does; once today's session is done ([lastCompletedOn] is today), the
     * search starts tomorrow. Null when the program has no training days.
     */
    fun next(program: Program, lastCompletedDayId: String?, today: LocalDate, lastCompletedOn: LocalDate? = null): ProgramDay? {
        val training = program.trainingDays
        if (training.isEmpty()) return null
        return when (program.scheduleMode) {
            ScheduleMode.SEQUENCE -> {
                val days = program.days
                val last = days.indexOfFirst { it.id == lastCompletedDayId }
                if (last == -1) return training.first()
                (1..days.size).map { days[(last + it) % days.size] }.first { !it.isRest }
            }
            ScheduleMode.WEEKDAYS -> (if (lastCompletedOn == today) 1L..7L else 0L..6L).map { today.plusDays(it).dayOfWeek }
                .firstNotNullOfOrNull { weekday -> training.firstOrNull { it.preferredWeekday == weekday } }
                ?: training.first()
        }
    }

    fun nextAfter(program: Program, last: CompletedDay?, today: LocalDate): ProgramDay? = next(program, last?.dayId, today, last?.date)

    /** The program day planned for [weekday] (rest days included), for the week view. */
    fun dayOn(program: Program, weekday: DayOfWeek): ProgramDay? = program.days.firstOrNull { it.preferredWeekday == weekday }

    /**
     * Whether [today] is meant for training. Weekday mode: today's weekday has a training day.
     * Sequence mode: yes, unless the last session was 1..n days ago and the n days after it in
     * the sequence are rest days (a missed session is never skipped, so later days count).
     */
    fun isTrainingDay(program: Program, last: CompletedDay?, today: LocalDate): Boolean {
        if (program.trainingDays.isEmpty()) return false
        return when (program.scheduleMode) {
            ScheduleMode.WEEKDAYS -> program.trainingDays.any { it.preferredWeekday == today.dayOfWeek }
            ScheduleMode.SEQUENCE -> {
                val index = program.days.indexOfFirst { it.id == last?.dayId }
                if (last == null || index == -1) return true
                val since = java.time.temporal.ChronoUnit.DAYS.between(last.date, today)
                if (since <= 0) return true
                val restAfter = (1..program.days.size).takeWhile { program.days[(index + it) % program.days.size].isRest }.size
                since > restAfter
            }
        }
    }
}

/** Planned hard sets per muscle: a primary muscle counts 1 per set, a secondary one 0.5. */
object PlannedVolume {
    fun perCycle(program: Program, kind: VariantKind = VariantKind.FULL): Map<Muscle, Double> {
        val totals = mutableMapOf<Muscle, Double>()
        program.trainingDays.forEach { day ->
            day.variant(kind)?.exercises.orEmpty().forEach { p ->
                p.exercise.primary.forEach { totals[it] = (totals[it] ?: 0.0) + p.sets }
                p.exercise.secondary.forEach { totals[it] = (totals[it] ?: 0.0) + p.sets * 0.5 }
            }
        }
        return totals.toList().sortedByDescending { it.second }.toMap()
    }

    /** Primary and secondary muscles of a session, for the muscle map. */
    fun muscles(exercises: List<PlannedExercise>): Pair<Set<Muscle>, Set<Muscle>> {
        val primary = exercises.flatMap { it.exercise.primary }.toSet()
        val secondary = exercises.flatMap { it.exercise.secondary }.toSet() - primary
        return primary to secondary
    }
}

/** Rough session length: about 40 s per set plus the planned rest, and a minute per exercise to set up. */
object SessionEstimate {
    fun minutes(exercises: List<PlannedExercise>): Int {
        if (exercises.isEmpty()) return 0
        val seconds = exercises.sumOf { p ->
            val work = when (p.exercise.logType) {
                LogType.TIME -> p.durationSec ?: 30
                LogType.DISTANCE -> ((p.distanceM ?: 1000) / 1000.0 * 600).roundToInt()
                else -> 40
            }
            p.sets * work + (p.sets - 1).coerceAtLeast(0) * (p.restSec ?: 60) + 60
        }
        return ((seconds + 59) / 60)
    }
}
