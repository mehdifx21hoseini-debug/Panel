package app.fitos.core.domain.report

import app.fitos.core.domain.body.BuiltinSite
import app.fitos.core.domain.body.MeasurementRepository
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.body.WeightTrend
import app.fitos.core.domain.habits.DayFactsSource
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.SleepRepository
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.WorkoutRepository
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Reads one week's logs from every module and builds its [WeeklyReport]. Nothing is stored: a report always shows the data as it is now. */
class WeeklyReportSource(
    private val weights: WeightRepository,
    private val measurements: MeasurementRepository,
    private val workouts: WorkoutRepository,
    private val programs: ProgramRepository,
    private val foodLog: FoodLogRepository,
    private val targets: NutritionTargetRepository,
    private val habits: HabitRepository,
    private val dayFacts: DayFactsSource,
    private val sleep: SleepRepository,
    private val profiles: ProfileRepository,
) {
    suspend fun report(weekStart: LocalDate, today: LocalDate): WeeklyReport = WeeklyReportRules.build(data(weekStart, today))

    suspend fun data(weekStart: LocalDate, today: LocalDate): WeekData {
        val end = weekStart.plusDays(6)
        val last = minOf(end, today)
        val windowStart = minOf(weekStart, last.minusDays(AdaptiveTargets.WINDOW_DAYS - 1L))

        val daily = WeightTrend.daily(weights.all.first()).filter { it.day <= end }

        val sites = measurements.sites.first()
        val all = measurements.all.first()
        val site = listOf(BuiltinSite.ABDOMEN, BuiltinSite.WAIST).firstNotNullOfOrNull { b ->
            sites.firstOrNull { it.builtinKey == b.key }?.takeIf { s -> all.any { it.siteId == s.id } }
        }
        val waist = site?.let { s -> WaistSeries(s.name, all.filter { it.siteId == s.id && it.day <= end }.map { it.day to it.valueCm }) }

        val sessions = workouts.history.first().filter { it.day in weekStart..end }
        val full = sessions.mapNotNull { s -> workouts.session(s.id).first() }
        val exercises = full.flatMap { it.exercises }
            .filter { it.working.isNotEmpty() }
            .map { it.exercise }
            .distinctBy { it.id }
            .mapNotNull { e -> ExerciseProgress.week(e, workouts.exerciseHistory(e.id).first(), weekStart, end) }

        val habitList = habits.habits.first().filter { !it.archived }
        val logs = habits.logs(weekStart, end).first()
        val facts = dayFacts.facts(weekStart, end).first()
        val scores = habitList.map { h -> h to HabitRules.adherence(h, weekStart, end, today, HabitBoard.results(h, logs, facts, weekStart, end)) }
        val known = scores.filter { it.second.score != null }
        val habitsWeek = HabitsWeek(
            score = HabitRules.overall(scores.map { it.second }).score,
            habits = known.size,
            weakest = known.minByOrNull { it.second.score!! }?.let { (h, a) -> HabitScore(h.name, a.score!!) },
        )

        val nights = sleep.entries(weekStart, end).first().groupBy { it.day }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

        val program = programs.active.first()
        return WeekData(
            weekStart = weekStart,
            today = today,
            weights = daily,
            waist = waist,
            sessions = sessions,
            plannedSessions = program?.let(::plannedPerWeek),
            exercises = exercises,
            muscleSets = WeeklyVolume.done(full),
            plannedMuscleSets = program?.let(WeeklyVolume::plannedPerWeek)?.takeIf { it.isNotEmpty() },
            food = foodLog.totals(windowStart, end).first(),
            targets = targets.history.first(),
            habits = habitsWeek,
            sleepMinutes = nights,
            profile = profiles.profile.first(),
            checkTargets = weekStart == WeeklyReportRules.latestWeek(today),
        )
    }

    /** The weeks that have any log, newest first, up to the latest one a report is due for. */
    suspend fun weeks(today: LocalDate): List<LocalDate> {
        val firstDay = listOfNotNull(
            weights.all.first().minOfOrNull { it.day },
            workouts.history.first().minOfOrNull { it.day },
            targets.history.first().minOfOrNull { it.effectiveFrom },
        ).minOrNull() ?: return emptyList()
        val first = JalaliCalendar.startOfWeek(firstDay)
        val current = JalaliCalendar.startOfWeek(today)
        return generateSequence(current) { it.minusWeeks(1) }.takeWhile { it >= first }.toList()
    }

    companion object {
        /** Sessions a week: the weekday plan's training days, or the sequence's share of seven days. */
        fun plannedPerWeek(program: Program): Int? {
            if (program.days.isEmpty() || program.trainingDays.isEmpty()) return null
            return when (program.scheduleMode) {
                ScheduleMode.WEEKDAYS -> program.trainingDays.mapNotNull { it.preferredWeekday }.distinct().size.takeIf { it > 0 }
                ScheduleMode.SEQUENCE -> (program.trainingDays.size * 7.0 / program.days.size).roundToInt().coerceIn(1, 7)
            }
        }
    }
}
