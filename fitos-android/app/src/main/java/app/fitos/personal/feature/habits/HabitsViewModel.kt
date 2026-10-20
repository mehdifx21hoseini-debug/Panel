package app.fitos.personal.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.habits.ActivityEntry
import app.fitos.core.domain.habits.ActivityInput
import app.fitos.core.domain.habits.ActivityRepository
import app.fitos.core.domain.habits.Adherence
import app.fitos.core.domain.habits.DayFacts
import app.fitos.core.domain.habits.DayFactsSource
import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SleepEntry
import app.fitos.core.domain.habits.SleepRepository
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.habits.TrendPoint
import app.fitos.core.domain.habits.WaterEntry
import app.fitos.core.domain.habits.WaterRepository
import app.fitos.core.domain.habits.WeekResult
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.time.JalaliCalendar
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One habit on the chosen day, with its last four weeks. */
data class HabitRow(val habit: Habit, val result: DayResult, val week: WeekResult?, val adherence: Adherence)

data class HabitsUi(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val day: LocalDate = LocalDate.MIN,
    /** Habits on the chosen day's list. */
    val rows: List<HabitRow> = emptyList(),
    val active: List<Habit> = emptyList(),
    val archived: List<Habit> = emptyList(),
    /** Last four weeks over all active habits. */
    val overall: Adherence = Adherence(null, 0, 0),
    /** Eight weeks, oldest first: the mean of the habits' weekly scores. */
    val trend: List<TrendPoint> = emptyList(),
    val water: List<WaterEntry> = emptyList(),
    val waterTargetMl: Int? = null,
    val sleep: SleepEntry? = null,
    val sleepTargetMinutes: Int? = null,
    val activities: List<ActivityEntry> = emptyList(),
    /** Ready-made habits not added yet. */
    val suggestions: List<SystemHabit> = emptyList(),
) {
    val waterMl: Int? get() = water.takeIf { it.isNotEmpty() }?.sumOf { it.amountMl }
}

