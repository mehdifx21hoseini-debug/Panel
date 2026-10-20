package app.fitos.personal.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Progression
import app.fitos.core.domain.training.SessionExercise
import app.fitos.core.domain.training.SessionFlow
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.Suggestion
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUi(
    val loaded: Boolean = false,
    val session: WorkoutSession? = null,
    val selected: Int = 0,
    /** Last finished performance per session exercise id (null = first time; absent = loading). */
    val last: Map<String, ExercisePerformance?> = emptyMap(),
    val suggestions: Map<String, Suggestion> = emptyMap(),
    /** When the current rest ends; null when not resting. */
    val restEndsAt: Instant? = null,
) {
    val current: SessionExercise? get() = session?.exercises?.getOrNull(selected)
}

/** The user changed the rest that followed the set logged at [base]: moved it, or skipped it (null). */
private data class RestOverride(val base: Instant, val endsAt: Instant?)

class SessionViewModel(
    private val workouts: WorkoutRepository,
    private val clock: Clock,
    private val alarm: RestAlarm,
) : ViewModel() {
    private val selected = MutableStateFlow<Int?>(null)
    /** Keyed by "sessionExerciseId:exerciseId", so a swapped exercise is looked up again. */
    private val performances = MutableStateFlow<Map<String, ExercisePerformance?>>(emptyMap())
    private val restOverride = MutableStateFlow<RestOverride?>(null)
    private val loading = mutableSetOf<String>()

    private val active = workouts.active.onEach { session -> session?.let(::loadPerformances) }

    val ui: StateFlow<SessionUi> = combine(active, selected, performances, restOverride) { session, sel, perf, override ->
        if (session == null) return@combine SessionUi(loaded = true)
        val index = (sel ?: SessionFlow.resumeAt(session) ?: 0).coerceIn(0, (session.exercises.size - 1).coerceAtLeast(0))
        // Only looked-up entries: a missing key means "still loading", a null value "first time".
        val last = session.exercises.filter { key(it) in perf }.associate { it.id to perf[key(it)] }
        val computed = SessionFlow.restEndsAt(session)
        val rest = if (override != null && override.base == computed) override.endsAt else computed
        SessionUi(
            loaded = true,
            session = session,
            selected = index,
            last = last,
            suggestions = session.exercises.associate { e ->
                e.id to Progression.suggest(e.exercise, e.targets, e.progression, last[e.id], session.day)
            },
            restEndsAt = rest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUi())

    private fun key(e: SessionExercise) = "${e.id}:${e.exercise.id}"

    private fun loadPerformances(session: WorkoutSession) {
        session.exercises.filter { key(it) !in performances.value && loading.add(key(it)) }.forEach { e ->
            viewModelScope.launch {
                val p = workouts.lastPerformance(e.exercise.id, session.startedAt)
                performances.update { it + (key(e) to p) }
            }
        }
    }

    fun select(index: Int) {
        selected.value = index
    }

    /** Saves the set at once, then moves on and starts the rest the way [SessionFlow] says. */
    fun logSet(item: SessionExercise, input: SetInput) {
        viewModelScope.launch {
            workouts.logSet(item.id, input)
            val session = workouts.active.first() ?: return@launch
            val index = session.exercises.indexOfFirst { it.id == item.id }
            val step = SessionFlow.after(session, index)
            step.next?.let { selected.value = it }
            restOverride.value = null
            val endsAt = SessionFlow.restEndsAt(session)
            if (endsAt != null && step.rest) {
                val next = step.next?.let { session.exercises[it].exercise.nameFa } ?: item.exercise.nameFa
                alarm.schedule(endsAt, next)
            } else {
                alarm.cancel()
            }
        }
    }

    fun adjustRest(seconds: Long) {
        val state = ui.value
        val session = state.session ?: return
        val base = SessionFlow.restEndsAt(session) ?: return
        val current = state.restEndsAt ?: return
        val moved = current.plusSeconds(seconds)
        if (!moved.isAfter(clock.instant())) return skipRest()
        restOverride.value = RestOverride(base, moved)
        val next = state.session.exercises.getOrNull(state.selected)?.exercise?.nameFa.orEmpty()
        alarm.schedule(moved, next)
    }

    fun skipRest() {
        val session = ui.value.session ?: return
        val base = SessionFlow.restEndsAt(session) ?: return
        restOverride.value = RestOverride(base, null)
        alarm.cancel()
    }

    fun updateSet(setId: String, input: SetInput) = viewModelScope.launch { workouts.updateSet(setId, input) }
    fun deleteSet(setId: String) = viewModelScope.launch { workouts.deleteSet(setId) }
    fun setSkipped(item: SessionExercise, skipped: Boolean) = viewModelScope.launch {
        workouts.setSkipped(item.id, skipped)
        if (skipped) workouts.active.first()?.let { s -> SessionFlow.firstOpen(s)?.let { selected.value = it } }
    }

    fun pause() = viewModelScope.launch {
        ui.value.session?.let { workouts.pause(it.id) }
        alarm.cancel()
    }

    fun resume() = viewModelScope.launch { ui.value.session?.let { workouts.resume(it.id) } }

    fun finish(effort: Int?, note: String?, onDone: (String) -> Unit) {
        val id = ui.value.session?.id ?: return
        alarm.cancel()
        viewModelScope.launch {
            workouts.finish(id, effort, note)
            onDone(id)
        }
    }

    fun discard(onDone: () -> Unit) {
        val id = ui.value.session?.id ?: return
        alarm.cancel()
        viewModelScope.launch {
            workouts.discard(id)
            onDone()
        }
    }

    companion object {
        /**
         * What the input starts with: the set just done (same exercise), otherwise today's
         * suggestion, otherwise last time and the plan.
         */
        fun prefill(item: SessionExercise, suggestion: Suggestion?, last: ExercisePerformance?): SetInput {
            val previous = item.working.lastOrNull() ?: item.sets.lastOrNull()
            if (previous != null) return SetInput(weightKg = previous.weightKg, reps = previous.reps, durationSec = previous.durationSec, distanceM = previous.distanceM)
            val lastSet = last?.working?.firstOrNull()
            return when (item.exercise.logType) {
                LogType.WEIGHT_REPS -> SetInput(
                    weightKg = suggestion?.weightKg ?: lastSet?.weightKg ?: item.targets.weightKg,
                    reps = suggestion?.reps ?: item.targets.repMin ?: 8,
                )
                LogType.REPS -> SetInput(weightKg = lastSet?.weightKg, reps = suggestion?.reps ?: item.targets.repMin ?: 10)
                LogType.TIME -> SetInput(durationSec = lastSet?.durationSec ?: item.targets.durationSec ?: 30)
                LogType.DISTANCE -> SetInput(distanceM = lastSet?.distanceM ?: 1000.0)
            }
        }
    }
}
