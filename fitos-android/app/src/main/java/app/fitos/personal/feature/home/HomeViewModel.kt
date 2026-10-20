package app.fitos.personal.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.Schedule
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.fitos.personal.feature.body.WeightInput

data class HomeState(
    val name: String? = null,
    val target: NutritionTarget? = null,
    val latestWeight: WeightEntry? = null,
    val goal: WeightGoal? = null,
    /** Null while loading. */
    val training: HomeTraining? = null,
)

/** The active program's next day (both null without an active program), an open session, and whether today is done. */
data class HomeTraining(val programId: String?, val next: ProgramDay?, val open: WorkoutSession? = null, val doneToday: Boolean = false)

class HomeViewModel(
    profiles: ProfileRepository,
    targets: NutritionTargetRepository,
    private val weights: WeightRepository,
    goals: WeightGoalRepository,
    programs: ProgramRepository,
    workouts: WorkoutRepository,
) : ViewModel() {
    private val today = MutableStateFlow<LocalDate?>(null)

    /** The screen passes the current day, so the target follows midnight without restarting. */
    fun setToday(day: LocalDate) {
        today.value = day
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        profiles.profile,
        today.filterNotNull().flatMapLatest { targets.effectiveOn(it) },
        weights.latest,
        goals.activeGoal,
        combine(
            programs.active.flatMapLatest { p -> if (p == null) flowOf(null) else workouts.lastCompleted(p.id).map { p to it } },
            today.filterNotNull(),
            workouts.active,
            workouts.history,
        ) { pair, day, open, history ->
            val program = pair?.first
            HomeTraining(program?.id, program?.let { Schedule.nextAfter(it, pair.second, day) }, open, history.any { it.day == day })
        },
    ) { profile, target, weight, goal, training ->
        HomeState(name = profile?.displayName, target = target, latestWeight = weight, goal = goal, training = training)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun addWeight(input: WeightInput) {
        viewModelScope.launch { weights.add(input.weightKg, input.day, input.time, input.condition, input.note) }
    }
}
