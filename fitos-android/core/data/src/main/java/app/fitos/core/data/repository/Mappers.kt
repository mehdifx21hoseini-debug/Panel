package app.fitos.core.data.repository

import app.fitos.core.data.db.entity.NutritionTargetEntity
import app.fitos.core.data.db.entity.ProfileEntity
import app.fitos.core.data.db.entity.WeightEntryEntity
import app.fitos.core.data.db.entity.WeightGoalEntity
import app.fitos.core.domain.body.WeightCondition
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.common.DataSource
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.TargetAssumptions
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.GoalStatus
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.Sex
import app.fitos.core.domain.profile.WeightGoal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal fun ProfileEntity.toDomain() = Profile(
    displayName = displayName,
    sex = Sex.fromCode(sex),
    birthDate = LocalDate.ofEpochDay(birthDate),
    heightCm = heightCm,
    activityLevel = ActivityLevel.fromCode(activityLevel),
    trainingDaysPerWeek = trainingDaysPerWeek,
    usualTrainingTime = usualTrainingMinute?.let { LocalTime.of(it / 60, it % 60) },
    homeWorkoutMinutes = homeWorkoutMinutes,
    homeEquipment = homeEquipment.split(',').mapNotNull { HomeEquipment.fromCode(it) }.toSet(),
    primaryGoal = PrimaryGoal.fromCode(primaryGoal),
    energyPace = EnergyPace.fromCode(energyPace),
)

internal fun Profile.toEntity(createdAt: Long, updatedAt: Long) = ProfileEntity(
    id = ProfileEntity.SINGLE_ID,
    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
    sex = sex.code,
    birthDate = birthDate.toEpochDay(),
    heightCm = heightCm,
    activityLevel = activityLevel.code,
    trainingDaysPerWeek = trainingDaysPerWeek,
    usualTrainingMinute = usualTrainingTime?.let { it.hour * 60 + it.minute },
    homeWorkoutMinutes = homeWorkoutMinutes,
    homeEquipment = HomeEquipment.entries.filter { it in homeEquipment }.joinToString(",") { it.code },
    primaryGoal = primaryGoal.code,
    energyPace = energyPace.code,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun WeightGoalEntity.toDomain() = WeightGoal(
    id = id,
    targetWeightKg = targetWeightKg,
    startWeightKg = startWeightKg,
    startDate = LocalDate.ofEpochDay(startDate),
    reviewDate = reviewDate?.let(LocalDate::ofEpochDay),
    status = GoalStatus.fromCode(status),
)

internal fun WeightEntryEntity.toDomain() = WeightEntry(
    id = id,
    measuredAt = Instant.ofEpochMilli(measuredAt),
    zone = ZoneId.of(zoneId),
    day = LocalDate.ofEpochDay(day),
    weightKg = weightKg,
    condition = WeightCondition.fromCode(condition),
    note = note,
    source = DataSource.fromCode(source),
)

internal fun NutritionTargetEntity.toDomain() = NutritionTarget(
    effectiveFrom = LocalDate.ofEpochDay(effectiveFrom),
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    waterMl = waterMl,
    sleepMinutes = sleepMinutes,
    origin = TargetOrigin.fromCode(origin),
    assumptions = assumptionsJson?.let(AssumptionsCodec::decode),
)

internal fun NutritionTarget.toEntity(id: String, createdAt: Long, updatedAt: Long) = NutritionTargetEntity(
    id = id,
    effectiveFrom = effectiveFrom.toEpochDay(),
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    waterMl = waterMl,
    sleepMinutes = sleepMinutes,
    origin = origin.code,
    assumptionsJson = assumptions?.let(AssumptionsCodec::encode),
    note = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Versioned JSON for [TargetAssumptions]; unreadable or unknown data decodes to null, never crashes. */
internal object AssumptionsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class V1(
        val v: Int,
        val sex: String,
        val ageYears: Int,
        val weightKg: Double,
        val heightCm: Double,
        val activity: String,
        val bmr: Double,
        val tdee: Double,
        val goal: String,
        val adjustmentPercent: Int,
        val referenceWeightKg: Double,
        val proteinPerKg: Double,
        val fatPerKg: Double,
        val raisedToFloor: Boolean,
        val fatRaisedToMinimum: Boolean,
    )

    fun encode(a: TargetAssumptions): String = json.encodeToString(
        V1.serializer(),
        V1(
            v = a.formulaVersion, sex = a.sex.code, ageYears = a.ageYears, weightKg = a.weightKg, heightCm = a.heightCm,
            activity = a.activityLevel.code, bmr = a.bmr, tdee = a.tdee, goal = a.goal.code,
            adjustmentPercent = a.adjustmentPercent, referenceWeightKg = a.referenceWeightKg,
            proteinPerKg = a.proteinPerKg, fatPerKg = a.fatPerKg,
            raisedToFloor = a.raisedToFloor, fatRaisedToMinimum = a.fatRaisedToMinimum,
        ),
    )

    fun decode(text: String): TargetAssumptions? = runCatching {
        val d = json.decodeFromString(V1.serializer(), text)
        TargetAssumptions(
            formulaVersion = d.v, sex = Sex.fromCode(d.sex), ageYears = d.ageYears, weightKg = d.weightKg,
            heightCm = d.heightCm, activityLevel = ActivityLevel.fromCode(d.activity), bmr = d.bmr, tdee = d.tdee,
            goal = PrimaryGoal.fromCode(d.goal), adjustmentPercent = d.adjustmentPercent,
            referenceWeightKg = d.referenceWeightKg, proteinPerKg = d.proteinPerKg, fatPerKg = d.fatPerKg,
            raisedToFloor = d.raisedToFloor, fatRaisedToMinimum = d.fatRaisedToMinimum,
        )
    }.getOrNull()
}
