package app.fitos.core.domain.habits

import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.nutrition.on
import app.fitos.core.domain.training.SessionSummary
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Gathers, per day, everything the system habits and reminders read from the other modules' logs. */
class DayFactsSource(
    private val water: WaterRepository,
    private val sleep: SleepRepository,
    private val activity: ActivityRepository,
    private val workouts: WorkoutRepository,
    private val weights: WeightRepository,
    private val foodLog: FoodLogRepository,
    private val targets: NutritionTargetRepository,
) {
    fun facts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayFacts>> {
        val body = combine(water.totals(from, to), sleep.entries(from, to), activity.entries(from, to)) { w, s, a -> Triple(w, s, a) }
        val other = combine(workouts.history, weights.all, foodLog.totals(from, to), targets.history) { h, wt, f, t -> Other(h, wt.map { it.day }.toSet(), f, t) }
        return combine(body, other) { (waterByDay, nights, activities), o ->
            generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.associateWith { day ->
                val target = o.targets.on(day)
                val food = o.food[day]?.takeIf { it.count > 0 }
                val moves = activities.filter { it.day == day }
                val sessions = o.history.filter { it.day == day }
                DayFacts(
                    waterMl = waterByDay[day],
                    waterTargetMl = target?.waterMl,
                    sleepMinutes = nights.filter { it.day == day }.takeIf { it.isNotEmpty() }?.sumOf { it.durationMinutes },
                    sleepTargetMinutes = target?.sleepMinutes,
                    trained = sessions.isNotEmpty(),
                    homeWorkout = sessions.any { it.variantKind == VariantKind.HOME || it.variantKind == VariantKind.MINIMAL } ||
                        moves.any { it.kind == ActivityKind.HOME_WORKOUT },
                    weighed = day in o.weighedDays,
                    kcal = food?.kcal,
                    proteinG = food?.proteinG,
                    kcalTarget = target?.kcal,
                    proteinTargetG = target?.proteinG,
                    activityMinutes = moves.mapNotNull { it.durationMinutes }.takeIf { it.isNotEmpty() }?.sum(),
                    steps = moves.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sum(),
                )
            }
        }
    }

    suspend fun factsOn(day: LocalDate): DayFacts = facts(day, day).first().getValue(day)

    private data class Other(
        val history: List<SessionSummary>,
        val weighedDays: Set<LocalDate>,
        val food: Map<LocalDate, NutrientTotals>,
        val targets: List<NutritionTarget>,
    )
}

/** Each habit's results over a range, from its answers and the day facts. */
object HabitBoard {
    fun results(habit: Habit, logs: List<HabitLog>, facts: Map<LocalDate, DayFacts>, from: LocalDate, to: LocalDate): Map<LocalDate, DayResult> {
        val byDay = logs.filter { it.habitId == habit.id }.associateBy { it.day }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
            .associateWith { day -> HabitRules.day(habit, byDay[day], facts[day]) }
    }
}
