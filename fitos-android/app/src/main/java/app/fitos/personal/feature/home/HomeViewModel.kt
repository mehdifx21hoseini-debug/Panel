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
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val name: String? = null,
    val target: NutritionTarget? = null,
    val latestWeight: WeightEntry? = null,
    val goal: WeightGoal? = null,
)

class HomeViewModel(
    profiles: ProfileRepository,
    targets: NutritionTargetRepository,
    weights: WeightRepository,
    goals: WeightGoalRepository,
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
    ) { profile, target, weight, goal ->
        HomeState(name = profile?.displayName, target = target, latestWeight = weight, goal = goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