/** What the screen shows for one day, plus the eight weeks behind it. */
private data class Window(
    val habits: List<Habit>,
    val results: Map<String, Map<LocalDate, DayResult>>,
    val factsDay: DayFacts?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(
    private val habits: HabitRepository,
    private val water: WaterRepository,
    private val sleep: SleepRepository,
    private val activity: ActivityRepository,
    private val facts: DayFactsSource,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val today = MutableStateFlow<LocalDate?>(null)
    private val chosen = MutableStateFlow<LocalDate?>(null)

    fun setToday(day: LocalDate) {
        if (today.value != day && (chosen.value == null || chosen.value == today.value)) chosen.value = day
        today.value = day
    }

    fun show(day: LocalDate) {
        chosen.value = day
    }

    private val window = combine(today.filterNotNull(), chosen.filterNotNull()) { t, d -> t to d }.flatMapLatest { (t, d) ->
        // Eight weeks back from this week's Saturday, and far enough back for an older chosen day.
        val from = minOf(JalaliCalendar.startOfWeek(t).minusWeeks(7), JalaliCalendar.startOfWeek(d))
        combine(habits.habits, habits.logs(from, t), facts.facts(from, t)) { list, logs, f ->
            Triple(t to d, from, Window(list, list.associate { it.id to HabitBoard.results(it, logs, f, from, t) }, f[d]))
        }
    }

    private val day = chosen.filterNotNull().flatMapLatest { d ->
        combine(water.entries(d), sleep.entries(d, d), activity.entries(d, d)) { w, s, a -> Triple(w, s.lastOrNull(), a) }
    }

    val ui: StateFlow<HabitsUi> = combine(window, day) { (dates, _, w), (drinks, night, moves) ->
        val (t, d) = dates
        val active = w.habits.filter { !it.archived }
        val adherence = active.associate { h -> h.id to HabitRules.adherence(h, t.minusDays(27), t, t, w.results.getValue(h.id)) }
        val rows = active.filter { HabitRules.isDue(it, d) }.map { h ->
            val results = w.results.getValue(h.id)
            val result = results[d] ?: HabitRules.day(h, null, w.factsDay)
            val week = if (h.frequency == HabitFrequency.WEEKLY) HabitRules.week(h, JalaliCalendar.startOfWeek(d), results, t) else null
            HabitRow(h, result, week, adherence.getValue(h.id))
        }
        val trends = active.map { HabitRules.trend(it, 8, t, w.results.getValue(it.id)) }
        val trend = (0 until 8).map { i ->
            val scores = trends.mapNotNull { it[i].score }
            TrendPoint(JalaliCalendar.startOfWeek(t).minusWeeks((7 - i).toLong()), scores.takeIf { it.isNotEmpty() }?.average(), inProgress = i == 7)
        }
        HabitsUi(
            loaded = true,
            today = t,
            day = d,
            rows = rows,
            active = active,
            archived = w.habits.filter { it.archived },
            overall = HabitRules.overall(adherence.values.toList()),
            trend = trend,
            water = drinks,
            waterTargetMl = w.factsDay?.waterTargetMl,
            sleep = night,
            sleepTargetMinutes = w.factsDay?.sleepTargetMinutes,
            activities = moves,
            suggestions = SystemHabit.entries.filter { s -> w.habits.none { it.system == s } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUi())

    private val dayValue get() = chosen.value ?: ui.value.day

    /** Boolean habits: one tap marks done, a second tap takes the answer back. */
    fun toggle(row: HabitRow) = viewModelScope.launch {
        if (row.result.outcome == Outcome.DONE && !row.result.auto) habits.clearLog(row.habit.id, dayValue)
        else habits.setLog(row.habit.id, dayValue, HabitStatus.DONE)
    }

    fun answer(habit: Habit, day: LocalDate, status: HabitStatus, value: Double? = null) = viewModelScope.launch {
        // A quantity reaching its target counts as done; below it, as part of the day.
        val s = if (value != null && habit.kind != HabitKind.BOOLEAN && status != HabitStatus.SKIPPED) {
            val target = habit.target
            if (target == null || value >= target) HabitStatus.DONE else HabitStatus.NOT_DONE
        } else {
            status
        }
        habits.setLog(habit.id, day, s, value)
    }

    fun clear(habit: Habit, day: LocalDate) = viewModelScope.launch { habits.clearLog(habit.id, day) }

    fun addSuggestion(system: SystemHabit, name: String) = viewModelScope.launch {
        habits.add(systemInput(system, name, profiles.profile.first()?.trainingDaysPerWeek))
    }

    fun addWater(ml: Int) = viewModelScope.launch { water.add(dayValue, ml) }
    fun deleteWater(entry: WaterEntry) = viewModelScope.launch { water.delete(entry.id) }
    fun restoreWater(entry: WaterEntry) = viewModelScope.launch { water.restore(entry.id) }

    fun saveSleep(bedtime: LocalTime, wake: LocalTime, quality: Int?, note: String?) = viewModelScope.launch {
        sleep.save(dayValue, bedtime, wake, quality, note)
    }

    fun deleteSleep(entry: SleepEntry) = viewModelScope.launch { sleep.delete(entry.id) }
    fun restoreSleep(entry: SleepEntry) = viewModelScope.launch { sleep.restore(entry.id) }

    fun saveActivity(id: String?, input: ActivityInput) = viewModelScope.launch {
        if (id == null) activity.add(dayValue, input) else activity.update(id, input)
    }

    fun deleteActivity(entry: ActivityEntry) = viewModelScope.launch { activity.delete(entry.id) }
    fun restoreActivity(entry: ActivityEntry) = viewModelScope.launch { activity.restore(entry.id) }

    fun restoreHabit(id: String) = viewModelScope.launch { habits.restore(id) }
}
