package app.fitos.core.data.repository

import app.fitos.core.data.db.dao.DayWithVariants
import app.fitos.core.data.db.dao.ExerciseWithMuscles
import app.fitos.core.data.db.dao.PlannedWithDetails
import app.fitos.core.data.db.dao.ProgramTree
import app.fitos.core.data.db.entity.ExerciseMuscleEntity
import app.fitos.core.data.db.entity.ProgressionRuleEntity
import app.fitos.core.domain.training.DayVariant
import app.fitos.core.domain.training.Equipment
import app.fitos.core.domain.training.Exercise
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.PlannedExercise
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.ProgressionRule
import app.fitos.core.domain.training.ProgressionType
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.VariantKind
import java.time.DayOfWeek

internal const val ROLE_PRIMARY = "primary"
internal const val ROLE_SECONDARY = "secondary"

internal fun ExerciseWithMuscles.toDomain() = Exercise(
    id = exercise.id,
    builtinKey = exercise.builtinKey,
    nameFa = exercise.nameFa,
    nameEn = exercise.nameEn,
    equipment = Equipment.fromCode(exercise.equipment),
    logType = LogType.fromCode(exercise.logType),
    primary = muscles.filter { it.role == ROLE_PRIMARY }.mapNotNull { Muscle.fromCode(it.muscle) }.toSet(),
    secondary = muscles.filter { it.role == ROLE_SECONDARY }.mapNotNull { Muscle.fromCode(it.muscle) }.toSet(),
    weightIncrementKg = exercise.weightIncrementKg,
    bodyweightPercent = exercise.bodyweightPercent,
    instructions = exercise.instructions,
    isCustom = exercise.isCustom,
    isFavorite = exercise.isFavorite,
)

internal fun muscleRows(exerciseId: String, primary: Set<Muscle>, secondary: Set<Muscle>): List<ExerciseMuscleEntity> =
    primary.map { ExerciseMuscleEntity(exerciseId, it.code, ROLE_PRIMARY) } +
        (secondary - primary).map { ExerciseMuscleEntity(exerciseId, it.code, ROLE_SECONDARY) }

internal fun ProgressionRuleEntity.toDomain() = ProgressionRule(ProgressionType.fromCode(type), incrementKg, deloadPercent, requireGoodQuality)

internal fun PlannedWithDetails.toDomain() = PlannedExercise(
    id = planned.id,
    exercise = exercise.toDomain(),
    position = planned.position,
    sets = planned.sets,
    repMin = planned.repMin,
    repMax = planned.repMax,
    targetRir = planned.targetRir,
    durationSec = planned.durationSec,
    distanceM = planned.distanceM,
    startWeightKg = planned.startWeightKg,
    restSec = planned.restSec,
    supersetGroup = planned.supersetGroup,
    note = planned.note,
    progression = rule?.toDomain(),
)

internal fun DayWithVariants.toDomain() = ProgramDay(
    id = day.id,
    position = day.position,
    name = day.name,
    isRest = day.isRest,
    preferredWeekday = day.preferredWeekday?.let { DayOfWeek.of(it) },
    note = day.note,
    variants = variants
        .map { v -> DayVariant(v.variant.id, VariantKind.fromCode(v.variant.kind), v.variant.estimatedMinutes, v.planned.map { it.toDomain() }.sortedBy { it.position }) }
        .sortedBy { it.kind.ordinal },
)

internal fun ProgramTree.toDomain() = Program(
    id = program.id,
    name = program.name,
    description = program.description,
    isActive = program.isActive,
    scheduleMode = ScheduleMode.fromCode(program.scheduleMode),
    days = days.map { it.toDomain() }.sortedBy { it.position },
    archived = program.archivedAt != null,
)

internal val VariantKind.labelFa: String
    get() = when (this) {
        VariantKind.FULL -> "باشگاه"
        VariantKind.HOME -> "خانگی"
        VariantKind.MINIMAL -> "حداقلی"
    }
