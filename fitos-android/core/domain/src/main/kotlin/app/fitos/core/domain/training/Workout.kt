package app.fitos.core.domain.training

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

enum class SetType(val code: String) {
    WARMUP("warmup"),
    WORKING("working"),
    DROP("drop"),
    FAILURE("failure");

    /** Warm-ups never count towards volume, records or progression. */
    val counts: Boolean get() = this != WARMUP

    companion object {
        fun fromCode(code: String): SetType = entries.firstOrNull { it.code == code } ?: WORKING
    }
}

/** How clean the set was, as the user felt it. Optional on every set. */
enum class SetQuality(val code: String) {
    GOOD("good"),
    OK("ok"),
    POOR("poor");

    companion object {
        fun fromCode(code: String?): SetQuality? = entries.firstOrNull { it.code == code }
    }
}

enum class SessionStatus(val code: String) {
    IN_PROGRESS("in_progress"),
    FINISHED("finished"),
    ABANDONED("abandoned");

    companion object {
        fun fromCode(code: String): SessionStatus = entries.firstOrNull { it.code == code } ?: FINISHED
    }
}

/** What the user enters for one set; fields that do not apply to the exercise stay null. */
data class SetInput(
    val type: SetType = SetType.WORKING,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rir: Int? = null,
    val quality: SetQuality? = null,
    val pain: Boolean = false,
)

data class SetLog(
    val id: String,
    val position: Int,
    val type: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    val rir: Int?,
    val quality: SetQuality?,
    val pain: Boolean,
    val completedAt: Instant,
) {
    fun input() = SetInput(type, weightKg, reps, durationSec, distanceM, rir, quality, pain)
}

/** The plan copied into the session when it starts, so later program edits never rewrite history. */
data class SessionTargets(
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val rir: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val restSec: Int?,
)

data class SessionExercise(
    val id: String,
    val exercise: Exercise,
    val position: Int,
    val plannedExerciseId: String?,
    /** The exercise this one replaced during the session. */
    val replacedExerciseId: String?,
    val skipped: Boolean,
    val targets: SessionTargets,
    /** From the plan: exercises with the same group are done back to back. */
    val supersetGroup: Int?,
    val progression: ProgressionRule?,
    val note: String?,
    val sets: List<SetLog>,
) {
    val working: List<SetLog> get() = sets.filter { it.type.counts }
    val isDone: Boolean get() = skipped || working.size >= targets.sets
}

data class WorkoutSession(
    val id: String,
    val programId: String?,
    val programDayId: String?,
    /** Name of the program day, when it still exists. */
    val dayName: String?,
    val variantKind: VariantKind?,
    val day: LocalDate,
    val zone: ZoneId,
    val startedAt: Instant,
    val endedAt: Instant?,
    val pausedAt: Instant?,
    val pausedMillis: Long,
    val status: SessionStatus,
    /** 1–10, asked when finishing. */
    val perceivedEffort: Int?,
    val note: String?,
    val exercises: List<SessionExercise>,
) {
    val isPaused: Boolean get() = pausedAt != null
    val workingSets: Int get() = exercises.sumOf { it.working.size }

    /** Training time without pauses. */
    fun activeMillis(now: Instant): Long =
        ((endedAt ?: pausedAt ?: now).toEpochMilli() - startedAt.toEpochMilli() - pausedMillis).coerceAtLeast(0)
}

/** One finished session in the history list. */
data class SessionSummary(
    val id: String,
    val programDayId: String?,
    val dayName: String?,
    val variantKind: VariantKind?,
    val day: LocalDate,
    val startedAt: Instant,
    val activeMinutes: Int,
    val exerciseCount: Int,
    val workingSets: Int,
    val tonnageKg: Double,
)

/** An exercise as done in one earlier session (for "last time" and the exercise history). */
data class ExercisePerformance(
    val sessionId: String,
    val day: LocalDate,
    val sets: List<SetLog>,
    /** Body weight that day, for body-weight load; null when none was logged before it. */
    val bodyWeightKg: Double?,
) {
    val working: List<SetLog> get() = sets.filter { it.type.counts }
}

enum class RecordType(val code: String) {
    /** Heaviest weight lifted for an exact number of reps. */
    MAX_WEIGHT_FOR_REPS("max_weight_for_reps"),
    /** Best estimated one-rep max (Epley, 1–12 reps). */
    E1RM("e1rm"),
    /** Most reps in one body-weight set. */
    MAX_REPS_BODYWEIGHT("max_reps_bodyweight"),
    /** Most tonnage of the exercise in one session. */
    SESSION_TONNAGE("session_tonnage");

    companion object {
        fun fromCode(code: String): RecordType? = entries.firstOrNull { it.code == code }
    }
}

data class PersonalRecord(
    val type: RecordType,
    /** Rep count for [RecordType.MAX_WEIGHT_FOR_REPS]. */
    val reps: Int?,
    val value: Double,
    val sessionId: String,
    val setId: String?,
    val day: LocalDate,
)

/** The last finished session of a program, for picking the next day. */
data class CompletedDay(val dayId: String?, val date: LocalDate)

/** What the end-of-session screen shows. */
data class SessionReport(
    val session: WorkoutSession,
    val tonnageKg: Double,
    /** The previous finished session of the same program day, if any. */
    val previous: SessionSummary?,
    /** Records set in this session, by exercise id (exercises done for the first time have none). */
    val records: Map<String, List<PersonalRecord>>,
    /** Exercises done for the first time in this session. */
    val firstTime: Set<String>,
)

interface WorkoutRepository {
    /** The unfinished session, if any; survives the app being closed. */
    val active: Flow<WorkoutSession?>

    /** Finished sessions, newest first. */
    val history: Flow<List<SessionSummary>>

    fun session(id: String): Flow<WorkoutSession?>
    fun lastCompleted(programId: String): Flow<CompletedDay?>

    /** Earlier sessions of an exercise, newest first (finished only). */
    fun exerciseHistory(exerciseId: String): Flow<List<ExercisePerformance>>
    fun records(exerciseId: String): Flow<List<PersonalRecord>>

    /** The newest finished session started at or before [before] that has sets of the exercise. */
    suspend fun lastPerformance(exerciseId: String, before: Instant): ExercisePerformance?

    /**
     * Starts the chosen version of a program day. Fails while another session is unfinished.
     * [lighter] takes one set off exercises with three or more ([ReturnPlan.lighterSets]).
     */
    suspend fun start(programId: String, dayId: String, kind: VariantKind, lighter: Boolean = false): String
    suspend fun addExercise(sessionId: String, exerciseId: String): String
    /** Swaps an exercise that has no sets yet; the targets stay. */
    suspend fun replaceExercise(sessionExerciseId: String, exerciseId: String)
    suspend fun setSkipped(sessionExerciseId: String, skipped: Boolean)

    /** Saves the set at once, so closing the app never loses it. */
    suspend fun logSet(sessionExerciseId: String, input: SetInput): String
    suspend fun updateSet(setId: String, input: SetInput)
    suspend fun deleteSet(setId: String)

    suspend fun pause(sessionId: String)
    suspend fun resume(sessionId: String)
    suspend fun finish(sessionId: String, perceivedEffort: Int?, note: String?)
    /** Throws away an unfinished session (the user confirms first). */
    suspend fun discard(sessionId: String)
    /** Removes a finished session from history; records are rebuilt. */
    suspend fun delete(sessionId: String)

    suspend fun report(sessionId: String): SessionReport?
}
