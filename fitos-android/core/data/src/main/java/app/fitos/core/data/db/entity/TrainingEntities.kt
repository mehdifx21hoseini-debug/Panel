package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Built-in exercises carry a stable [builtinKey] so library updates can find them; custom ones have none. */
@Entity(tableName = "exercise", indices = [Index(value = ["builtinKey"], unique = true), Index("nameFa")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val builtinKey: String?,
    val nameFa: String,
    val nameEn: String?,
    val equipment: String,
    /** weight_reps, bodyweight_reps, time or distance. */
    val logType: String,
    val weightIncrementKg: Double?,
    /** Share of body weight counted as load for bodyweight moves, so they can add to tonnage. */
    val bodyweightPercent: Int?,
    val instructions: String?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Muscles worked by an exercise; a set counts 1 for a primary muscle and 0.5 for a secondary one. */
@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exerciseId", "muscle"],
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("muscle")],
)
data class ExerciseMuscleEntity(
    val exerciseId: String,
    val muscle: String,
    /** primary or secondary. */
    val role: String,
)

@Entity(tableName = "program")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    /** sequence (next day follows the last one done) or weekdays (fixed days). */
    val scheduleMode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
    val deletedAt: Long?,
)

@Entity(
    tableName = "program_day",
    foreignKeys = [ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programId")],
)
data class ProgramDayEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val position: Int,
    val name: String,
    val isRest: Boolean,
    /** ISO day of week 1..7 for the weekdays mode. */
    val preferredWeekday: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Full gym, short home and minimal versions of one program day. */
@Entity(
    tableName = "day_variant",
    foreignKeys = [ForeignKey(entity = ProgramDayEntity::class, parentColumns = ["id"], childColumns = ["programDayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["programDayId", "kind"], unique = true)],
)
data class DayVariantEntity(
    @PrimaryKey val id: String,
    val programDayId: String,
    /** full, home or minimal. */
    val kind: String,
    val name: String?,
    val estimatedMinutes: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "planned_exercise",
    foreignKeys = [
        ForeignKey(entity = DayVariantEntity::class, parentColumns = ["id"], childColumns = ["variantId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("variantId"), Index("exerciseId")],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val variantId: String,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val targetRir: Int?,
    val durationSec: Int?,
    val distanceM: Int?,
    val startWeightKg: Double?,
    val restSec: Int?,
    /** Exercises sharing a group number are done as a superset. */
    val supersetGroup: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "progression_rule",
    foreignKeys = [ForeignKey(entity = PlannedExerciseEntity::class, parentColumns = ["id"], childColumns = ["plannedExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["plannedExerciseId"], unique = true)],
)
data class ProgressionRuleEntity(
    @PrimaryKey val id: String,
    val plannedExerciseId: String,
    /** double_progression or none. */
    val type: String,
    val incrementKg: Double?,
    val deloadPercent: Int?,
    val requireGoodQuality: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "program_change_log",
    foreignKeys = [ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programId")],
)
data class ProgramChangeLogEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val at: Long,
    val changeType: String,
    val summary: String,
    val detailJson: String?,
)

@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ProgramDayEntity::class, parentColumns = ["id"], childColumns = ["programDayId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("day"), Index("status"), Index("programId"), Index("programDayId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val programId: String?,
    val programDayId: String?,
    val variantKind: String?,
    val day: Long,
    val startedAt: Long,
    val endedAt: Long?,
    /** Set while paused; total paused time is kept in [pausedMillis]. */
    val pausedAt: Long?,
    val pausedMillis: Long,
    /** in_progress, finished or abandoned. */
    val status: String,
    val zoneId: String,
    val perceivedEffort: Int?,
    val note: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** A copy of the plan at the moment of training, so later program edits never rewrite history. */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val plannedExerciseId: String?,
    /** The planned exercise this one replaced, when swapped during the session. */
    val replacedExerciseId: String?,
    val skipped: Boolean,
    val targetSets: Int?,
    val targetRepMin: Int?,
    val targetRepMax: Int?,
    val targetRir: Int?,
    val targetWeightKg: Double?,
    val targetDurationSec: Int?,
    val restSec: Int?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [ForeignKey(entity = SessionExerciseEntity::class, parentColumns = ["id"], childColumns = ["sessionExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionExerciseId")],
)
data class SetLogEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val position: Int,
    /** warmup, working, drop or failure. Warm-ups never count towards volume. */
    val setType: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    val rir: Int?,
    /** good, ok or poor. */
    val quality: String?,
    val pain: Boolean,
    val completedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Rebuildable cache of personal records; recomputed whenever a set is edited or deleted. */
@Entity(
    tableName = "personal_record",
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["exerciseId", "type", "reps"])],
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    /** max_weight_for_reps, e1rm, max_reps_bodyweight or session_tonnage. */
    val type: String,
    val reps: Int?,
    val value: Double,
    val setLogId: String?,
    val sessionId: String?,
    val achievedAt: Long,
    val day: Long,
)
