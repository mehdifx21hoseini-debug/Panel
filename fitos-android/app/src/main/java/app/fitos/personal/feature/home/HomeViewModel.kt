package app.fitos.personal.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.habits.Adherence
import app.fitos.core.domain.habits.DayFactsSource
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.ReminderRules
import app.fitos.core.domain.habits.WaterRepository
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.EasyStart
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.ReturnEvent
import app.fitos.core.domain.training.ReturnPlan
import app.fitos.core.domain.training.ReturnPlanChecker
import app.fitos.core.domain.training.ReturnPlanRepository
import app.fitos.core.domain.training.Schedule
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import app.fitos.personal.feature.body.WeightInput
import app.fitos.personal.feature.habits.HabitRow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val name: String? = null,
    val target: NutritionTarget? = null,
    val latestWeight: WeightEntry? = null,
    val goal: WeightGoal? = null,
    /** Null while loading. */
    val training: HomeTraining? = null,
    /** What was logged today; null when nothing was. */
    val eaten: NutrientTotals? = null,
)

/** The active program's next day (both null without an active program), an open session, and whether today is done. */
data class HomeTraining(
    val programId: String?,
    val next: ProgramDay?,
    val open: WorkoutSession? = null,
    val doneToday: Boolean = false,
    /** The easier start chosen after a break, while it lasts. */
    val easy: EasyStart? = null,
)

/** Stage 6 on Home: water, sleep and today's habits, the question after a break, and an old backup. */
data class HomeDay(
    val loaded: Boolean = false,
    val waterMl: Int? = null,
    val sleepMinutes: Int? = null,
    /** Today's habits (as listed on the Habits tab). */
    val habits: List<HabitRow> = emptyList(),
    /** An open "how do you want to get back" question. */
    val returnEvent: ReturnEvent? = null,
    /** Days since the last backup when one is due and no automatic backup runs; -1 = never backed up. */
    val backupDueDays: Int? = null,
) {
    /** Habits that count today (skipped ones do not). */
    val habitsCounted: List<HabitRow> get() = habits.filter { it.result.outcome != Outcome.SKIPPED }
    val habitsDone: Int get() = habitsCounted.count { it.result.outcome == Outcome.DONE }
}

class HomeViewModel(
    profiles: ProfileRepository,
    targets: NutritionTargetRepository,
    private val weights: WeightRepository,
    goals: WeightGoalRepository,
    programs: ProgramRepository,
    workouts: WorkoutRepository,
    foodLog: FoodLogRepository,
    private val habits: HabitRepository,
    private val water: WaterRepository,
    facts: DayFactsSource,
    private val returnPlan: ReturnPlanRepository,
    returnChecker: ReturnPlanChecker,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today = MutableStateFlow<LocalDate?>(null)

    /** The screen passes the current day, so the target follows midnight without restarting. */
    fun setToday(day: LocalDate) {
        today.value = day
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        profiles.profile,
        today.filterNotNull().flatMapLatest { d -> combine(targets.effectiveOn(d), foodLog.totals(d, d)) { t, totals -> t to totals[d] } },
        weights.latest,
        goals.activeGoal,
        combine(
            programs.active.flatMapLatest { p -> if (p == null) flowOf(null) else workouts.lastCompleted(p.id).map { p to it } },
            today.filterNotNull(),
            workouts.active,
            workouts.history,
            returnPlan.latest,
        ) { pair, day, open, history, latest ->
            val program = pair?.first
            val starts = history.map { it.startedAt } + listOfNotNull(open?.startedAt)
            HomeTraining(
                program?.id, program?.let { Schedule.nextAfter(it, pair.second, day) }, open, history.any { it.day == day },
                easy = if (open == null) ReturnPlan.next(latest, clock.instant(), starts) else null,
            )
        },
    ) { profile, targetAndEaten, weight, goal, training ->
        HomeState(
            name = profile?.displayName, target = targetAndEaten.first, latestWeight = weight, goal = goal, training = training,
            eaten = targetAndEaten.second?.takeIf { it.count > 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val day: StateFlow<HomeDay> = today.filterNotNull().flatMapLatest { d ->
        val weekStart = JalaliCalendar.startOfWeek(d)
        val board = combine(habits.habits, habits.logs(weekStart, d), facts.facts(weekStart, d)) { list, logs, f ->
            val rows = list.filter { !it.archived }.mapNotNull { h ->
                val results = HabitBoard.results(h, logs, f, weekStart, d)
                val result = results.getValue(d)
                val week = if (h.frequency == HabitFrequency.WEEKLY) HabitRules.week(h, weekStart, results, d) else null
                if (HabitRules.listed(h, d, result, week)) HabitRow(h, result, week, Adherence(null, 0, 0)) else null
            }
            Triple(rows, f[d], d)
        }
        val backup = combine(settings.lastBackupAt, settings.autoBackup, weights.all) { last, auto, all ->
            val now = clock.instant()
            val firstDay = all.firstOrNull()?.day
            when {
                auto.enabled && auto.lastError == null -> null
                last != null -> if (ReminderRules.backupDue(last, now)) Duration.between(last, now).toDays().toInt() else null
                // Never backed up: ask once there is a week of data worth keeping.
                firstDay != null && firstDay <= d.minusDays(ReminderRules.BACKUP_DUE_DAYS) -> -1
                else -> null
            }
        }
        combine(board, returnPlan.latest, backup) { (rows, f, _), latest, due ->
            HomeDay(true, f?.waterMl, f?.sleepMinutes, rows, ReturnPlan.pending(latest), due)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeDay())

    init {
        // Notice a break as soon as Home opens; the daily background check does the same at night.
        viewModelScope.launch { returnChecker.check() }
    }

    fun toggleHabit(row: HabitRow) {
        val d = today.value ?: return
        viewModelScope.launch {
            if (row.result.outcome == Outcome.DONE && !row.result.auto) habits.clearLog(row.habit.id, d) else habits.setLog(row.habit.id, d, HabitStatus.DONE)
        }
    }

    fun addWater(ml: Int) {
        val d = today.value ?: return
        viewModelScope.launch { water.add(d, ml) }
    }

    fun chooseReturn(event: ReturnEvent, choice: ReturnChoice) {
        viewModelScope.launch { returnPlan.choose(event.id, choice) }
    }

    fun addWeight(input: WeightInput) {
        viewModelScope.launch { weights.add(input.weightKg, input.day, input.time, input.condition, input.note) }
    }
}
