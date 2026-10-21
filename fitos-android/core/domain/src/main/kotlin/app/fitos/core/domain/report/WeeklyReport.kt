package app.fitos.core.domain.report

import app.fitos.core.domain.body.DailyWeight
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.on
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.Exercise
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.SessionSummary
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.Strength
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Everything one week's report reads, gathered by [WeeklyReportSource]. */
data class WeekData(
    /** Saturday of the week. */
    val weekStart: LocalDate,
    val today: LocalDate,
    /** Daily weight averages up to the week's end (the previous week and the calorie check need older ones). */
    val weights: List<DailyWeight>,
    val waist: WaistSeries?,
    /** Finished sessions of the week. */
    val sessions: List<SessionSummary>,
    /** Sessions a week in the active program; null without one. */
    val plannedSessions: Int?,
    val exercises: List<ExerciseWeek>,
    /** Daily food totals, from 21 days before the week's end at the latest. */
    val food: Map<LocalDate, NutrientTotals>,
    /** Target history, newest first. */
    val targets: List<NutritionTarget>,
    val habits: HabitsWeek,
    /** Sleep minutes by wake day. */
    val sleepMinutes: Map<LocalDate, Int>,
    val profile: Profile?,
    /** Run the calorie check (only for the latest week, which is the one a change could follow). */
    val checkTargets: Boolean,
    /** Hard sets per muscle done this week ([WeeklyVolume.done]), and planned for a week by the active program. */
    val muscleSets: Map<Muscle, Double> = emptyMap(),
    val plannedMuscleSets: Map<Muscle, Double>? = null,
)

/** The waist place the report follows ("دور شکم", or "دور کمر" when only that is measured). */
data class WaistSeries(val siteName: String, val values: List<Pair<LocalDate, Double>>)

/** Adherence of the active habits over the week, and the one furthest behind. */
data class HabitsWeek(val score: Double?, val habits: Int, val weakest: HabitScore?)

data class HabitScore(val name: String, val score: Double)

enum class ProgressMetric { E1RM, WEIGHT, REPS, SECONDS, METERS }

/** One exercise done this week: its best this week against its best before the week (null the first time). */
data class ExerciseWeek(
    val exerciseId: String,
    val name: String,
    val metric: ProgressMetric,
    val before: Double?,
    val best: Double,
    /** The set behind [best] for weighted moves. */
    val bestWeightKg: Double?,
    val bestReps: Int?,
) {
    val improved: Boolean get() = before != null && best > before + 1e-9
    val gain: Double? get() = before?.takeIf { it > 0 }?.let { best / it - 1 }
}

/**
 * Hard sets per muscle: each working set counts 1 for the exercise's primary muscles and 0.5 for
 * its secondary ones (a muscle that is both counts once, as primary). Warm-ups do not count.
 */
object WeeklyVolume {
    fun done(sessions: List<WorkoutSession>): Map<Muscle, Double> {
        val totals = mutableMapOf<Muscle, Double>()
        sessions.flatMap { it.exercises }.forEach { e ->
            val n = e.working.size.toDouble()
            if (n == 0.0) return@forEach
            e.exercise.primary.forEach { totals[it] = (totals[it] ?: 0.0) + n }
            (e.exercise.secondary - e.exercise.primary).forEach { totals[it] = (totals[it] ?: 0.0) + n * 0.5 }
        }
        return totals
    }

    /** The full version of every training day, scaled to seven days for a sequence that is not a week long. */
    fun plannedPerWeek(program: Program): Map<Muscle, Double> {
        val totals = mutableMapOf<Muscle, Double>()
        program.trainingDays.forEach { day ->
            day.variant(VariantKind.FULL)?.exercises.orEmpty().forEach { p ->
                p.exercise.primary.forEach { totals[it] = (totals[it] ?: 0.0) + p.sets }
                (p.exercise.secondary - p.exercise.primary).forEach { totals[it] = (totals[it] ?: 0.0) + p.sets * 0.5 }
            }
        }
        val factor = if (program.scheduleMode == ScheduleMode.SEQUENCE && program.days.isNotEmpty()) 7.0 / program.days.size else 1.0
        return totals.mapValues { it.value * factor }
    }
}

