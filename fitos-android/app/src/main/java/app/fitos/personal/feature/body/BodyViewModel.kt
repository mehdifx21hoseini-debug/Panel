package app.fitos.personal.feature.body

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.body.BodyMeasurement
import app.fitos.core.domain.body.MeasurementRepository
import app.fitos.core.domain.body.MeasurementSite
import app.fitos.core.domain.body.TrendPoint
import app.fitos.core.domain.body.WeeklyAverage
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.body.WeightTrend
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.personal.R
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChartRange(val days: Long?, @param:StringRes val label: Int) {
    MONTH(30, R.string.range_month),
    QUARTER(91, R.string.range_quarter),
    YEAR(365, R.string.range_year),
    ALL(null, R.string.range_all),
}

data class BodyState(
    val loaded: Boolean = false,
    /** Oldest first. */
    val entries: List<WeightEntry> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val weekly: List<WeeklyAverage> = emptyList(),
    /** kg per week, null when there is not enough data. */
    val rate: Double? = null,
    val goal: WeightGoal? = null,
    val sites: List<MeasurementSite> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    val range: ChartRange = ChartRange.MONTH,
)

class BodyViewModel(
    private val weights: WeightRepository,
    private val goals: WeightGoalRepository,
    private val measurements: MeasurementRepository,
    private val clock: Clock,
) : ViewModel() {
    private val range = MutableStateFlow(ChartRange.MONTH)

    fun today(): LocalDate = LocalDate.now(clock)
    fun now(): LocalTime = LocalTime.now(clock)

    val state: StateFlow<BodyState> = combine(weights.all, goals.activeGoal, measurements.sites, measurements.all, range) { entries, goal, sites, values, r ->
        val daily = WeightTrend.daily(entries)
        BodyState(
            loaded = true,
            entries = entries,
            trend = WeightTrend.ema(daily),
            weekly = WeightTrend.weeklyAverages(daily),
            rate = WeightTrend.weeklyRate(daily, today()),
            goal = goal,
            sites = sites,
            measurements = values,
            range = r,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyState())

    fun setRange(value: ChartRange) {
        range.value = value
    }

    fun saveWeight(editing: WeightEntry?, input: WeightInput) {
        viewModelScope.launch {
            if (editing == null) {
                weights.add(input.weightKg, input.day, input.time, input.condition, input.note)
            } else {
                weights.update(editing.id, input.weightKg, input.day, input.time, input.condition, input.note)
            }
        }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch { weights.delete(id) }
    }

    fun restoreWeight(id: String) {
        viewModelScope.launch { weights.restore(id) }
    }

    /** Moves the review date four weeks later, keeping the goal's start. */
    fun extendGoal() {
        val goal = state.value.goal ?: return
        viewModelScope.launch {
            val from = maxOf(goal.reviewDate ?: today(), today())
            goals.setActiveGoal(goal.targetWeightKg, from.plusWeeks(4), goal.startWeightKg, goal.startDate)
        }
    }
}