object ExerciseProgress {
    /** How [exercise] went in the week from [from] to [to], from its full [history]; null when not done. */
    fun week(exercise: Exercise, history: List<ExercisePerformance>, from: LocalDate, to: LocalDate): ExerciseWeek? {
        val during = history.filter { it.day in from..to }.flatMap { it.working }
        if (during.isEmpty()) return null
        val before = history.filter { it.day < from }.flatMap { it.working }
        fun e1rm(sets: List<SetLog>) = sets.mapNotNull { s -> Strength.e1rm(s.weightKg, s.reps)?.let { it to s } }.maxByOrNull { it.first }
        return when (exercise.logType) {
            LogType.WEIGHT_REPS -> {
                val best = e1rm(during)
                if (best != null) {
                    ExerciseWeek(exercise.id, exercise.nameFa, ProgressMetric.E1RM, e1rm(before)?.first, best.first, best.second.weightKg, best.second.reps)
                } else {
                    val top = during.filter { it.weightKg != null }.maxByOrNull { it.weightKg!! } ?: return null
                    ExerciseWeek(exercise.id, exercise.nameFa, ProgressMetric.WEIGHT, before.mapNotNull { it.weightKg }.maxOrNull(), top.weightKg!!, top.weightKg, top.reps)
                }
            }
            LogType.REPS -> {
                val best = during.mapNotNull { it.reps }.maxOrNull() ?: return null
                ExerciseWeek(exercise.id, exercise.nameFa, ProgressMetric.REPS, before.mapNotNull { it.reps }.maxOrNull()?.toDouble(), best.toDouble(), null, best)
            }
            LogType.TIME -> {
                val best = during.mapNotNull { it.durationSec }.maxOrNull() ?: return null
                ExerciseWeek(exercise.id, exercise.nameFa, ProgressMetric.SECONDS, before.mapNotNull { it.durationSec }.maxOrNull()?.toDouble(), best.toDouble(), null, null)
            }
            LogType.DISTANCE -> {
                val best = during.mapNotNull { it.distanceM }.maxOrNull() ?: return null
                ExerciseWeek(exercise.id, exercise.nameFa, ProgressMetric.METERS, before.mapNotNull { it.distanceM }.maxOrNull(), best, null, null)
            }
        }
    }
}

data class WeightPart(val days: Int, val averageKg: Double?, val previousDays: Int, val previousAverageKg: Double?) {
    /** This week's average minus last week's, when both weeks have enough weigh-ins. */
    val change: Double?
        get() = if (days >= WeeklyReportRules.MIN_WEIGH_DAYS_FOR_CHANGE && previousDays >= WeeklyReportRules.MIN_WEIGH_DAYS_FOR_CHANGE) {
            averageKg!! - previousAverageKg!!
        } else {
            null
        }
}

data class WaistPart(val siteName: String, val latest: Pair<LocalDate, Double>?, val before: Pair<LocalDate, Double>?) {
    val change: Double? get() = if (latest != null && before != null) latest.second - before.second else null
}

data class TrainingPart(
    val sessions: Int,
    val planned: Int?,
    /** Of [sessions], those done in the short home or minimal version. */
    val shortVersions: Int,
    val minutes: Int,
    val tonnageKg: Double,
    val exercises: List<ExerciseWeek>,
    val muscleSets: Map<Muscle, Double> = emptyMap(),
    val plannedMuscleSets: Map<Muscle, Double>? = null,
) {
    /** Muscles done or planned this week, the planned ones first: (muscle, done, planned). */
    val volume: List<Triple<Muscle, Double, Double?>>
        get() = (plannedMuscleSets.orEmpty().keys + muscleSets.keys).distinct()
            .map { Triple(it, muscleSets[it] ?: 0.0, plannedMuscleSets?.get(it)) }
            .sortedWith(compareByDescending<Triple<Muscle, Double, Double?>> { it.third ?: 0.0 }.thenByDescending { it.second })

    val compared: List<ExerciseWeek> get() = exercises.filter { it.before != null }
    val improved: List<ExerciseWeek> get() = exercises.filter { it.improved }.sortedByDescending { it.gain ?: 0.0 }
}

data class NutritionPart(
    /** Days with food logged, out of [days] (the days of the week so far). */
    val loggedDays: Int,
    val days: Int,
    val meanKcal: Double?,
    val meanProteinG: Double?,
    /** The targets of the logged days, averaged. */
    val meanKcalTarget: Double?,
    val meanProteinTargetG: Double?,
    /** Logged days within ±10% of the calorie target, and with at least 90% of the protein target. */
    val daysInBand: Int,
    val daysProteinMet: Int,
)

data class SleepPart(val nights: Int, val meanMinutes: Double?, val targetMinutes: Int?)

/** The week's single most notable step forward. */
sealed interface Highlight {
    data class Strength(val exercise: ExerciseWeek) : Highlight
    data class WeightDown(val kg: Double) : Highlight
    data class WeightUp(val kg: Double) : Highlight
    data class WaistDown(val siteName: String, val cm: Double) : Highlight
    data class AllSessions(val done: Int, val planned: Int) : Highlight
    data class Habits(val score: Double) : Highlight
    data class Protein(val days: Int) : Highlight
    data class FoodEveryDay(val days: Int) : Highlight
}

/** One practical step for next week; each carries the numbers it comes from. */
sealed interface Advice {
    /** The calorie check found something to do ([AdaptiveResult.Change] or [AdaptiveResult.EatUpToTarget]). */
    data class Calories(val result: AdaptiveResult) : Advice
    data class LogFood(val loggedDays: Int, val days: Int) : Advice
    data class WeighMore(val weighDays: Int) : Advice
    data class Training(val done: Int, val planned: Int) : Advice
    data class Protein(val meanG: Double, val targetG: Double) : Advice
    data class Sleep(val meanMinutes: Double, val targetMinutes: Int) : Advice
    data class Habit(val name: String, val score: Double) : Advice
    data class CaloriesOver(val meanKcal: Double, val targetKcal: Double) : Advice
}

data class WeeklyReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    /** The week has not reached its Friday yet: numbers so far, no highlight or advice. */
    val inProgress: Boolean,
    val weight: WeightPart,
    val waist: WaistPart?,
    val training: TrainingPart,
    val nutrition: NutritionPart,
    val habits: HabitsWeek,
    val sleep: SleepPart,
    val highlight: Highlight?,
    val advice: List<Advice>,
    /** Only for the latest week. */
    val adaptive: AdaptiveResult?,
) {
    val hasData: Boolean
        get() = weight.days > 0 || waist?.latest != null || training.sessions > 0 || nutrition.loggedDays > 0 || sleep.nights > 0 ||
            habits.score != null
}

/**
 * The weekly report (docs/formulas.md): the week from Saturday to Friday, built from real logs
 * only. Every part says when it has too little data, and the advice points at the numbers behind it.
 */
object WeeklyReportRules {
    const val MIN_WEIGH_DAYS_FOR_CHANGE = 2
    const val LOG_FOOD_MIN_DAYS = 5
    const val WEIGH_MIN_DAYS = 3
    const val PROTEIN_SHARE = 0.90
    const val KCAL_BAND = 0.10
    const val SLEEP_SHORT_MINUTES = 30
    const val HABIT_LOW = 0.6
    const val MIN_DAYS_FOR_AVERAGE_ADVICE = 3
    const val HIGHLIGHT_WEIGHT_KG = 0.2
    const val HIGHLIGHT_WAIST_CM = 0.5
    const val HIGHLIGHT_HABITS = 0.8
    const val HIGHLIGHT_PROTEIN_DAYS = 5
    const val MAX_ADVICE = 2

    /** The week a report is due for: this one on Friday, the one that just ended on other days. */
    fun latestWeek(today: LocalDate): LocalDate {
        val start = JalaliCalendar.startOfWeek(today)
        return if (today.dayOfWeek == DayOfWeek.FRIDAY) start else start.minusWeeks(1)
    }

    fun build(d: WeekData): WeeklyReport {
        val start = d.weekStart
        val end = start.plusDays(6)
        val inProgress = d.today < end
        val last = minOf(end, d.today)
        val days = (ChronoUnit.DAYS.between(start, last) + 1).toInt().coerceIn(0, 7)

        val thisWeek = d.weights.filter { it.day in start..end }
        val previous = d.weights.filter { it.day in start.minusDays(7)..start.minusDays(1) }
        val weight = WeightPart(
            thisWeek.size, thisWeek.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average(),
            previous.size, previous.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average(),
        )

        val waist = d.waist?.let { s ->
            WaistPart(s.siteName, s.values.filter { it.first in start..end }.maxByOrNull { it.first }, s.values.filter { it.first < start }.maxByOrNull { it.first })
        }

        val training = TrainingPart(
            sessions = d.sessions.size,
            planned = d.plannedSessions,
            shortVersions = d.sessions.count { it.variantKind == VariantKind.HOME || it.variantKind == VariantKind.MINIMAL },
            minutes = d.sessions.sumOf { it.activeMinutes },
            tonnageKg = d.sessions.sumOf { it.tonnageKg },
            exercises = d.exercises,
            muscleSets = d.muscleSets,
            plannedMuscleSets = d.plannedMuscleSets,
        )

        val logged = (0 until days).map { start.plusDays(it.toLong()) }.mapNotNull { day -> d.food[day]?.takeIf { it.count > 0 }?.let { day to it } }
        val withTargets = logged.mapNotNull { (day, t) -> d.targets.on(day)?.let { Triple(day, t, it) } }
        val nutrition = NutritionPart(
            loggedDays = logged.size,
            days = days,
            meanKcal = logged.takeIf { it.isNotEmpty() }?.map { it.second.kcal }?.average(),
            meanProteinG = logged.takeIf { it.isNotEmpty() }?.map { it.second.proteinG }?.average(),
            meanKcalTarget = withTargets.takeIf { it.isNotEmpty() }?.map { it.third.kcal.toDouble() }?.average(),
            meanProteinTargetG = withTargets.takeIf { it.isNotEmpty() }?.map { it.third.proteinG.toDouble() }?.average(),
            daysInBand = withTargets.count { (_, t, target) -> abs(t.kcal - target.kcal) <= target.kcal * KCAL_BAND },
            daysProteinMet = withTargets.count { (_, t, target) -> t.proteinG >= target.proteinG * PROTEIN_SHARE },
        )

        val nights = d.sleepMinutes.filterKeys { it in start..end }.values
        val sleep = SleepPart(nights.size, nights.takeIf { it.isNotEmpty() }?.average(), d.targets.on(last)?.sleepMinutes)

        val adaptive = if (d.checkTargets) AdaptiveTargets.check(last, d.weights, d.food, d.targets, d.profile, d.today) else null
        val highlight = if (inProgress) null else highlight(d.profile?.primaryGoal, weight, waist, training, nutrition, d.habits)
        val advice = if (inProgress) emptyList() else advice(weight, training, nutrition, d.habits, sleep, adaptive)
        return WeeklyReport(start, end, inProgress, weight, waist, training, nutrition, d.habits, sleep, highlight, advice, adaptive)
    }

    /** The first match, in order: a strength record, weight or waist toward the goal, all sessions, habits, protein, food logged daily. */
    fun highlight(goal: PrimaryGoal?, weight: WeightPart, waist: WaistPart?, training: TrainingPart, nutrition: NutritionPart, habits: HabitsWeek): Highlight? {
        training.improved.firstOrNull()?.let { return Highlight.Strength(it) }
        weight.change?.let { c ->
            if (goal == PrimaryGoal.FAT_LOSS && c <= -HIGHLIGHT_WEIGHT_KG) return Highlight.WeightDown(-c)
            if (goal == PrimaryGoal.MUSCLE_GAIN && c >= HIGHLIGHT_WEIGHT_KG / 2) return Highlight.WeightUp(c)
        }
        waist?.change?.let { if (it <= -HIGHLIGHT_WAIST_CM) return Highlight.WaistDown(waist.siteName, -it) }
        training.planned?.let { if (it > 0 && training.sessions >= it) return Highlight.AllSessions(training.sessions, it) }
        habits.score?.let { if (it >= HIGHLIGHT_HABITS) return Highlight.Habits(it) }
        if (nutrition.daysProteinMet >= HIGHLIGHT_PROTEIN_DAYS) return Highlight.Protein(nutrition.daysProteinMet)
        if (nutrition.days > 0 && nutrition.loggedDays == nutrition.days) return Highlight.FoodEveryDay(nutrition.loggedDays)
        return null
    }

    /** At most [MAX_ADVICE], in order of what helps most; each only when its numbers show the need. */
    fun advice(weight: WeightPart, training: TrainingPart, nutrition: NutritionPart, habits: HabitsWeek, sleep: SleepPart, adaptive: AdaptiveResult?): List<Advice> = buildList {
        if (adaptive is AdaptiveResult.Change || adaptive is AdaptiveResult.EatUpToTarget) add(Advice.Calories(adaptive))
        if (nutrition.loggedDays < LOG_FOOD_MIN_DAYS) add(Advice.LogFood(nutrition.loggedDays, nutrition.days))
        if (weight.days < WEIGH_MIN_DAYS) add(Advice.WeighMore(weight.days))
        training.planned?.let { if (it > 0 && training.sessions < it) add(Advice.Training(training.sessions, it)) }
        if (nutrition.loggedDays >= MIN_DAYS_FOR_AVERAGE_ADVICE) {
            val p = nutrition.meanProteinG
            val pt = nutrition.meanProteinTargetG
            if (p != null && pt != null && p < pt * PROTEIN_SHARE) add(Advice.Protein(p, pt))
        }
        if (sleep.nights >= MIN_DAYS_FOR_AVERAGE_ADVICE && sleep.meanMinutes != null && sleep.targetMinutes != null &&
            sleep.meanMinutes < sleep.targetMinutes - SLEEP_SHORT_MINUTES
        ) {
            add(Advice.Sleep(sleep.meanMinutes, sleep.targetMinutes))
        }
        habits.weakest?.let { w -> if ((habits.score ?: 1.0) < HABIT_LOW || w.score < HABIT_LOW / 2) add(Advice.Habit(w.name, w.score)) }
        if (none { it is Advice.Calories } && nutrition.loggedDays >= MIN_DAYS_FOR_AVERAGE_ADVICE) {
            val k = nutrition.meanKcal
            val kt = nutrition.meanKcalTarget
            if (k != null && kt != null && k > kt * (1 + KCAL_BAND)) add(Advice.CaloriesOver(k, kt))
        }
    }.take(MAX_ADVICE)
}
